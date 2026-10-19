package com.privtellm.terminal

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object TermuxBootstrapManager {
    private const val TAG = "TermuxBootstrapManager"

    // Termux aarch64 Official Bootstrap Packages
    private const val PRIMARY_BOOTSTRAP_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.01.12-r1%2Bapt-android-7/bootstrap-aarch64.zip"
    private const val MIRROR_BOOTSTRAP_URL =
        "https://packages.termux.dev/apt/termux-main/bootstrap-aarch64.zip"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getTermuxRoot(context: Context): File = File(context.filesDir, "termux")
    fun getPrefixDir(context: Context): File = File(getTermuxRoot(context), "usr")
    fun getBinDir(context: Context): File = File(getPrefixDir(context), "bin")
    fun getLibDir(context: Context): File = File(getPrefixDir(context), "lib")
    fun getHomeDir(context: Context): File = File(getTermuxRoot(context), "home")

    fun isInstalled(context: Context): Boolean {
        val binDir = getBinDir(context)
        val bash = File(binDir, "bash")
        val sh = File(binDir, "sh")
        val busybox = File(binDir, "busybox")
        return (bash.exists() && bash.canExecute()) ||
               (sh.exists() && sh.canExecute()) ||
               (busybox.exists() && busybox.canExecute())
    }

    /**
     * Downloads and extracts the official Termux minimal aarch64 rootfs bootstrap
     */
    suspend fun installBootstrap(
        context: Context,
        onProgress: (Float, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = getTermuxRoot(context)
            val prefix = getPrefixDir(context)
            val binDir = getBinDir(context)
            val libDir = getLibDir(context)
            val homeDir = getHomeDir(context)

            root.mkdirs()
            prefix.mkdirs()
            binDir.mkdirs()
            libDir.mkdirs()
            homeDir.mkdirs()

            onProgress(0.05f, "Connecting to Termux bootstrap repository...")

            // Try primary then mirror
            val urls = listOf(PRIMARY_BOOTSTRAP_URL, MIRROR_BOOTSTRAP_URL)
            var downloadSuccess = false
            val zipFile = File(context.cacheDir, "termux_bootstrap_aarch64.zip")

            for (url in urls) {
                try {
                    onProgress(0.1f, "Downloading Termux bootstrap from $url...")
                    val request = Request.Builder().url(url).build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        val body = response.body!!
                        val contentLength = body.contentLength()
                        var totalRead = 0L
                        val buffer = ByteArray(64 * 1024)

                        body.byteStream().use { input ->
                            FileOutputStream(zipFile).use { output ->
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    totalRead += read
                                    if (contentLength > 0) {
                                        val p = 0.1f + 0.6f * (totalRead.toFloat() / contentLength)
                                        val mb = totalRead / (1024 * 1024)
                                        onProgress(p, "Downloaded $mb MB / ${(contentLength / (1024 * 1024))} MB")
                                    }
                                }
                                output.flush()
                            }
                        }
                        downloadSuccess = true
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed downloading from $url: ${e.message}")
                }
            }

            if (!downloadSuccess || !zipFile.exists() || zipFile.length() < 1024 * 1024) {
                return@withContext Result.failure(Exception("Failed to download Termux bootstrap. Check network connection."))
            }

            onProgress(0.75f, "Extracting Termux rootfs into sandbox...")

            // Unzip bootstrap archive
            val symlinks = mutableListOf<Pair<String, String>>()
            java.io.FileInputStream(zipFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (entryName == "SYMLINKS.txt") {
                            val reader = BufferedReader(InputStreamReader(zis))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val parts = line!!.split("←")
                                if (parts.size == 2) {
                                    symlinks.add(Pair(parts[0].trim(), parts[1].trim()))
                                }
                            }
                        } else {
                            val target = File(prefix, entryName)
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { fos ->
                                    val b = ByteArray(32 * 1024)
                                    var r: Int
                                    while (zis.read(b).also { r = it } != -1) {
                                        fos.write(b, 0, r)
                                    }
                                }
                                if (entryName.startsWith("bin/") || entryName.contains("/bin/")) {
                                    target.setExecutable(true, false)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            onProgress(0.9f, "Configuring POSIX permissions & symbolic links...")

            // Create symlinks if on Android API >= 26
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for ((link, target) in symlinks) {
                    try {
                        val linkFile = File(prefix, link)
                        linkFile.parentFile?.mkdirs()
                        if (linkFile.exists()) linkFile.delete()
                        Files.createSymbolicLink(linkFile.toPath(), Paths.get(target))
                        linkFile.setExecutable(true, false)
                    } catch (e: Exception) {
                        Log.d(TAG, "Symlink error for $link -> $target: ${e.message}")
                    }
                }
            }

            // Ensure all binaries in usr/bin have execute permissions
            binDir.listFiles()?.forEach { binary ->
                binary.setExecutable(true, false)
                binary.setReadable(true, false)
            }

            zipFile.delete()
            onProgress(1.0f, "Termux bootstrap installed successfully!")
            Result.success("Termux aarch64 bootstrap successfully initialized. curl, wget, bash, and busybox ready.")
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap installation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Built-in pure-Kotlin curl implementation that works ALWAYS without any external binary
     */
    fun executeBuiltinCurl(command: String, workingDir: File): Pair<String, Int> {
        return try {
            val tokens = splitCommandLine(command.removePrefix("curl").trim())
            var url: String? = null
            var method = "GET"
            val headers = mutableMapOf<String, String>()
            var data: String? = null
            var outputFile: String? = null
            var silent = false
            var headOnly = false

            var i = 0
            while (i < tokens.size) {
                when (val token = tokens[i]) {
                    "-s", "--silent" -> silent = true
                    "-I", "--head" -> { headOnly = true; method = "HEAD" }
                    "-X", "--request" -> {
                        if (i + 1 < tokens.size) {
                            method = tokens[++i].uppercase()
                        }
                    }
                    "-H", "--header" -> {
                        if (i + 1 < tokens.size) {
                            val headerLine = tokens[++i]
                            val colonIndex = headerLine.indexOf(':')
                            if (colonIndex != -1) {
                                val name = headerLine.substring(0, colonIndex).trim()
                                val value = headerLine.substring(colonIndex + 1).trim()
                                headers[name] = value
                            }
                        }
                    }
                    "-d", "--data", "--data-raw" -> {
                        if (i + 1 < tokens.size) {
                            data = tokens[++i]
                            if (method == "GET") method = "POST"
                        }
                    }
                    "-o", "--output" -> {
                        if (i + 1 < tokens.size) {
                            outputFile = tokens[++i]
                        }
                    }
                    else -> {
                        if (!token.startsWith("-") && url == null) {
                            url = token.trim('\'', '"')
                        }
                    }
                }
                i++
            }

            if (url == null) {
                return Pair("curl: no URL specified!\nUsage: curl [options...] <url>\n", 2)
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }

            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!headers.containsKey("User-Agent")) {
                reqBuilder.header("User-Agent", "curl/8.5.0 (PrivteLLM-Android)")
            }

            val body = if (method in listOf("POST", "PUT", "PATCH")) {
                val mediaType = headers["Content-Type"]?.toMediaTypeOrNull() ?: "application/json".toMediaTypeOrNull()
                (data ?: "").toRequestBody(mediaType)
            } else null

            reqBuilder.method(method, body)

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val code = response.code
            val responseText = response.body?.string() ?: ""

            if (outputFile != null) {
                val outFile = if (outputFile.startsWith("/")) File(outputFile) else File(workingDir, outputFile)
                outFile.writeText(responseText)
                Pair(if (!silent) "Downloaded ${responseText.length} bytes to ${outFile.name} (HTTP $code)\n" else "", 0)
            } else if (headOnly) {
                val sb = StringBuilder()
                sb.append("HTTP/1.1 $code ${response.message}\n")
                response.headers.forEach { pair ->
                    sb.append("${pair.first}: ${pair.second}\n")
                }
                Pair(sb.toString(), 0)
            } else {
                Pair(responseText + (if (!responseText.endsWith("\n")) "\n" else ""), 0)
            }
        } catch (e: Exception) {
            Pair("curl: (7) Failed to connect: ${e.message}\n", 7)
        }
    }

    /**
     * Built-in pure-Kotlin wget implementation
     */
    fun executeBuiltinWget(command: String, workingDir: File): Pair<String, Int> {
        return try {
            val tokens = splitCommandLine(command.removePrefix("wget").trim())
            var url: String? = null
            var outputFile: String? = null

            var i = 0
            while (i < tokens.size) {
                when (tokens[i]) {
                    "-O", "--output-document" -> {
                        if (i + 1 < tokens.size) outputFile = tokens[++i]
                    }
                    else -> {
                        if (!tokens[i].startsWith("-") && url == null) {
                            url = tokens[i].trim('\'', '"')
                        }
                    }
                }
                i++
            }

            if (url == null) {
                return Pair("wget: missing URL\nUsage: wget [options] <url>\n", 1)
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }

            val fileName = outputFile ?: url.substringAfterLast('/').substringBefore('?').ifEmpty { "index.html" }
            val targetFile = if (fileName.startsWith("/")) File(fileName) else File(workingDir, fileName)

            val request = Request.Builder().url(url).header("User-Agent", "Wget/1.21.4 (PrivteLLM)").build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                return Pair("wget: server returned error: HTTP ${response.code} ${response.message}\n", 8)
            }

            val body = response.body!!
            val contentLength = body.contentLength()
            var totalRead = 0L
            val buffer = ByteArray(32 * 1024)

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    var r: Int
                    while (input.read(buffer).also { r = it } != -1) {
                        output.write(buffer, 0, r)
                        totalRead += r
                    }
                    output.flush()
                }
            }

            Pair("Saving to: '$fileName'\nHTTP request sent, awaiting response... 200 OK\nLength: $totalRead bytes\n'$fileName' saved [$totalRead/$totalRead]\n", 0)
        } catch (e: Exception) {
            Pair("wget: unable to resolve host address: ${e.message}\n", 4)
        }
    }

    private fun splitCommandLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                c.isWhitespace() && !inSingle && !inDouble -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        return tokens
    }
}
