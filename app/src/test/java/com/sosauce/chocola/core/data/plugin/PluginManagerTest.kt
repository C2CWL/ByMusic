package com.sosauce.chocola.core.data.plugin

import com.sosauce.chocola.core.data.plugin.PluginTestSupport.fictionalPlugin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PluginManagerTest {

    private lateinit var dir: File
    private var remote: String = fictionalPlugin
    private val fetched = mutableListOf<String>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("bymusic-plugins").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun manager(store: PluginStore = PluginStore(dir)) = PluginManager(
        store = store,
        host = PluginTestSupport.host(),
        fetcher = PluginSourceFetcher { url -> fetched += url; remote },
        clock = { 1_000L }
    )

    @Test
    fun installEnableUpdateDeleteFlow() = runBlocking {
        val m = manager()
        val candidate = m.inspect(fictionalPlugin, PluginSourceKind.LOCAL_FILE)
        assertEquals("虚构音源", candidate.manifest.platform)
        assertNull(candidate.existing)
        assertTrue("inspect must not save anything", m.plugins.value.isEmpty())

        val installed = m.install(candidate)
        assertTrue(installed.enabled)
        assertEquals(PluginStore.fileNameFor("虚构音源"), installed.fileName)
        assertTrue(File(dir, "index.json").isFile)
        assertEquals(fictionalPlugin, m.readCode("虚构音源"))
        assertEquals(1, m.usablePlugins().size)

        // Persisted: a new manager on the same directory sees it.
        val reopened = manager()
        reopened.ensureLoaded()
        assertEquals(listOf("虚构音源"), reopened.plugins.value.map { it.id })

        m.setUserVariables("虚构音源", mapOf("token" to "t"))
        m.setEnabled("虚构音源", false)
        assertTrue(m.usablePlugins().isEmpty())
        try {
            m.runtime("虚构音源")
            fail("disabled plugin must not run")
        } catch (e: PluginException) {
            assertEquals(PluginErrorType.LOAD, e.type)
        }

        // Same code at the update address: nothing to update.
        assertNull(m.checkUpdate("虚构音源"))
        assertEquals(listOf("https://plugins.example.com/fictional.js"), fetched)

        // Newer version: a candidate is offered but not installed until asked.
        remote = PluginTestSupport.fictionalPlugin("1.10.0")
        val update = m.checkUpdate("虚构音源")
        assertNotNull(update)
        assertEquals(1, update!!.versionVsInstalled)
        assertEquals("1.2.0", m.plugins.value.single().manifest.version)
        m.install(update)
        val after = m.plugins.value.single().plugin
        assertEquals("1.10.0", after.manifest.version)
        assertFalse("enabled state is kept on update", after.enabled)
        assertEquals(mapOf("token" to "t"), after.userVariables)
        assertEquals(PluginSourceKind.URL, after.sourceKind)

        // Older version is not offered.
        remote = PluginTestSupport.fictionalPlugin("1.9.9")
        assertNull(m.checkUpdate("虚构音源"))

        // A different plugin at the address is refused.
        remote = fictionalPlugin.replace("platform: '虚构音源'", "platform: '别的'")
        try {
            m.checkUpdate("虚构音源")
            fail("must refuse a different plugin")
        } catch (e: PluginException) {
            assertTrue(e.message!!.contains("别的"))
        }

        m.delete("虚构音源")
        assertTrue(m.plugins.value.isEmpty())
        assertFalse(File(dir, "code/${after.fileName}").exists())
        assertNull(m.readCode("虚构音源"))
    }

    @Test
    fun brokenStoredCodeMarksLoadError() = runBlocking {
        val m = manager()
        m.install(m.inspect(fictionalPlugin, PluginSourceKind.LOCAL_FILE))
        val file = File(dir, "code/${PluginStore.fileNameFor("虚构音源")}")
        assertTrue(file.isFile)
        file.writeText("syntax error here (")
        val fresh = manager()
        try {
            fresh.runtime("虚构音源")
            fail("expected load failure")
        } catch (e: PluginException) {
            assertEquals(PluginErrorType.LOAD, e.type)
        }
        assertNotNull(fresh.plugins.value.single().loadError)
        assertTrue(fresh.usablePlugins().isEmpty())
    }

    @Test
    fun emptyOrInvalidSourceIsRejected() = runBlocking {
        val m = manager()
        for (code in listOf("", "   ", "module.exports = 42;")) {
            try {
                m.inspect(code, PluginSourceKind.LOCAL_FILE)
                fail("should reject: '$code'")
            } catch (e: PluginException) {
                assertEquals(PluginErrorType.LOAD, e.type)
            }
        }
    }

    @Test
    fun versionCompareIsPartByPart() {
        assertTrue(PluginVersion.compare("1.10.0", "1.9.9") > 0)
        assertTrue(PluginVersion.compare("2.0.0", "1.99.99") > 0)
        assertEquals(0, PluginVersion.compare("1.2", "1.2.0"))
        assertEquals(0, PluginVersion.compare("v1.2.0", "1.2.0"))
        assertTrue(PluginVersion.compare(null, "0.0.1") < 0)
        assertTrue(PluginVersion.compare("1.2.3-beta", "1.2.2") > 0)
    }

    @Test
    fun storeFileNamesDoNotLeakPluginNames() {
        val name = PluginStore.fileNameFor("../../etc/passwd")
        assertTrue(name.matches(Regex("[0-9a-f]{40}\\.js")))
        assertEquals(64, PluginStore.sha256("x").length)
    }
}
