package com.github.andreyasadchy.xtra.util

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.db.AppDatabase
import com.github.andreyasadchy.xtra.db.GlobalPlaybackProxies
import com.github.andreyasadchy.xtra.db.PlaybackProxyDatabase
import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import org.junit.Assert.*
import org.junit.Test

class GlobalPlaybackProxiesDeviceTest {
    @Test fun accountFacadesSharePresetsEditsAndCustomUrlsWithoutReseeding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Run migration fixtures only in the isolated QA app", "com.github.andreyasadchy.xtra.qa", context.packageName)
        val state = context.getSharedPreferences("global_playback_proxy_migrations", Context.MODE_PRIVATE)
        val previous = state.all
        val global = Room.inMemoryDatabaseBuilder(context, PlaybackProxyDatabase::class.java).build()
        val legacyA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val legacyB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            check(state.edit().clear().commit())
            val customUrl = "https://fixture.invalid/live/\$channel"
            legacyA.customProxies().insert(CustomProxy(customUrl, false, 0, false))
            legacyB.customProxies().insert(CustomProxy(customUrl, true, 0, true))
            val first = GlobalPlaybackProxies(context, "fixture_a", legacyA.customProxies(), global)
            val seeded = first.getAll()
            val presetCount = PlaybackProxyPresets.urls.size
            assertEquals(PlaybackProxyPresets.urls, seeded.take(presetCount).map { it.url })
            assertEquals(presetCount + 1, seeded.size)
            seeded.first().enabled = false
            first.update(seeded.first())
            val second = GlobalPlaybackProxies(context, "fixture_b", legacyB.customProxies(), global)
            val shared = second.getAll()
            assertEquals(presetCount + 1, shared.size)
            assertFalse(shared.first().enabled)
            assertFalse(shared.single { it.url == customUrl }.enabled)
            assertFalse(shared.single { it.url == customUrl }.addQueryParams)
            first.delete(shared[1])
            assertEquals(presetCount, second.getAll().size)
            assertFalse(second.getAll().any { it.url == PlaybackProxyPresets.urls[1] })
            assertEquals(1, legacyA.customProxies().getAll().size)
            assertEquals(1, legacyB.customProxies().getAll().size)
        } finally {
            global.close(); legacyA.close(); legacyB.close()
            val restore = state.edit().clear()
            previous.forEach { (key, value) -> if (value is Boolean) restore.putBoolean(key, value) }
            check(restore.commit())
        }
    }
}
