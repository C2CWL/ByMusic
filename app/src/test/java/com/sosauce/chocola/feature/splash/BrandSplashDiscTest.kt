package com.sosauce.chocola.feature.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The system splash icon (vector XML) and the in-app redraw (BrandSplashDisc)
 * must describe the same disc, otherwise the hand-off frame visibly jumps.
 */
class BrandSplashDiscTest {

    private val xml: String by lazy { locate("src/main/res/drawable/splash_system_mark.xml").readText() }

    @Test
    fun iconCanvasIs288dpWithA112dpDiscInsideThe160dpCircle() {
        assertTrue(xml.contains("android:width=\"288dp\""))
        assertTrue(xml.contains("android:height=\"288dp\""))
        assertTrue(xml.contains("android:viewportWidth=\"288\""))
        assertTrue(xml.contains("android:viewportHeight=\"288\""))
        assertEquals(288f, BrandSplashDisc.CANVAS)
        assertEquals(112f, BrandSplashDisc.DIAMETER)
        assertTrue(BrandSplashDisc.DIAMETER <= 160f)
        assertFalse("no bitmap inside the system icon", xml.contains("<bitmap"))
        assertFalse(xml.contains("splash_vinyl_bg"))
    }

    @Test
    fun circlesInXmlMatchTheSpec() {
        val expected = mutableMapOf(
            "body" to BrandSplashDisc.RADIUS,
            "label" to BrandSplashDisc.RADIUS * BrandSplashDisc.LABEL_FRACTION,
            "hole" to BrandSplashDisc.RADIUS * BrandSplashDisc.HOLE_FRACTION,
        )
        BrandSplashDisc.GROOVES.forEachIndexed { i, f -> expected["groove_$i"] = BrandSplashDisc.RADIUS * f }
        for ((name, radius) in expected) {
            val data = pathData(name)
            val m = Regex("""^M([\d.]+),([\d.]+) a([\d.]+),([\d.]+) 0 1,0""").find(data)
                ?: throw AssertionError("$name is not a circle: $data")
            val startX = m.groupValues[1].toFloat()
            val startY = m.groupValues[2].toFloat()
            val r = m.groupValues[3].toFloat()
            assertEquals("$name radius", radius, r, 0.002f)
            assertEquals("$name centre x", BrandSplashDisc.CENTER, startX + r, 0.002f)
            assertEquals("$name centre y", BrandSplashDisc.CENTER, startY, 0.002f)
        }
    }

    @Test
    fun grooveAlphasAndColorsMatchTheSpec() {
        BrandSplashDisc.GROOVES.indices.forEach { i ->
            val block = block("groove_$i")
            assertTrue(block.contains("android:strokeColor=\"#E2DCD0\""))
            val alpha = Regex("""strokeAlpha="([\d.]+)"""").find(block)!!.groupValues[1].toFloat()
            assertEquals(BrandSplashDisc.grooveAlpha(i), alpha, 0.001f)
        }
        assertTrue(block("body").contains("#0C0C0C"))
        assertTrue(block("label").contains("#28241F"))
    }

    @Test
    fun sheenArcsMatchTheSpec() {
        val layers = BrandSplashDisc.SHEEN_HALF_SPANS.size
        assertEquals(layers, BrandSplashDisc.SHEEN_ALPHAS.size)
        val count = Regex("android:name=\"sheen_").findAll(xml).count()
        assertEquals(layers * BrandSplashDisc.SHEEN_GROOVES.count(), count)
        for (layer in 0 until layers) {
            for (index in BrandSplashDisc.SHEEN_GROOVES) {
                val block = block("sheen_${layer}_$index")
                val alpha = Regex("""strokeAlpha="([\d.]+)"""").find(block)!!.groupValues[1].toFloat()
                assertEquals(BrandSplashDisc.SHEEN_ALPHAS[layer], alpha, 0.001f)
                val r = Regex(""" A([\d.]+),""").find(block)!!.groupValues[1].toFloat()
                assertEquals(BrandSplashDisc.RADIUS * BrandSplashDisc.GROOVES[index], r, 0.002f)
            }
        }
    }

    @Test
    fun specIsWellFormed() {
        val g = BrandSplashDisc.GROOVES
        for (i in 1 until g.size) assertTrue(g[i] > g[i - 1])
        assertTrue(g.first() > BrandSplashDisc.LABEL_FRACTION)
        assertTrue(g.last() < 1f)
        assertTrue(BrandSplashDisc.SHEEN_GROOVES.last < g.size)
    }

    private fun block(name: String): String {
        val start = xml.indexOf("android:name=\"$name\"")
        assertTrue("missing path $name", start >= 0)
        return xml.substring(start, xml.indexOf("/>", start))
    }

    private fun pathData(name: String): String =
        Regex("""android:pathData="([^"]+)"""").find(block(name))!!.groupValues[1]

    private fun locate(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("missing $relative, cwd=${File(".").canonicalPath}")
    }
}
