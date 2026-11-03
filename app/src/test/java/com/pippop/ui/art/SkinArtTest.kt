package com.pippop.ui.art

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SkinArtTest {
    private val dir = File("../skins")

    @Test
    fun everyPublishedSkinReadsAndIsListed() {
        val index = JSONObject(File(dir, "index.json").readText()).getJSONArray("skins")
        assertTrue(index.length() >= 13)
        for (i in 0 until index.length()) {
            val entry = index.getJSONObject(i)
            val id = entry.getString("id")
            val file = File(dir, "$id.json")
            val art = SkinArt.parse(file.readText())
            assertEquals(id, art.id)
            assertEquals(entry.getString("name"), art.name)
            assertEquals(entry.getInt("version"), art.version)
            // The size the store shows is the file's.
            assertEquals(file.length().toInt(), entry.getInt("size"))
            assertEquals(ShipKind.entries.toSet(), art.ships.keys)
            assertTrue("$id has no sea", art.sea.isNotEmpty())
            assertTrue("$id has an empty ship", art.ships.values.all { it.isNotEmpty() })
            assertTrue("$id has no marks", art.hit.isNotEmpty() && art.miss.isNotEmpty())
            // Every save has its restore, so a skin can't leave the board clipped.
            val all = art.sea + art.ships.values.flatten() + art.hit + art.miss
            assertEquals(all.count { it == SkinOp.Save }, all.count { it == SkinOp.Restore })
            assertTrue("$id has no store picture", File(dir, "$id.png").length() > 0)
            assertTrue("$id is an unknown kind", art.kind == "flat" || art.kind == "3d")
        }
        // The 3D Fleet is drawn by the app's own 3D page, which has to be there.
        assertEquals("3d", SkinArt.parse(File(dir, "3d-fleet.json").readText()).kind)
        assertTrue(File("src/main/assets/ships3d/ships3d.js").length() > 0)
    }

    @Test
    fun aBrokenFileIsRefused() {
        val broken = listOf("{}", """{"id":"../x","name":"x","sea":[],"ships":{},"hit":[],"miss":[]}""", "not json")
        for (text in broken) assertTrue(runCatching { SkinArt.parse(text) }.isFailure)
    }
}
