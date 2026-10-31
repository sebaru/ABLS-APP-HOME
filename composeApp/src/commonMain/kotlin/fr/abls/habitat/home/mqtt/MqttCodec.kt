package fr.abls.habitat.home.mqtt

import io.ktor.utils.io.core.toByteArray

/** Encodage/décodage minimal des paquets MQTT 3.1.1 nécessaires au client (CONNECT, SUBSCRIBE, UNSUBSCRIBE, PINGREQ, DISCONNECT, PUBLISH entrant). */
object MqttCodec {
    const val CONNECT = 1; const val CONNACK = 2; const val PUBLISH = 3; const val PUBACK = 4
    const val SUBSCRIBE = 8; const val SUBACK = 9; const val UNSUBSCRIBE = 10; const val UNSUBACK = 11
    const val PINGREQ = 12; const val PINGRESP = 13; const val DISCONNECT = 14

    class Packet(val type: Int, val flags: Int, val payload: ByteArray)

    private fun encodeString(s: String): ByteArray {
        val b = s.toByteArray()
        return byteArrayOf((b.size shr 8).toByte(), b.size.toByte()) + b
    }

    private fun encodeRemainingLength(len: Int): ByteArray {
        var x = len
        val out = ArrayList<Byte>()
        do {
            var d = x % 128
            x /= 128
            if (x > 0) d = d or 0x80
            out.add(d.toByte())
        } while (x > 0)
        return out.toByteArray()
    }

    private fun frame(type: Int, flags: Int, body: ByteArray): ByteArray =
        byteArrayOf(((type shl 4) or flags).toByte()) + encodeRemainingLength(body.size) + body

    fun connect(clientId: String, username: String?, password: String?, keepAliveSec: Int, cleanSession: Boolean = true): ByteArray {
        var connectFlags = 0
        if (cleanSession) connectFlags = connectFlags or 0x02
        if (username != null) connectFlags = connectFlags or 0x80
        if (password != null) connectFlags = connectFlags or 0x40
        var body = encodeString("MQTT") + byteArrayOf(4, connectFlags.toByte(), (keepAliveSec shr 8).toByte(), keepAliveSec.toByte())
        body += encodeString(clientId)
        if (username != null) body += encodeString(username)
        if (password != null) body += encodeString(password)
        return frame(CONNECT, 0, body)
    }

    fun subscribe(packetId: Int, topics: List<String>, qos: Int = 0): ByteArray {
        var body = byteArrayOf((packetId shr 8).toByte(), packetId.toByte())
        topics.forEach { body += encodeString(it) + byteArrayOf(qos.toByte()) }
        return frame(SUBSCRIBE, 0x02, body)
    }

    fun unsubscribe(packetId: Int, topics: List<String>): ByteArray {
        var body = byteArrayOf((packetId shr 8).toByte(), packetId.toByte())
        topics.forEach { body += encodeString(it) }
        return frame(UNSUBSCRIBE, 0x02, body)
    }

    fun pingreq(): ByteArray = frame(PINGREQ, 0, ByteArray(0))
    fun disconnect(): ByteArray = frame(DISCONNECT, 0, ByteArray(0))
    fun puback(packetId: Int): ByteArray = frame(PUBACK, 0, byteArrayOf((packetId shr 8).toByte(), packetId.toByte()))

    /** Découpe un buffer en paquets complets ; renvoie les paquets et le reliquat. */
    fun split(buffer: ByteArray): Pair<List<Packet>, ByteArray> {
        val packets = ArrayList<Packet>()
        var pos = 0
        while (pos < buffer.size) {
            val header = buffer[pos].toInt() and 0xff
            var mult = 1; var len = 0; var i = pos + 1
            var complete = false
            while (i < buffer.size) {
                val d = buffer[i].toInt() and 0xff
                len += (d and 0x7f) * mult
                mult *= 128
                i++
                if (d and 0x80 == 0) { complete = true; break }
                if (mult > 128 * 128 * 128) break
            }
            if (!complete || i + len > buffer.size) break
            packets.add(Packet(header shr 4, header and 0x0f, buffer.copyOfRange(i, i + len)))
            pos = i + len
        }
        return packets to buffer.copyOfRange(pos, buffer.size)
    }

    class Publish(val topic: String, val payload: ByteArray, val qos: Int, val packetId: Int?)

    fun decodePublish(p: Packet): Publish {
        val qos = (p.flags shr 1) and 0x03
        val topicLen = ((p.payload[0].toInt() and 0xff) shl 8) or (p.payload[1].toInt() and 0xff)
        val topic = p.payload.copyOfRange(2, 2 + topicLen).decodeToString()
        var pos = 2 + topicLen
        var packetId: Int? = null
        if (qos > 0) {
            packetId = ((p.payload[pos].toInt() and 0xff) shl 8) or (p.payload[pos + 1].toInt() and 0xff)
            pos += 2
        }
        return Publish(topic, p.payload.copyOfRange(pos, p.payload.size), qos, packetId)
    }
}
