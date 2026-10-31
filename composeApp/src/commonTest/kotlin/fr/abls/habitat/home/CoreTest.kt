package fr.abls.habitat.home

import fr.abls.habitat.home.auth.Pkce
import fr.abls.habitat.home.mqtt.MqttCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreTest {
    @Test
    fun pkceChallengeMatchesRfc7636AppendixB() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun sha256KnownVectors() {
        fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(Pkce.sha256(ByteArray(0))))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(Pkce.sha256("abc".encodeToByteArray())))
    }

    @Test
    fun mqttSplitAndDecodePublish() {
        val topic = "dom/browsers/DLS_VISUEL/page1"
        val payload = """{"a":1}"""
        val t = topic.encodeToByteArray()
        val body = byteArrayOf((t.size shr 8).toByte(), t.size.toByte()) + t + payload.encodeToByteArray()
        val publish = byteArrayOf(0x30.toByte(), body.size.toByte()) + body

        val (packets, rest) = MqttCodec.split(publish + MqttCodec.pingreq() + byteArrayOf(0x30))
        assertEquals(2, packets.size)
        assertEquals(1, rest.size)
        val pub = MqttCodec.decodePublish(packets[0])
        assertEquals(topic, pub.topic)
        assertEquals(payload, pub.payload.decodeToString())
        assertEquals(0x10, MqttCodec.connect("id", "u", "p", 15)[0].toInt() and 0xFF)
    }
}
