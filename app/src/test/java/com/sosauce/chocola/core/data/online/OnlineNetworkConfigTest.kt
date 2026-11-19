package com.sosauce.chocola.core.data.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the network-related manifest/config choices of the online foundation. */
class OnlineNetworkConfigTest {

    @Test
    fun manifestDeclaresNetworkAndUsesSecurityConfig() {
        val manifest = locate("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertFalse(
            "cleartext must not be enabled globally",
            manifest.contains("usesCleartextTraffic=\"true\"")
        )
        val networkState = manifest.substringAfter("android.permission.ACCESS_NETWORK_STATE", "")
            .substringBefore("/>")
        assertFalse("ACCESS_NETWORK_STATE must no longer be removed", networkState.contains("tools:node=\"remove\""))
    }

    @Test
    fun cleartextIsOffAndUserCertificatesAreNotTrusted() {
        val config = locate("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(config.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(config.contains("cleartextTrafficPermitted=\"true\""))
        assertTrue(config.contains("src=\"system\""))
        assertFalse(config.contains("src=\"user\""))
    }

    @Test
    fun playerKeepsDefaultDataSourceForLocalUris() {
        val factory = locate("src/main/java/com/sosauce/chocola/core/data/online/OnlineDataSources.kt").readText()
        assertTrue(factory.contains("DefaultDataSource.Factory(context, remote)"))
        assertTrue(factory.contains("LazyDataSource"))
        val resolver = locate("src/main/java/com/sosauce/chocola/core/data/online/OnlineDataSourceResolver.kt").readText()
        assertTrue(resolver.contains("OnlinePlaybackUri.parse(dataSpec.uri.toString()) ?: return dataSpec"))
    }

    private fun locate(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("cannot find $relative from ${File(".").absolutePath}")
    }
}
