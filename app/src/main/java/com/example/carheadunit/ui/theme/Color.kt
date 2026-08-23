package com.example.carheadunit.ui.theme

import androidx.compose.ui.graphics.Color

// Core surfaces (design: solid charcoal, no gradient)
val SurfaceBg = Color(0xFF111316)
val SurfaceContainer = Color(0xFF1E2023)     // dock
val SurfaceHighest = Color(0xFF333538)       // gauge tracks
val SurfaceLowest = Color(0xFF0C0E11)        // steering track base

// Glass panels (tonal layering, no shadows)
val GlassFill = Color(0xCC1C1F26)            // rgba(28,31,38,0.8)
val GlassBorder = Color(0x33849396)          // rgba(132,147,150,0.2)
val TopBorderWhite10 = Color(0x1AFFFFFF)     // dock top border

// Text
val OnSurface = Color(0xFFE2E2E6)
val OnSurfaceVariant = Color(0xFFBAC9CC)
val White20 = Color(0x33FFFFFF)              // inactive icons, tick marks

// All-apps page (opaque white: reads as its own page, not a popup)
val AllAppsBackground = Color.White
val OnAllApps = Color(0xFF17191C)            // labels/titles on the white page
val OnAllAppsVariant = Color(0xFFC6CBD0)     // inactive page dots

// Cyan family (primary)
val PrimaryContainer = Color(0xFF00E5FF)     // glows, fills
val Primary = Color(0xFFC3F5FF)
val PrimaryFixed = Color(0xFF9CF0FF)
val PrimaryFixedDim = Color(0xFF00DAF3)
val CyanGlowAmbient = Color(0x0D00DAF3)      // rgba(0,218,243,0.05)

// Green family (secondary)
val SecondaryFixed = Color(0xFF79FF5B)
val SecondaryContainer = Color(0xFF2FF801)

// Status
val ErrorRed = Color(0xFFFFB4AB)
val OutlineVariant = Color(0xFF3B494C)       // dividers
