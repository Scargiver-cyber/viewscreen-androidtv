package org.jellyfin.androidtv.ui.base

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jellyfin.design.Tokens

fun colorScheme(): ColorScheme = ColorScheme(
	background = Tokens.Color.colorGrey975,
	onBackground = Tokens.Color.colorBluegrey25,
	button = Color(0xB3747474),
	onButton = Color(0xFFDDDDDD),
	buttonFocused = Color(0xE6CCCCCC),
	onButtonFocused = Color(0xFF444444),
	buttonDisabled = Color(0x33747474),
	onButtonDisabled = Color(0xFF686868),
	buttonActive = Color(0x4DCCCCCC),
	onButtonActive = Color(0xFFDDDDDD),
	input = Color(0xB3747474),
	onInput = Color(0xE6CCCCCC),
	inputFocused = Color(0xE6CCCCCC),
	onInputFocused = Color(0xFFDDDDDD),
	rangeControlBackground = Tokens.Color.colorBluegrey700,
	rangeControlFill = Tokens.Color.colorCyan500,
	rangeControlKnob = Tokens.Color.colorBluegrey100,
	seekbarBuffer = Tokens.Color.colorBluegrey300,
	recording = Tokens.Color.colorRed300,
	onRecording = Tokens.Color.colorRed25,
	badge = Tokens.Color.colorCyan500,
	onBadge = Tokens.Color.colorBluegrey100,
	listHeader = Tokens.Color.colorGrey50,
	listOverline = Tokens.Color.colorGrey500,
	listHeadline = Tokens.Color.colorGrey25,
	listCaption = Tokens.Color.colorGrey200,
	listButton = Color.Transparent,
	listButtonFocused = Tokens.Color.colorBluegrey800,
	surface = Tokens.Color.colorBluegrey900,
	scrim = Tokens.Color.colorBlack.copy(alpha = 0.67f),
	sidebarBackground = Color(0xFF111111),
)

// Tree frog green theme for Momma's profile
private val TreeFrogGreen = Color(0xFF23C762)
private val TreeFrogGreenDark = Color(0xFF0BA245)
private val TreeFrogGreenLight = Color(0xFF56E78B)

fun greenColorScheme(): ColorScheme = colorScheme().copy(
	buttonFocused = TreeFrogGreen,
	onButtonFocused = Color(0xFF111111),
	buttonActive = TreeFrogGreenDark.copy(alpha = 0.5f),
	onButtonActive = TreeFrogGreenLight,
	rangeControlFill = TreeFrogGreen,
	badge = TreeFrogGreen,
	onBadge = Color(0xFF111111),
	listButtonFocused = Color(0xFF003214),
	sidebarBackground = Color(0xFF001A0B),
)

// Red theme for Mad Maddie's profile
private val MaddieRed = Color(0xFFF85A5A)
private val MaddieRedDark = Color(0xFFB9090F)
private val MaddieRedLight = Color(0xFFFFBAB8)

fun redColorScheme(): ColorScheme = colorScheme().copy(
	buttonFocused = MaddieRed,
	onButtonFocused = Color(0xFF170002),
	buttonActive = MaddieRedDark.copy(alpha = 0.5f),
	onButtonActive = MaddieRedLight,
	rangeControlFill = MaddieRed,
	badge = MaddieRed,
	onBadge = Color(0xFF170002),
	listButtonFocused = Color(0xFF570009),
	sidebarBackground = Color(0xFF1A0002),
)

// LCARS theme for Daddy's profile — matches lcars-display project palette
private val LcarsOrange = Color(0xFFFF9966)  // --lcars-orange: primary accent
private val LcarsGold = Color(0xFFFFCC99)    // --lcars-gold: headers, bars
private val LcarsCyan = Color(0xFF99CCFF)    // --lcars-cyan: focused/active
private val LcarsSurface = Color(0xFF1A1A2E) // --lcars-surface
private val LcarsWarm = Color(0xFFCC6699)    // --lcars-warm: secondary

fun lcarsColorScheme(): ColorScheme = colorScheme().copy(
	buttonFocused = LcarsCyan,
	onButtonFocused = Color(0xFF0A0A1E),
	buttonActive = LcarsOrange.copy(alpha = 0.5f),
	onButtonActive = LcarsGold,
	rangeControlFill = LcarsOrange,
	badge = LcarsOrange,
	onBadge = Color.Black,
	listButtonFocused = LcarsSurface,
	surface = LcarsSurface,
	sidebarBackground = LcarsSurface,
)

// LCARS-style rounded shapes (the distinctive pill/rounded-rect look)
fun lcarsShapes(): Shapes = Shapes(
	extraSmall = RoundedCornerShape(8.dp),
	small = RoundedCornerShape(12.dp),
	medium = RoundedCornerShape(16.dp),
	large = RoundedCornerShape(20.dp),
	extraLarge = RoundedCornerShape(24.dp),
)

// Dark purple emo theme for Lil Sweet's profile
private val EmoPurple = Color(0xFFAE67FA)       // vivid purple
private val EmoPurpleDark = Color(0xFF5407A6)   // deep purple
private val EmoPurpleLight = Color(0xFFDDBDFF)  // soft lavender
private val EmoPurpleSurface = Color(0xFF19013F) // near-black purple

fun emoPurpleColorScheme(): ColorScheme = colorScheme().copy(
	buttonFocused = EmoPurple,
	onButtonFocused = Color(0xFF0E0024),
	buttonActive = EmoPurpleDark.copy(alpha = 0.6f),
	onButtonActive = EmoPurpleLight,
	rangeControlFill = EmoPurple,
	badge = EmoPurple,
	onBadge = Color(0xFF0E0024),
	listButtonFocused = EmoPurpleSurface,
	surface = EmoPurpleSurface,
	sidebarBackground = Color(0xFF0E0024),
)

fun colorSchemeForUser(userName: String?): ColorScheme = when {
	userName.equals("Daddy", ignoreCase = true) -> lcarsColorScheme()
	userName.equals("Momma", ignoreCase = true) -> greenColorScheme()
	userName.equals("Mad Maddie", ignoreCase = true) -> redColorScheme()
	userName.equals("Lil Sweet", ignoreCase = true) -> emoPurpleColorScheme()
	else -> colorScheme()
}

fun shapesForUser(userName: String?): Shapes = when {
	userName.equals("Daddy", ignoreCase = true) -> lcarsShapes()
	else -> Shapes()
}

@Immutable
data class ColorScheme(
	val background: Color,
	val onBackground: Color,

	val button: Color,
	val onButton: Color,
	val buttonFocused: Color,
	val onButtonFocused: Color,
	val buttonDisabled: Color,
	val onButtonDisabled: Color,
	val buttonActive: Color,
	val onButtonActive: Color,

	val input: Color,
	val onInput: Color,
	val inputFocused: Color,
	val onInputFocused: Color,

	val rangeControlBackground: Color,
	val rangeControlFill: Color,
	val rangeControlKnob: Color,
	val seekbarBuffer: Color,

	val recording: Color,
	val onRecording: Color,

	val badge: Color,
	val onBadge: Color,

	val listHeader: Color,
	val listOverline: Color,
	val listHeadline: Color,
	val listCaption: Color,
	val listButton: Color,
	val listButtonFocused: Color,

	val surface: Color,
	val scrim: Color,
	val sidebarBackground: Color,
)

val LocalColorScheme = staticCompositionLocalOf { colorScheme() }
