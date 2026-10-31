package fr.abls.habitat.home.mqtt

import fr.abls.habitat.home.session.Session
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class MqttMessage(val tag: String, val target: String?, val payload: String)

enum class MqttState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Équivalent de Load_mqtt() / Mqtt_subscribe() dans common_home.js :
 * connexion ws(s)://host:port, user "<domain>-browser", topics "<domain>/browsers/<tag>/...".
 */
class MqttManager(private val http: HttpClient, private val session: Session, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(MqttState.DISCONNECTED)
    val state: StateFlow<MqttState> = _state
    private val _messages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 256)
    val messages: SharedFlow<MqttMessage> = _messages

    private var ws: DefaultWebSocketSession? = null
    private var job: Job? = null
    private val sendMutex = Mutex()
    private var packetId = 1
    private val subscriptions = LinkedHashSet<String>()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel(); job = null
        val s = ws; ws = null
        scope.launch { try { s?.close() } catch (_: Exception) {} }
        _state.value = MqttState.DISCONNECTED
    }

    fun subscribe(topic: String) {
        val full = fullTopic(topic) ?: return
        subscriptions.add(full)
        if (_state.value == MqttState.CONNECTED) scope.launch { send(MqttCodec.subscribe(nextId(), listOf(full))) }
    }

    fun unsubscribe(topic: String) {
        val full = fullTopic(topic) ?: return
        subscriptions.remove(full)
        if (_state.value == MqttState.CONNECTED) scope.launch { send(MqttCodec.unsubscribe(nextId(), listOf(full))) }
    }

    private fun fullTopic(topic: String): String? = session.domainUuid?.let { "$it/browsers/$topic" }

    private fun nextId(): Int { packetId = (packetId % 65535) + 1; return packetId }

    private suspend fun send(bytes: ByteArray) {
        val s = ws ?: return
        sendMutex.withLock { try { s.send(Frame.Binary(true, bytes)) } catch (_: Exception) {} }
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            val domain = session.domainUuid
            val host = session.mqttHostname
            if (domain == null || host.isNullOrBlank()) { delay(2000); continue }
            val url = (if (session.mqttOverSsl) "wss" else "ws") + "://$host:${session.mqttPort}"
            _state.value = MqttState.CONNECTING
            try {
                val s = http.webSocketSession(url) { header("Sec-WebSocket-Protocol", "mqtt") }
                ws = s
                s.send(Frame.Binary(true, MqttCodec.connect("$domain-browser-${(1..999999).random()}", "$domain-browser", session.browserPassword, 30)))
                var buffer = ByteArray(0)
                var connected = false
                val pinger = scope.launch {
                    while (isActive) { delay(15_000); send(MqttCodec.pingreq()) }
                }
                try {
                    while (true) {
                        val frame = withTimeoutOrNull(60_000) { s.incoming.receive() } ?: break
                        if (frame !is Frame.Binary && frame !is Frame.Text) continue
                        buffer += frame.readBytes()
                        val (packets, rest) = MqttCodec.split(buffer)
                        buffer = rest
                        for (p in packets) when (p.type) {
                            MqttCodec.CONNACK -> {
                                if (p.payload.size >= 2 && p.payload[1].toInt() == 0) {
                                    connected = true
                                    _state.value = MqttState.CONNECTED
                                    if (subscriptions.isNotEmpty()) send(MqttCodec.subscribe(nextId(), subscriptions.toList()))
                                } else throw IllegalStateException("CONNACK refused: ${p.payload.getOrNull(1)}")
                            }
                            MqttCodec.PUBLISH -> {
                                val pub = MqttCodec.decodePublish(p)
                                pub.packetId?.let { send(MqttCodec.puback(it)) }
                                dispatch(domain, pub)
                            }
                            else -> {}
                        }
                    }
                } finally {
                    pinger.cancel()
                }
                if (!connected) throw IllegalStateException("MQTT: no CONNACK")
            } catch (e: Exception) {
                /* reconnectPeriod 10 s comme dans mqtt.js */
            } finally {
                try { ws?.close() } catch (_: Exception) {}
                ws = null
                _state.value = MqttState.DISCONNECTED
            }
            delay(10_000)
        }
    }

    private fun dispatch(domain: String, pub: MqttCodec.Publish) {
        val parts = pub.topic.split("/")
        if (parts.size < 3 || parts[0] != domain || parts[1] != "browsers") return
        _messages.tryEmit(MqttMessage(parts[2], parts.getOrNull(3), pub.payload.decodeToString()))
    }
}
