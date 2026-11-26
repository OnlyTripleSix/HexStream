package com.github.andreyasadchy.xtra.db

import android.content.Context
import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import com.github.andreyasadchy.xtra.util.PlaybackProxyPresets

/** Synchronous DAO facade; PlayerRepository invokes every operation on Dispatchers.IO. */
class GlobalPlaybackProxies(
    context: Context,
    private val accountKey: String,
    private val legacy: CustomProxiesDao,
    private val database: PlaybackProxyDatabase,
) : CustomProxiesDao {
    private val state = context.getSharedPreferences("global_playback_proxy_migrations", Context.MODE_PRIVATE)
    private val dao get() = database.proxies()

    private fun prepare() = synchronized(lock) {
        val seed = !state.getBoolean("seeded_v1", false)
        val import = !state.getBoolean("import_$accountKey", false)
        val automatic = !state.getBoolean("automatic_http_v1", false)
        if (!seed && !import && !automatic) return@synchronized
        database.runInTransaction {
            if (seed && dao.getAll().isEmpty()) dao.insertList(PlaybackProxyPresets.initialItems())
            if (import) {
                // Never change/delete the old account's rows; retain custom URLs and enabled state.
                dao.insertList(PlaybackProxyPresets.imports(dao.getAll(), legacy.getAll()))
            }
            if (automatic) dao.insertList(PlaybackProxyPresets.automaticImports(dao.getAll()))
        }
        check(state.edit().putBoolean("seeded_v1", true).putBoolean("import_$accountKey", true)
            .putBoolean("automatic_http_v1", true).commit())
    }

    override fun getAll(): List<CustomProxy> { prepare(); return dao.getAll() }
    override fun insertList(items: List<CustomProxy>) { prepare(); dao.insertList(items) }
    override fun updateList(items: List<CustomProxy>) { prepare(); dao.updateList(items) }
    override fun insert(item: CustomProxy): Long { prepare(); return dao.insert(item) }
    override fun delete(item: CustomProxy) { prepare(); dao.delete(item) }
    override fun update(item: CustomProxy) { prepare(); dao.update(item) }

    companion object { private val lock = Any() }
}
