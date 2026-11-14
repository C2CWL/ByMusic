package com.sosauce.chocola.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupAppNameTest {

    @Test
    fun newExportsUseByMusicName() {
        assertEquals("ByMusic", SETTINGS_BACKUP_APP_NAME)
        assertEquals(
            "ByMusic",
            BackupSettingsFile(createdAtMillis = 0L, settings = emptyMap()).app
        )
    }

    @Test
    fun importAcceptsByMusicAndLegacyChocolaBackups() {
        assertTrue(isSupportedSettingsBackupApp("ByMusic"))
        assertTrue(isSupportedSettingsBackupApp("Chocola"))
        assertFalse(isSupportedSettingsBackupApp("OtherApp"))
        assertFalse(isSupportedSettingsBackupApp(""))
    }
}
