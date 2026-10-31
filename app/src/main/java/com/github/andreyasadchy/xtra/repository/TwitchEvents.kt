package com.github.andreyasadchy.xtra.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException

/** A bounded foreground EventSub connection, with keepalive recovery and overlapping handoff. */
object TwitchEvents {
    private data class Frame(val socket: WebSocket, val text: String?)
    suspend fun run(api: ChannelActionsApi, subscriptions: Map<String, JsonObject>, status: (String) -> Unit, event: suspend (String, JsonObject) -> Unit) {
        var backoff = 1_000L
        val seen = LinkedHashSet<String>()
        while (currentCoroutineContext().isActive) {
            api.checkCurrent()
            val messages = Channel<Frame>(64)
            fun connect(url: String) = api.client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!messages.trySend(Frame(webSocket, text)).isSuccess) { messages.close(IOException()); webSocket.cancel() }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { messages.trySend(Frame(webSocket, null)) }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); messages.trySend(Frame(webSocket, null)) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { messages.trySend(Frame(webSocket, null)) }
            })
            var socket = connect("wss://eventsub.wss.twitch.tv/ws")
            var pending: WebSocket? = null
            var timeout = 15_000L
            try {
                while (currentCoroutineContext().isActive) {
                    val frame = withTimeout(timeout) { messages.receive() }
                    api.checkCurrent()
                    if (frame.text == null) {
                        if (frame.socket === socket || frame.socket === pending) throw IOException()
                        continue
                    }
                    if (frame.socket !== socket && frame.socket !== pending) continue
                    val root = Json.parseToJsonElement(frame.text).jsonObject
                    val metadata = root["metadata"]?.jsonObject ?: continue
                    val payload = root["payload"]?.jsonObject ?: continue
                    when (metadata.text("message_type")) {
                        "session_welcome" -> {
                            val session = payload["session"]!!.jsonObject
                            timeout = ((session["keepalive_timeout_seconds"]?.jsonPrimitive?.longOrNull ?: 10L).coerceIn(5L, 600L) + 5L) * 1_000L
                            if (frame.socket === pending) {
                                val old = socket; socket = frame.socket; pending = null; old.close(1000, null)
                            } else subscriptions.forEach { (type, condition) -> api.subscribe(type, condition, session.text("id")) }
                            backoff = 1_000L
                            status("Connected • new messages arrive while HexStream is open")
                        }
                        "session_reconnect" -> {
                            if (pending == null) {
                                val next = payload["session"]?.jsonObject?.text("reconnect_url").orEmpty()
                                if (!validReconnectUrl(next)) throw IOException()
                                pending = connect(next)
                            }
                        }
                        "revocation" -> throw ActionFailure("Twitch revoked this connection. Reconnect your account using Twitch API login.")
                        "notification" -> {
                            val id = metadata.text("message_id")
                            if (id.isNotBlank() && seen.add(id)) {
                                while (seen.size > 500) seen.remove(seen.first())
                                event(metadata.text("subscription_type"), payload["event"]!!.jsonObject)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                status("Connection timed out. Reconnecting…")
            } catch (e: ActionFailure) { status(e.message.orEmpty()); return }
            catch (_: Exception) { status("Reconnecting… messages sent during a connection gap cannot be recovered from Twitch.") }
            finally { socket.cancel(); pending?.cancel(); messages.cancel() }
            delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }
    internal fun validReconnectUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "wss" && uri.host == "eventsub.wss.twitch.tv" && uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)
}
