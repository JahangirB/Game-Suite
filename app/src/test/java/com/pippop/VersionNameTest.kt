package com.pippop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/** The version people see comes from app/version.properties; versionCode (the run number) is what the updater compares. */
class VersionNameTest {
    @Test fun buildUsesTheNameInVersionProperties() {
        val props = Properties().apply { File("version.properties").reader().use { load(it) } }
        val name = props.getProperty("versionName")
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(name))
        assertEquals(name, BuildConfig.VERSION_NAME)
    }
}
