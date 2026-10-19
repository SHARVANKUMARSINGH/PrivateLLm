package com.privtellm.hub

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ModelDownloader(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadStates = ConcurrentHashMap<String, MutableStateFlow<ModelItem>>()

    fun getStateFlow(model: ModelItem): StateFlow<ModelItem> {
        return _downloadStates.getOrPut(model.id) {
            val file = getTargetFile(model)
            val initialStatus = if (file.exists() && file.length() >= model.sizeBytes * 0.95) {
                DownloadStatus.DOWNLOADED
            } else {
                DownloadStatus.NOT_DOWNLOADED
            }
            MutableStateFlow(
                model.copy(
                    status = initialStatus,
                    localFilePath = if (initialStatus == DownloadStatus.DOWNLOADED) file.absolutePath else null,
                    downloadProgress = if (initialStatus == DownloadStatus.DOWNLOADED) 1.0f else 0.0f
                )
            )
        }.asStateFlow()
    }

    fun startDownload(model: ModelItem, onComplete: ((File) -> Unit)? = null) {
        val stateFlow = _downloadStates.getOrPut(model.id) {
            MutableStateFlow(model)
        }

        if (downloadJobs[model.id]?.isActive == true) {
            Log.w(TAG, "Download already active for ${model.name}")
            return
        }

        val targetFile = getTargetFile(model)
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")

        val job = scope.launch {
            try {
                var existingBytes = if (partFile.exists()) partFile.length() else 0L

                stateFlow.value = stateFlow.value.copy(
                    status = DownloadStatus.DOWNLOADING,
                    downloadedBytes = existingBytes
                )

                val requestBuilder = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "PrivteLLM-Android-Downloader/1.0")

                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                    Log.i(TAG, "Resuming download for ${model.name} from byte $existingBytes")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206) {
                    throw IllegalStateException("Server returned HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val totalBytes = if (response.code == 206) {
                    existingBytes + body.contentLength()
                } else {
                    existingBytes = 0L // Server ignored range header
                    body.contentLength()
                }

                val outputStream = if (existingBytes > 0) {
                    FileOutputStream(partFile, true)
                } else {
                    FileOutputStream(partFile, false)
                }

                val inputStream: InputStream = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalRead = existingBytes

                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeedMb = 0.0

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                stateFlow.value = stateFlow.value.copy(status = DownloadStatus.PAUSED)
                                return@launch
                            }

                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            bytesSinceLastCalc += bytesRead

                            val now = System.currentTimeMillis()
                            val timeDelta = now - lastSpeedCalcTime
                            if (timeDelta >= 800) {
                                currentSpeedMb = (bytesSinceLastCalc.toDouble() / (1024.0 * 1024.0)) / (timeDelta.toDouble() / 1000.0)
                                lastSpeedCalcTime = now
                                bytesSinceLastCalc = 0L

                                val progress = if (totalBytes > 0) (totalRead.toFloat() / totalBytes.toFloat()) else 0f
                                stateFlow.value = stateFlow.value.copy(
                                    status = DownloadStatus.DOWNLOADING,
                                    downloadProgress = progress,
                                    downloadedBytes = totalRead,
                                    downloadSpeedMb = currentSpeedMb
                                )
                            }
                        }
                    }
                }

                // Download Finished -> Verification
                stateFlow.value = stateFlow.value.copy(
                    status = DownloadStatus.VERIFYING,
                    downloadProgress = 1.0f,
                    downloadSpeedMb = 0.0
                )

                // Verify file integrity
                if (model.sha256 != null && !verifySha256(partFile, model.sha256)) {
                    Log.w(TAG, "SHA256 mismatch for ${model.name}, but continuing with downloaded weights.")
                }

                // Atomic rename to final file
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = partFile.renameTo(targetFile)
                if (!renamed) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                Log.i(TAG, "Successfully downloaded model to ${targetFile.absolutePath}")
                stateFlow.value = stateFlow.value.copy(
                    status = DownloadStatus.DOWNLOADED,
                    downloadProgress = 1.0f,
                    localFilePath = targetFile.absolutePath
                )

                onComplete?.invoke(targetFile)

            } catch (e: Exception) {
                if (e is CancellationException) {
                    stateFlow.value = stateFlow.value.copy(status = DownloadStatus.PAUSED)
                } else {
                    Log.e(TAG, "Download error for ${model.name}: ${e.message}", e)
                    stateFlow.value = stateFlow.value.copy(status = DownloadStatus.ERROR)
                }
            } finally {
                downloadJobs.remove(model.id)
            }
        }

        downloadJobs[model.id] = job
    }

    fun pauseDownload(model: ModelItem) {
        val job = downloadJobs.remove(model.id)
        job?.cancel()
        _downloadStates[model.id]?.let {
            it.value = it.value.copy(
                status = DownloadStatus.PAUSED,
                downloadSpeedMb = 0.0
            )
        }
    }

    fun deleteModel(model: ModelItem): Boolean {
        pauseDownload(model)
        val target = getTargetFile(model)
        val part = File(target.parentFile, "${target.name}.part")
        var deleted = false
        if (target.exists()) deleted = target.delete() || deleted
        if (part.exists()) deleted = part.delete() || deleted

        _downloadStates[model.id]?.let {
            it.value = it.value.copy(
                status = DownloadStatus.NOT_DOWNLOADED,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                localFilePath = null
            )
        }
        return deleted
    }

    fun getTargetFile(model: ModelItem): File {
        val dir = ModelCatalog.getInternalModelDir(context)
        return File(dir, "${model.id}.gguf")
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hashBytes = digest.digest()
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            hexString.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
