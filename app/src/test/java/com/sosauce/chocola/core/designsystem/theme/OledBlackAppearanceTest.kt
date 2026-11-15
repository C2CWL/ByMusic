package com.sosauce.chocola.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.sosauce.chocola.core.designsystem.CuteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OledBlackAppearanceTest {

    private data class Case(
        val theme: String,
        val systemDark: Boolean,
        val oled: Boolean,
        val dynamicColor: Boolean,
        val expectDark: Boolean,
        val expectOled: Boolean
    )

    private val cases = listOf(
        Case(CuteTheme.SYSTEM, systemDark = false, oled = false, dynamicColor = false, expectDark = false, expectOled = false),
        Case(CuteTheme.SYSTEM, systemDark = false, oled = false, dynamicColor = true, expectDark = false, expectOled = false),
        Case(CuteTheme.SYSTEM, systemDark = false, oled = true, dynamicColor = false, expectDark = false, expectOled = false),
        Case(CuteTheme.SYSTEM, systemDark = false, oled = true, dynamicColor = true, expectDark = false, expectOled = false),
        Case(CuteTheme.SYSTEM, systemDark = true, oled = false, dynamicColor = false, expectDark = true, expectOled = false),
        Case(CuteTheme.SYSTEM, systemDark = true, oled = false, dynamicColor = true, expectDark = true, expectOled = false),
        Case(CuteTheme.SYSTEM, systemDark = true, oled = true, dynamicColor = false, expectDark = true, expectOled = true),
        Case(CuteTheme.SYSTEM, systemDark = true, oled = true, dynamicColor = true, expectDark = true, expectOled = true),
        Case(CuteTheme.LIGHT, systemDark = false, oled = false, dynamicColor = false, expectDark = false, expectOled = false),
        Case(CuteTheme.LIGHT, systemDark = false, oled = true, dynamicColor = false, expectDark = false, expectOled = false),
        Case(CuteTheme.LIGHT, systemDark = true, oled = true, dynamicColor = true, expectDark = false, expectOled = false),
        Case(CuteTheme.LIGHT, systemDark = true, oled = false, dynamicColor = true, expectDark = false, expectOled = false),
        Case(CuteTheme.DARK, systemDark = false, oled = false, dynamicColor = false, expectDark = true, expectOled = false),
        Case(CuteTheme.DARK, systemDark = false, oled = true, dynamicColor = false, expectDark = true, expectOled = true),
        Case(CuteTheme.DARK, systemDark = true, oled = false, dynamicColor = true, expectDark = true, expectOled = false),
        Case(CuteTheme.DARK, systemDark = true, oled = true, dynamicColor = true, expectDark = true, expectOled = true),
        Case(CuteTheme.DARK, systemDark = false, oled = true, dynamicColor = true, expectDark = true, expectOled = true),
        // Legacy exclusive AMOLED is dark + OLED, even if the new flag was never stored.
        Case(CuteTheme.AMOLED, systemDark = false, oled = false, dynamicColor = false, expectDark = true, expectOled = true),
        Case(CuteTheme.AMOLED, systemDark = true, oled = false, dynamicColor = true, expectDark = true, expectOled = true),
        Case(CuteTheme.AMOLED, systemDark = false, oled = true, dynamicColor = true, expectDark = true, expectOled = true),
    )

    @Test
    fun combinationsMatchEffectiveDarkAndOled() {
        cases.forEach { case ->
            assertEquals(
                "dark theme=${case.theme} systemDark=${case.systemDark} oled=${case.oled} dynamic=${case.dynamicColor}",
                case.expectDark,
                isEffectiveDark(case.theme, case.systemDark)
            )
            assertEquals(
                "oled theme=${case.theme} systemDark=${case.systemDark} oled=${case.oled} dynamic=${case.dynamicColor}",
                case.expectOled,
                shouldApplyOledBlack(case.theme, case.oled, case.systemDark)
            )
        }
    }

    @Test
    fun followingSystemDoesNotClearOledPreferenceWhenSystemTurnsLightThenDark() {
        val afterSelectingSystem = selectThemeMode(CuteTheme.DARK, storedOled = true, newMode = CuteTheme.SYSTEM)
        assertEquals(CuteTheme.SYSTEM, afterSelectingSystem.themeMode)
        assertTrue(afterSelectingSystem.oledBlack)

        assertFalse(
            shouldApplyOledBlack(
                afterSelectingSystem.themeMode,
                afterSelectingSystem.oledBlack,
                systemDark = false
            )
        )
        assertTrue(
            shouldApplyOledBlack(
                afterSelectingSystem.themeMode,
                afterSelectingSystem.oledBlack,
                systemDark = true
            )
        )
    }

    @Test
    fun legacyAmoledSurvivesThemeChangeAndRestartAsDarkPlusOled() {
        val migrated = resolveAppearancePrefs(CuteTheme.AMOLED, storedOled = false)
        assertEquals(CuteTheme.DARK, migrated.themeMode)
        assertTrue(migrated.oledBlack)

        val switchedToSystem = selectThemeMode(CuteTheme.AMOLED, storedOled = false, newMode = CuteTheme.SYSTEM)
        assertEquals(CuteTheme.SYSTEM, switchedToSystem.themeMode)
        assertTrue(switchedToSystem.oledBlack)

        val afterRestart = reloadAppearancePrefs(switchedToSystem.themeMode, switchedToSystem.oledBlack)
        assertEquals(CuteTheme.SYSTEM, afterRestart.themeMode)
        assertTrue(afterRestart.oledBlack)
        assertTrue(shouldApplyOledBlack(afterRestart.themeMode, afterRestart.oledBlack, systemDark = true))
        assertFalse(shouldApplyOledBlack(afterRestart.themeMode, afterRestart.oledBlack, systemDark = false))
    }

    @Test
    fun restartReloadsPersistedSystemPlusOledWithoutReset() {
        val persisted = AppearancePrefs(CuteTheme.SYSTEM, oledBlack = true)
        val reloaded = reloadAppearancePrefs(persisted.themeMode, persisted.oledBlack)
        assertEquals(persisted, reloaded)
        assertTrue(shouldApplyOledBlack(reloaded.themeMode, reloaded.oledBlack, systemDark = true))
    }

    @Test
    fun turningOledOffFromLegacyAmoledKeepsDarkMode() {
        val updated = setOledBlackPreference(CuteTheme.AMOLED, enabled = false)
        assertEquals(CuteTheme.DARK, updated.themeMode)
        assertFalse(updated.oledBlack)
        assertFalse(shouldApplyOledBlack(updated.themeMode, updated.oledBlack, systemDark = true))
        assertTrue(isEffectiveDark(updated.themeMode, systemDark = false))
    }

    @Test
    fun oledOverridesGrayAndDynamicDarkPageBackgroundsButKeepsCardsAndAccents() {
        val grayDark = darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            surfaceDim = Color(0xFF141414),
            surfaceContainerLowest = Color(0xFF0F0F0F),
            surfaceContainer = Color(0xFF2A2A2A),
            surfaceContainerHigh = Color(0xFF333333),
            primary = Color(0xFFAA88FF)
        )
        val dynamicDark = darkColorScheme(
            background = Color(0xFF1A1216),
            surface = Color(0xFF20181C),
            surfaceDim = Color(0xFF161014),
            surfaceContainerLowest = Color(0xFF140E12),
            surfaceContainer = Color(0xFF2C2226),
            surfaceContainerHigh = Color(0xFF382A30),
            primary = Color(0xFFFF88AA)
        )

        listOf(grayDark, dynamicDark).forEach { scheme ->
            val applied = scheme.withOledPageBackgrounds()
            assertEquals(Color.Black, applied.background)
            assertEquals(Color.Black, applied.surface)
            assertEquals(Color.Black, applied.surfaceDim)
            assertEquals(Color.Black, applied.surfaceContainerLowest)
            assertEquals(Color(0xFF000000), applied.background)
            assertEquals(scheme.surfaceContainer, applied.surfaceContainer)
            assertEquals(scheme.surfaceContainerHigh, applied.surfaceContainerHigh)
            assertEquals(scheme.primary, applied.primary)
        }
    }

    @Test
    fun lightSchemeIsNotBlackenedByTheOledDecision() {
        val light = lightColorScheme(
            background = Color(0xFFFFFBFF),
            surface = Color(0xFFFFFBFF),
            primary = Color(0xFF6750A4)
        )
        assertFalse(shouldApplyOledBlack(CuteTheme.LIGHT, storedOled = true, systemDark = true))
        assertFalse(shouldApplyOledBlack(CuteTheme.SYSTEM, storedOled = true, systemDark = false))
        // The color helper itself is only applied when the decision is true;
        // light page colors must remain if it is not used.
        assertEquals(Color(0xFFFFFBFF), light.background)
        assertEquals(Color(0xFFFFFBFF), light.surface)
    }
}
