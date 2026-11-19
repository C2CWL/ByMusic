package com.sosauce.chocola.feature.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BrandSplashResourceTest {

    @Test
    fun vinylBackgroundIsWebpWithoutWordmarkOrRingBakedInAndReportsSize() {
        val webp = locate("src/main/res/drawable-nodpi/splash_vinyl_bg.webp")
        assertTrue(webp.length() > 10_000)
        assertTrue("expected a compact webp, was ${webp.length()}", webp.length() < 400_000)
        val bytes = webp.readBytes()
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        val tag = String(bytes, 8, 4, Charsets.US_ASCII)
        assertEquals("WEBP", tag)
        val (width, height) = webpSize(bytes)
        assertEquals(1080, width)
        assertEquals(1920, height)
    }

    @Test
    fun systemSplashIsBlackPlusStaticMarkNotTheFullscreenArtwork() {
        val themes = locate("src/main/res/values/themes.xml").readText()
        val cute = themes.substringAfter("name=\"Theme.CuteSplash\"").substringBefore("</style>")
        assertTrue(cute.contains("@color/splash_system_background"))
        assertTrue(cute.contains("@drawable/splash_system_mark"))
        assertFalse(
            "the Android 12+ system splash cannot host the fullscreen vinyl image",
            cute.contains("splash_vinyl_bg")
        )
        val colors = locate("src/main/res/values/colors.xml").readText()
        assertTrue(colors.contains("name=\"splash_system_background\""))
        assertTrue(colors.contains("#000000"))
        val v31 = locate("src/main/res/values-v31/colors.xml").readText()
        assertFalse(
            "do not let dynamic system colors override the brand first-frame black",
            v31.contains("splash_system_background")
        )
        assertTrue(locate("src/main/res/drawable/splash_system_mark.xml").isFile)
        assertFalse(
            "the system mark is now a single vector drawable",
            listOf(
                File("src/main/res/drawable/splash_system_mark_glyph.xml"),
                File("app/src/main/res/drawable/splash_system_mark_glyph.xml")
            ).any { it.isFile }
        )
        assertFalse(
            "no icon background: the disc sits directly on the black field",
            cute.contains("windowSplashScreenIconBackgroundColor")
        )
    }

    @Test
    fun wordmarkFontAndLicensesAreBundled() {
        assertTrue(locate("src/main/res/font/cormorant_garamond.ttf").isFile)
        val ofl = locate("src/main/assets/licenses/CormorantGaramond-OFL.txt").readText()
        assertTrue(ofl.contains("SIL Open Font License"))
        assertTrue(ofl.contains("Cormorant"))
        val art = locate("src/main/assets/licenses/SPLASH_ARTWORK.txt").readText()
        assertTrue(art.contains("720"))
        assertTrue(art.contains("1080"))
        assertTrue(art.contains("not a native 1080"))
        assertTrue(art.lowercase().contains("not derived"))
    }

    @Test
    fun mainActivityKeepsSystemSplashUntilFirstFrameAndDoesNotCoverSetupByDefault() {
        val main = locate("src/main/java/com/sosauce/chocola/app/MainActivity.kt").readText()
        val onCreate = main.substringAfter("override fun onCreate").substringBefore("setContent")
        val installAt = onCreate.indexOf("installSplashScreen()")
        val superAt = onCreate.indexOf("super.onCreate")
        assertTrue(installAt >= 0 && superAt >= 0 && installAt < superAt)
        assertTrue(onCreate.contains("setKeepOnScreenCondition"))
        assertTrue(
            "system splash is removed directly, without the default exit animation",
            main.substringAfter("override fun onCreate").contains("setOnExitAnimationListener")
        )
        assertTrue(main.contains("provider.remove()"))
        assertTrue(main.contains("handoff = splashHandoff"))
        assertTrue(main.contains("BrandSplashHost("))
        assertTrue(main.contains("SetupScreen"))
        assertTrue(main.contains("hasMusicPermission"))
    }

    @Test
    fun layoutConstantsMatchConfirmedPreview() {
        assertEquals(0.70f, BrandSplashLayout.WORDMARK_BASELINE_FRACTION)
        assertEquals(0.88f, BrandSplashLayout.RING_CENTER_FRACTION)
        assertEquals(0.18f, BrandSplashLayout.WORDMARK_TRACKING_EM, 0.0f)
        assertEquals(78f, BrandSplashLayout.WORDMARK_FONT_PX_AT_1080)
    }

    private fun webpSize(bytes: ByteArray): Pair<Int, Int> {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunk = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val dataStart = offset + 8
            when (chunk) {
                "VP8X" -> {
                    val w = (bytes[dataStart + 4].toInt() and 0xFF) or
                        ((bytes[dataStart + 5].toInt() and 0xFF) shl 8) or
                        ((bytes[dataStart + 6].toInt() and 0xFF) shl 16)
                    val h = (bytes[dataStart + 7].toInt() and 0xFF) or
                        ((bytes[dataStart + 8].toInt() and 0xFF) shl 8) or
                        ((bytes[dataStart + 9].toInt() and 0xFF) shl 16)
                    return (w + 1) to (h + 1)
                }
                "VP8 " -> {
                    // lossy: 16-bit widths starting at dataStart+6, 14-bit fields
                    val w = (bytes[dataStart + 6].toInt() and 0xFF) or
                        ((bytes[dataStart + 7].toInt() and 0x3F) shl 8)
                    val h = (bytes[dataStart + 8].toInt() and 0xFF) or
                        ((bytes[dataStart + 9].toInt() and 0x3F) shl 8)
                    return w to h
                }
                "VP8L" -> {
                    val bits = ByteBuffer.wrap(bytes, dataStart + 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    val w = (bits and 0x3FFF) + 1
                    val h = ((bits shr 14) and 0x3FFF) + 1
                    return w to h
                }
            }
            offset = dataStart + ((size + 1) and 1.inv())
        }
        throw AssertionError("no VP8/VP8L/VP8X chunk found")
    }

    private fun locate(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../$relative")
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("missing $relative, cwd=${File(".").canonicalPath}")
    }
}
