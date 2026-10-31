package com.github.andreyasadchy.xtra.ui.common


import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogIntegrityBinding
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.github.andreyasadchy.xtra.util.WhisperHistorySession
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class IntegrityDialog : DialogFragment() {

    interface Listener {
        fun onIntegrityTokenLoaded(callback: String?)
    }

    private var _binding: DialogIntegrityBinding? = null
    private val binding get() = _binding!!
    private var listener: Listener? = null
    private var rendererGone = false
    private val whisperProofCaptured = AtomicBoolean(false)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? Listener ?: activity as? Listener
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogIntegrityBinding.inflate(layoutInflater)
        val context = requireContext()
        val whisperOwner = arguments?.getString(KEY_WHISPER_OWNER)
        val whisperHeaders = if (whisperOwner != null && context.tokenPrefs().getString(C.USER_ID, null) == whisperOwner) WhisperHistorySession.baseHeaders(context) else emptyMap()
        val builder = context.getAlertDialogBuilder()
            .setView(binding.root)
        val cookies = if (whisperOwner != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.setProfile(binding.webView, "hexstream-whisper-check")
            WebViewCompat.getProfile(binding.webView).cookieManager
        } else CookieManager.getInstance()
        if (whisperOwner == null) cookies.removeAllCookies(null)
        val token = (if (whisperOwner == null) TwitchApiHelper.getGQLHeaders(context, true) else whisperHeaders)[C.HEADER_TOKEN]?.removePrefix("OAuth ")
        if (!token.isNullOrBlank()) {
            cookies.setCookie("https://www.twitch.tv", "auth-token=$token")
        }
        with(binding.webView) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webChromeClient = WebChromeClient()
            webViewClient = object : RecoveringWebViewClient() {
                override fun onBrowserFailure() {
                    rendererGone = true
                    android.widget.Toast.makeText(context, R.string.hex_browser_retry, android.widget.Toast.LENGTH_LONG).show(); dismissAllowingStateLoss()
                }

                override fun shouldInterceptRequest(view: WebView, webViewRequest: WebResourceRequest): WebResourceResponse? {
                    if (!webViewRequest.requestHeaders.entries.find { it.key.equals("Client-Integrity", true) }?.value.isNullOrBlank()) {
                        if (whisperOwner != null) {
                            val received = webViewRequest.requestHeaders
                            fun header(name: String) = received.entries.find { it.key.equals(name, true) }?.value
                            if (webViewRequest.url.scheme == "https" && webViewRequest.url.host == "gql.twitch.tv" &&
                                context.tokenPrefs().getString(C.USER_ID, null) == whisperOwner &&
                                header(C.HEADER_TOKEN) == whisperHeaders[C.HEADER_TOKEN] && header(C.HEADER_CLIENT_ID) == whisperHeaders[C.HEADER_CLIENT_ID] &&
                                whisperProofCaptured.compareAndSet(false, true)) {
                                val safe = received.filterKeys { name -> name.equals(C.HEADER_TOKEN, true) || name.equals(C.HEADER_CLIENT_ID, true) || name.equals("Client-Integrity", true) || name.equals("X-Device-Id", true) }
                                context.tokenPrefs().edit {
                                    putString(WhisperHistorySession.VERIFIED_HEADERS, JSONObject(safe).toString())
                                    putLong(WhisperHistorySession.VERIFIED_UNTIL, System.currentTimeMillis() + 57600000)
                                }
                                activity?.runOnUiThread { listener?.onIntegrityTokenLoaded(requireArguments().getString(KEY_CALLBACK)); dismissAllowingStateLoss() }
                            }
                            return super.shouldInterceptRequest(view, webViewRequest)
                        }
                        context.tokenPrefs().edit {
                            putLong(C.INTEGRITY_EXPIRATION, System.currentTimeMillis() + 57600000)
                            putString(C.GQL_HEADERS, JSONObject(
                                if (context.prefs().getBoolean(C.GET_ALL_GQL_HEADERS, false)) {
                                    webViewRequest.requestHeaders
                                } else {
                                    webViewRequest.requestHeaders.filterKeys {
                                        it.equals(C.HEADER_TOKEN, true) ||
                                                it.equals(C.HEADER_CLIENT_ID, true) ||
                                                it.equals("Client-Integrity", true) ||
                                                it.equals("X-Device-Id", true)
                                    }
                                }
                            ).toString())
                        }
                        listener?.onIntegrityTokenLoaded(requireArguments().getString(KEY_CALLBACK))
                        dismiss()
                    }
                    return super.shouldInterceptRequest(view, webViewRequest)
                }
            }
            loadUrl("https://www.twitch.tv/login")
        }
        return builder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        (activity as? MainActivity)?.integrityTokenLoaded()
        if (!rendererGone) _binding?.webView?.loadUrl("about:blank")
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_CALLBACK = "callback"
        private const val KEY_WHISPER_OWNER = "whisper_owner"

        fun newInstance(callback: String?): IntegrityDialog {
            return IntegrityDialog().apply {
                arguments = Bundle().apply {
                    putString(KEY_CALLBACK, callback)
                }
            }
        }
        fun forWhispers(owner: String) = newInstance("whispers").apply { requireArguments().putString(KEY_WHISPER_OWNER, owner) }
    }
}
