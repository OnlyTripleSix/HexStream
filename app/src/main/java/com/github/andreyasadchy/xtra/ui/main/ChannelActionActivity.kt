package com.github.andreyasadchy.xtra.ui.main

import android.content.Intent
import androidx.core.net.toUri
import com.github.andreyasadchy.xtra.R
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.andreyasadchy.xtra.repository.*
import com.github.andreyasadchy.xtra.util.*
import kotlinx.coroutines.*

/** Shared accessible form shell. Requests are account-bound; mutations require an explicit confirmation. */
abstract class ChannelActionActivity : AppCompatActivity() {
    protected lateinit var body: LinearLayout
    protected lateinit var notice: TextView
    protected var api: ChannelActionsApi? = null
    protected var busy = false
    protected var page = 0
    protected fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        WhisperCenter.initialize(application)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        root.addView(Button(this).apply { text = getString(R.string.hex_tools_back); setOnClickListener { finish() } })
        root.addView(TextView(this).apply {
            text = getString(R.string.hex_tools_account, tokenPrefs().getString(C.USERNAME, "Twitch"))
            setPadding(dp(20), dp(8), dp(20), dp(8))
        })
        notice = TextView(this).apply { setPadding(dp(20), dp(8), dp(20), dp(12)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        root.addView(notice)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(24)) }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        try {
            api = ChannelActionSession.open(this)
            notice.text = ""
        } catch (e: ActionFailure) { notice.text = e.message }
    }
    protected fun clear(title: String) { page++; body.removeAllViews(); label(title, 23f) }
    protected fun label(text: String, size: Float = 15f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setPadding(dp(4), dp(10), dp(4), dp(10)); setTextIsSelectable(true)
    }.also { body.addView(it) }
    protected fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text; isAllCaps = false; minHeight = dp(48); setOnClickListener { if (!busy) action() }
    }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2)) }
    protected fun input(title: String, value: String = "", multiline: Boolean = false, number: Boolean = false): EditText {
        val description = label(title)
        return EditText(this).apply {
            id = View.generateViewId(); description.labelFor = id
            hint = title; setText(value); minHeight = dp(48)
            inputType = if (number) InputType.TYPE_CLASS_NUMBER else if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
            if (!multiline) isSingleLine = true
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2)) }
    }
    protected fun confirm(title: String, message: String, action: () -> Unit) {
        getAlertDialogBuilder().setTitle(title).setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ -> if (!busy) action() }.show()
    }
    protected fun task(mutation: Boolean = false, block: suspend (ChannelActionsApi) -> Unit) {
        val session = api ?: run { notice.text = getString(R.string.hex_tools_reconnect); return }
        if (busy) return
        busy = true
        notice.text = if (mutation) "Submitting to Twitch…" else "Loading…"
        lifecycleScope.launch {
            try {
                session.checkCurrent(); block(session); session.checkCurrent()
                notice.text = if (mutation) "Twitch accepted the action." else "Up to date."
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { notice.text = e.message ?: "Check the entered values." }
            catch (e: ActionFailure) { notice.text = e.message }
            catch (_: Exception) { notice.text = getString(R.string.hex_tools_uncertain) }
            finally { busy = false }
        }
    }
    protected fun openTwitch(url: String) {
        // Explicit browser targets avoid HexStream's own Twitch deep-link handler.
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val browserProbe = Intent(Intent.ACTION_VIEW, "https://example.org/".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val choices = packageManager.queryIntentActivities(browserProbe, 0).filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { Intent(intent).setClassName(it.activityInfo.packageName, it.activityInfo.name) }
        if (choices.isEmpty()) { notice.text = getString(R.string.hex_tools_no_browser); return }
        startActivity(Intent.createChooser(choices.first(), "Open Twitch").putExtra(Intent.EXTRA_INITIAL_INTENTS, choices.drop(1).toTypedArray()))
    }
}
