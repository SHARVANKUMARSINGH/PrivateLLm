package com.privtellm.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Google Material You / Google Apps Color Palette
// Inspired by Google Gemini, Google Drive, Gmail, & Pixel System UI
// ============================================================================

// Google Core Brand Colors
val GoogleBlue = Color(0xFF1A73E8)
val GoogleBlueDark = Color(0xFFA8C7FA)
val GoogleBlueContainerLight = Color(0xFFD3E3FD)
val GoogleBlueContainerDark = Color(0xFF004A77)
val GoogleOnBlueContainerLight = Color(0xFF041E49)
val GoogleOnBlueContainerDark = Color(0xFFD3E3FD)

val GoogleRed = Color(0xFFD93025)
val GoogleRedDark = Color(0xFFF28B82)
val GoogleRedContainerLight = Color(0xFFFCE8E6)
val GoogleRedContainerDark = Color(0xFF601410)

val GoogleGreen = Color(0xFF1E8E3E)
val GoogleGreenDark = Color(0xFF81C995)
val GoogleGreenContainerLight = Color(0xFFE6F4EA)
val GoogleGreenContainerDark = Color(0xFF0D5323)

val GoogleAmber = Color(0xFFF9AB00)
val GoogleAmberDark = Color(0xFFFDD663)
val GoogleAmberContainerLight = Color(0xFFFEF7E0)
val GoogleAmberContainerDark = Color(0xFF594300)

// Backward compatibility aliases for existing UI references
val AccentBlue = GoogleBlue
val AccentBlueLight = Color(0xFF4285F4)
val AccentBlueDark = Color(0xFF0D47A1)
val AccentBlueGlow = Color(0x221A73E8)
val AccentCyan = Color(0xFF007BFF)
val AccentCyanGlow = Color(0x18007BFF)

// Status Colors (Google Palette)
val StatusSuccess = GoogleGreen
val StatusSuccessGlow = GoogleGreenContainerLight
val StatusWarning = GoogleAmber
val StatusWarningGlow = GoogleAmberContainerLight
val StatusCritical = GoogleRed
val StatusCriticalGlow = GoogleRedContainerLight
val StatusNormal = GoogleBlue
val StatusNormalGlow = GoogleBlueContainerLight

// System Dark Palette (Gemini / Pixel Clean Dark - Not harsh black)
val DarkBackground = Color(0xFF131314)
val DarkSurface = Color(0xFF1E1F20)
val DarkSurfaceVariant = Color(0xFF282A2C)
val DarkSurfaceContainerHigh = Color(0xFF333537)
val DarkBorder = Color(0xFF444746)
val DarkBorderSubtle = Color(0xFF2E3134)
val DarkTextPrimary = Color(0xFFE3E3E3)
val DarkTextSecondary = Color(0xFFC4C7C5)
val DarkTextMuted = Color(0xFF8E918F)

// System Light Palette (Google Off-White & Crisp Clean Cards)
val LightBackground = Color(0xFFF8FAFD)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F4F9)
val LightSurfaceContainerHigh = Color(0xFFE9EEF6)
val LightBorder = Color(0xFFC4C7C5)
val LightBorderSubtle = Color(0xFFE1E3E1)
val LightTextPrimary = Color(0xFF1F1F1F)
val LightTextSecondary = Color(0xFF444746)
val LightTextMuted = Color(0xFF747775)

// Preserved for backwards compatibility
val PureBlack = DarkBackground
val SystemLightBg = LightBackground
