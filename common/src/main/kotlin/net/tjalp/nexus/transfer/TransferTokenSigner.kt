package net.tjalp.nexus.transfer

import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs and verifies [TransferToken] values using HMAC-SHA256.
 *
 * Cookie value format (UTF-8 bytes):
 * ```
 * base64url(jsonPayload) + '.' + base64url(hmacSha256(secret, base64url(jsonPayload)))
 * ```
 *
 * @param secret The shared HMAC secret. Must be the same on every server in the network.
 */
class TransferTokenSigner(secret: String) {

    private val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM)
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /**
     * Encodes a [TransferToken] into cookie bytes ready to be stored via [Player.storeCookie].
     */
    fun encode(token: TransferToken): ByteArray {
        val json = Json.encodeToString(TransferToken.serializer(), token)
        val encodedPayload = encoder.encodeToString(json.toByteArray(Charsets.UTF_8))
        val sig = hmac(encodedPayload)
        return "$encodedPayload.$sig".toByteArray(Charsets.UTF_8)
    }

    /**
     * Decodes and verifies cookie bytes back to a [TransferToken], or returns `null` if the
     * bytes are missing, malformed, the signature is invalid, or the token has expired.
     *
     * @param cookieBytes The raw bytes retrieved from the player's cookie storage.
     * @param expectedPlayerId The player UUID string that must match [TransferToken.playerId].
     * @param expectedToServerId The server ID that must match [TransferToken.toServerId].
     * @param nowMillis Current epoch millis used for expiry check (defaults to system clock).
     */
    fun decode(
        cookieBytes: ByteArray?,
        expectedPlayerId: String,
        expectedToServerId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): TransferToken? {
        if (cookieBytes == null || cookieBytes.isEmpty()) return null
        val raw = cookieBytes.toString(Charsets.UTF_8)
        val dotIndex = raw.lastIndexOf('.')
        if (dotIndex < 0) return null

        val encodedPayload = raw.substring(0, dotIndex)
        val providedSig = raw.substring(dotIndex + 1)

        // Constant-time signature comparison to prevent timing attacks
        val expectedSig = hmac(encodedPayload)
        if (!constantTimeEquals(expectedSig, providedSig)) return null

        val token = try {
            val payloadJson = decoder.decode(encodedPayload).toString(Charsets.UTF_8)
            Json.decodeFromString(TransferToken.serializer(), payloadJson)
        } catch (_: Exception) {
            return null
        }

        if (token.playerId != expectedPlayerId) return null
        if (token.toServerId != expectedToServerId) return null
        if (nowMillis > token.expiresAtMillis) return null

        return token
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hmac(data: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(secretKey)
        return encoder.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"

        /** Default secret used when none is configured. Servers will log a warning at startup. */
        const val DEFAULT_SECRET = "change-me-in-config"
    }
}
