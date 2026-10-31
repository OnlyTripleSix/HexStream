package com.github.andreyasadchy.xtra.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

/** Real account-scoped database, synthetic channel, empty headers: cannot issue a Twitch mutation. */
class ChannelAlertsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun fixture(test: (XtraApp, ChannelPagerViewModel) -> Unit) {
        val app = instrumentation.targetContext.applicationContext as XtraApp
        check(app.packageName == "com.github.andreyasadchy.xtra.qa")
        check(app.tokenPrefs().getString(C.USER_ID, null).isNullOrBlank())
        val module = app.xtraModule
        lateinit var model: ChannelPagerViewModel
        instrumentation.runOnMainSync {
            model = ChannelPagerViewModel(module.localChannelFollowsRepository, module.offlineVideosRepository,
                module.bookmarksRepository, module.notificationsRepository, module.graphQLRepository, module.helixRepository,
                module.httpEngine, module.cronetEngine, module.cronetExecutor, module.okHttpClient,
                SavedStateHandle(mapOf("channelLogin" to "fixture_channel")))
        }
        try { test(app, model) }
        finally {
            instrumentation.runOnMainSync { model.viewModelScope.cancel() }
            runBlocking { module.notificationsRepository.deleteUser(NotificationUser("990000099")) }
        }
    }
    private fun await(check: () -> Boolean) {
        repeat(150) { if (check()) return; android.os.SystemClock.sleep(20) }
        fail("Synthetic channel alert did not reach the expected state")
    }
    private fun resolve(model: ChannelPagerViewModel) = instrumentation.runOnMainSync {
        @Suppress("UNCHECKED_CAST")
        (model.user as MutableStateFlow<User?>).value = User(id = "990000099", login = "fixture_channel")
        model.isFollowingChannel(null, model.channelId, "fixture_channel", 1, C.OKHTTP, emptyMap(), emptyMap())
    }
    @Test fun usernameOnlyPageSavesAndRemovesAlertWithoutCreatingAFollow() = fixture { app, model ->
        assertNull(model.channelId); assertNull(model.notificationsEnabled.value)
        resolve(model)
        await { model.notificationsEnabled.value == false && model.isFollowing.value == false }
        instrumentation.runOnMainSync { model.enableNotifications(null, model.channelId, 1, false, C.OKHTTP, emptyMap(), false) }
        await { model.notificationsEnabled.value == true }
        runBlocking {
            assertNotNull(app.xtraModule.notificationsRepository.getUserById("990000099"))
            assertNull(app.xtraModule.localChannelFollowsRepository.getById("990000099"))
        }
        assertEquals(false, model.isFollowing.value)
        instrumentation.runOnMainSync { model.disableNotifications(null, model.channelId, 1, C.OKHTTP, emptyMap(), false) }
        await { model.notificationsEnabled.value == false }
        runBlocking { assertNull(app.xtraModule.notificationsRepository.getUserById("990000099")) }
    }
    @Test fun usernameOnlyPageRestoresAnIndependentSavedAlert() = fixture { app, model ->
        runBlocking { app.xtraModule.notificationsRepository.saveUser(NotificationUser("990000099")) }
        resolve(model)
        await { model.notificationsEnabled.value == true && model.isFollowing.value == false }
        assertEquals("990000099", model.channelId)
        runBlocking { assertNull(app.xtraModule.localChannelFollowsRepository.getById("990000099")) }
    }
}
