package com.github.andreyasadchy.xtra.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.github.andreyasadchy.xtra.ui.common.RecoveringWebViewClient
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.ChannelActionSession
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.security.MessageDigest
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.repository.TwitchModeratorRole
import com.github.andreyasadchy.xtra.repository.text
import com.github.andreyasadchy.xtra.util.WhisperHistorySession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Twitch's moderator-capable web controls, kept over the current channel/player. */
class TwitchPollDialog : BottomSheetDialogFragment() {
    private var browser: WebView? = null
    private var owner: String? = null
    private var webLoginAtOpen: String? = null
    private var session: com.github.andreyasadchy.xtra.repository.ChannelActionsApi? = null
    private var roleCheck: Job? = null

    companion object {
        fun newInstance(channelId: String, channelLogin: String, fullTools: Boolean = false) = TwitchPollDialog().apply {
            arguments = Bundle().apply {
                putString("channel_id", channelId)
                putString("channel_login", channelLogin)
                putBoolean("full_tools", fullTools)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        owner = context.tokenPrefs().getString(C.USER_ID, null)
        webLoginAtOpen = context.tokenPrefs().getString(C.GQL_TOKEN_WEB, null)
        val login = requireArguments().getString("channel_login").orEmpty()
        require(login.matches(Regex("[A-Za-z0-9_]{1,25}")))
        val fullTools = requireArguments().getBoolean("full_tools")
        val target = TwitchPanelTarget(login, fullTools)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Button(context).apply {
            setText(com.github.andreyasadchy.xtra.R.string.hex_mod_back_controls)
            isAllCaps = false
            setOnClickListener { dismiss() }
        })
        val notice = TextView(context).apply {
            setText(com.github.andreyasadchy.xtra.R.string.hex_mod_checking_channel)
            setPadding(16, 8, 16, 8)
        }
        root.addView(notice)
        val web = WebView(context).apply {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val identity = MessageDigest.getInstance("SHA-256").digest(owner.orEmpty().toByteArray()).joinToString("") { "%02x".format(it) }
                WebViewCompat.setProfile(this, "hexstream-moderation-$identity")
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : RecoveringWebViewClient() {
                override fun onBrowserFailure() {
                    roleCheck?.cancel()
                    browser = null
                    notice.setText(com.github.andreyasadchy.xtra.R.string.hex_mod_web_stopped)
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !target.allowsNavigation(request.url.toString())
                }
            }
        }
        browser = web
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.layoutParams = ViewGroup.LayoutParams(-1, (resources.displayMetrics.heightPixels * .85f).toInt())
        roleCheck = lifecycleScope.launch {
            try {
                val active = ChannelActionSession.open(context)
                val channelId = requireArguments().getString("channel_id").orEmpty()
                active.requireModerator(channelId)
                val channel = active.userById(channelId)
                check(channel.text("id") == channelId && channel.text("login").equals(login, true))
                if (browser !== web || !isAdded) return@launch
                session = active
                val usedSavedLogin = useOwnedLogin(web)
                active.checkCurrent()
                if (browser !== web || !isAdded) return@launch
                notice.text = if (usedSavedLogin) "Twitch controls for $login using your active account's web login. Complete any Twitch verification here."
                    else "Twitch controls for $login. Sign in here with your active HexStream account and check the account shown before using the controls."
                web.loadUrl(target.url)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { notice.setText(com.github.andreyasadchy.xtra.R.string.hex_mod_web_access_failed) }
        }
        return root
    }

    private suspend fun useOwnedLogin(web: WebView): Boolean {
        // Only the per-account in-app profile receives this login. The external browser is untouched.
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return false
        val context = requireContext().applicationContext
        val token = context.tokenPrefs().getString(C.GQL_TOKEN_WEB, null) ?: return false
        if (!token.matches(Regex("[A-Za-z0-9]+"))) return false
        val headers = WhisperHistorySession.headers(context)
        if (headers[C.HEADER_TOKEN] != "OAuth $token") return false
        val expectedOwner = owner ?: return false
        val role = TwitchModeratorRole((context as XtraApp).xtraModule.okHttpClient.value, expectedOwner, headers) {
            context.tokenPrefs().getString(C.USER_ID, null) == expectedOwner && context.tokenPrefs().getString(C.GQL_TOKEN_WEB, null) == token
        }
        val allowed = try { role.check(requireArguments().getString("channel_id").orEmpty()) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { false }
        if (!allowed || browser !== web) return false
        val cookies = WebViewCompat.getProfile(web).cookieManager
        return suspendCancellableCoroutine { continuation ->
            cookies.setCookie("https://www.twitch.tv", "auth-token=$token; Domain=.twitch.tv; Path=/; Secure; SameSite=Lax") { accepted ->
                if (continuation.isActive) continuation.resume(accepted)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (owner != requireContext().tokenPrefs().getString(C.USER_ID, null) ||
            webLoginAtOpen != requireContext().tokenPrefs().getString(C.GQL_TOKEN_WEB, null) ||
            runCatching { session?.checkCurrent() }.isFailure) {
            dismissAllowingStateLoss()
            return
        }
        browser?.onResume()
    }

    override fun onPause() { browser?.onPause(); super.onPause() }
    override fun onDestroyView() {
        roleCheck?.cancel()
        browser?.let { web ->
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        browser = null
        super.onDestroyView()
    }
}
