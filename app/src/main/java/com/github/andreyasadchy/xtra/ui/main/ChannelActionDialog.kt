package com.github.andreyasadchy.xtra.ui.main

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.viewModels
import androidx.appcompat.app.AlertDialog
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.ActionFailure
import com.github.andreyasadchy.xtra.repository.ChannelActionsApi
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.ChannelActionSession
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Keep channel tools over the existing player. In-flight writes survive view recreation. */
abstract class ChannelActionDialog : BottomSheetDialogFragment() {
    protected lateinit var body: LinearLayout
    protected lateinit var notice: TextView
    protected var api: ChannelActionsApi? = null
    private val requests: ChannelDialogRequests by viewModels()
    private val alerts = mutableSetOf<AlertDialog>()
    private var ownerAtOpen: String? = null
    private var headersAtOpen: Map<String, String> = emptyMap()
    protected val busy get() = requests.state.value.busy
    protected suspend fun awaitIdle() { requests.state.first { !it.busy } }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Button(context).apply {
            setText(R.string.hex_mod_back_channel)
            isAllCaps = false
            setOnClickListener { dismiss() }
        })
        root.addView(TextView(context).apply {
            text = getString(R.string.hex_tools_account, context.tokenPrefs().getString(C.USERNAME, "Twitch"))
            setPadding(dp(20), dp(4), dp(20), dp(4))
        })
        notice = TextView(context).apply {
            setPadding(dp(20), dp(4), dp(20), dp(4))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(notice)
        body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        root.addView(ScrollView(context).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.layoutParams = ViewGroup.LayoutParams(-1, (resources.displayMetrics.heightPixels * .8f).toInt())
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ownerAtOpen = requireContext().tokenPrefs().getString(C.USER_ID, null)
        headersAtOpen = TwitchApiHelper.getHelixHeaders(requireContext()).toMap()
        try {
            api = ChannelActionSession.open(requireContext())
        } catch (error: ActionFailure) {
            requests.state.value = ChannelDialogState(message = error.message.orEmpty())
        }
        viewLifecycleOwner.lifecycleScope.launch {
            requests.state.collect { notice.text = it.message }
        }
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        if (context.tokenPrefs().getString(C.USER_ID, null) != ownerAtOpen || TwitchApiHelper.getHelixHeaders(context) != headersAtOpen) {
            dismissAllowingStateLoss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            BottomSheetBehavior.from(it).apply { skipCollapsed = true; state = BottomSheetBehavior.STATE_EXPANDED }
        }
    }

    protected fun clear(title: String) { body.removeAllViews(); label(title, 23f) }
    protected fun label(text: String, size: Float = 15f): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = size
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setTextIsSelectable(true)
    }.also { body.addView(it) }
    protected fun button(text: String, action: () -> Unit): Button = Button(requireContext()).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { if (!busy) action() }
    }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2)) }
    protected fun input(title: String, value: String = "", multiline: Boolean = false, number: Boolean = false): EditText {
        val description = label(title)
        return EditText(requireContext()).apply {
            id = View.generateViewId()
            description.labelFor = id
            hint = title
            setText(value)
            minHeight = dp(48)
            inputType = if (number) InputType.TYPE_CLASS_NUMBER else if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
            if (!multiline) isSingleLine = true
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2)) }
    }
    protected fun confirm(title: String, message: String, positive: String = "Confirm", action: () -> Unit) {
        val alert = requireContext().getAlertDialogBuilder().setTitle(title).setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positive) { _, _ -> if (!busy) action() }.create()
        showAlert(alert)
    }
    protected fun showAlert(alert: AlertDialog) {
        if (view == null || !isAdded) return
        alerts.add(alert)
        alert.setOnDismissListener { alerts.remove(alert) }
        alert.show()
    }
    override fun onDestroyView() {
        alerts.toList().forEach { it.dismiss() }
        alerts.clear()
        super.onDestroyView()
    }
    protected fun task(mutation: Boolean = false, block: suspend (ChannelActionsApi) -> Unit) {
        val session = api ?: run { notice.text = getString(R.string.hex_tools_reconnect); return }
        requests.execute(session, mutation, block)
    }
}
