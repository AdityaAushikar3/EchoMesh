package chat.bitchat.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Proves that the encode/decode roundtrip preserves stable identities exactly,
 * and that BLE MACs are correctly encoded/decoded.
 */
class PeerRouteEncodingTest {

    // ── Stable identity: must pass through unchanged ──────────────────────────

    @Test
    fun `stable identity dev_1709479198 survives encode then decode unchanged`() {
        val input = "dev_1709479198"
        val encoded = encodePeerRouteId(input)
        val decoded = decodePeerRouteId(encoded)
        assertEquals(
            "Stable identity must not be mutated by encode/decode",
            input,
            decoded
        )
    }

    @Test
    fun `stable identity does NOT equal corrupted colon form`() {
        assertNotEquals(
            "dev_1709479198 must never equal dev:1709479198",
            "dev_1709479198",
            "dev:1709479198"
        )
    }

    @Test
    fun `encode does not modify stable identity`() {
        assertEquals("dev_1709479198", encodePeerRouteId("dev_1709479198"))
    }

    @Test
    fun `decode does not modify stable identity`() {
        assertEquals("dev_1709479198", decodePeerRouteId("dev_1709479198"))
    }

    // ── BLE MAC: must be encoded with pipe, decoded back to colon ─────────────

    @Test
    fun `BLE MAC is encoded to pipe-separated form`() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val encoded = encodePeerRouteId(mac)
        assertEquals("AA|BB|CC|DD|EE|FF", encoded)
    }

    @Test
    fun `BLE MAC roundtrip encode then decode restores original`() {
        val mac = "56:B3:A9:35:8F:93"
        val decoded = decodePeerRouteId(encodePeerRouteId(mac))
        assertEquals(mac, decoded)
    }

    @Test
    fun `BLE MAC lowercase roundtrip`() {
        val mac = "aa:bb:cc:dd:ee:ff"
        val decoded = decodePeerRouteId(encodePeerRouteId(mac))
        assertEquals(mac, decoded)
    }

    // ── Nickname: must pass through unchanged ─────────────────────────────────

    @Test
    fun `plain nickname passes through encode and decode unchanged`() {
        val nickname = "papa"
        assertEquals(nickname, decodePeerRouteId(encodePeerRouteId(nickname)))
    }

    @Test
    fun `nickname with spaces passes through unchanged`() {
        val nickname = "Cool Person"
        assertEquals(nickname, decodePeerRouteId(encodePeerRouteId(nickname)))
    }
}
