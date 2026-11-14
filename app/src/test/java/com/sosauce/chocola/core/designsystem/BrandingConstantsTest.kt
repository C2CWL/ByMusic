package com.sosauce.chocola.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrandingConstantsTest {

    @Test
    fun githubLinkPointsToByMusicRepository() {
        assertEquals("https://github.com/C2CWL/ByMusic", BYMUSIC_GITHUB)
        assertFalse(BYMUSIC_GITHUB.contains("sosauce", ignoreCase = true))
        assertFalse(BYMUSIC_GITHUB.contains("Chocola"))
    }
}
