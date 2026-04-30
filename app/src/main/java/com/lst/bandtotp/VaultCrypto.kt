package com.lst.bandtotp

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

object VaultCrypto {
    private const val VAULT_VERSION = 5
    private const val KDF_NAME = "PIN-MIX-XOR-32"
    private const val VAULT_INFO = "BandTOTP vault v5"
    private const val PLAIN_PREFIX = "BTOTP5|"
    private val random = SecureRandom()

    fun buildPayload(accounts: List<TotpInfo>, pin: String): JSONObject {
        val salt = b64(randomBytes(16))
        val plain = "$PLAIN_PREFIX${
            JSONObject().put("list", JSONArray(accounts.map { it.toJson() }))
        }".toByteArray(Charsets.UTF_8)
        val vault = JSONObject()
            .put("version", VAULT_VERSION)
            .put("kdf", KDF_NAME)
            .put("salt", salt)
            .put("ciphertext", b64(cryptBytes(plain, pin, salt)))

        return JSONObject()
            .put("vault", vault)
            .put("meta", JSONArray(accounts.map { JSONObject().put("name", it.name).put("usr", it.usr) }))
    }

    internal fun decryptPayloadForTest(vault: JSONObject, pin: String): JSONObject {
        val salt = vault.getString("salt")
        val decrypted = cryptBytes(Base64.getDecoder().decode(vault.getString("ciphertext")), pin, salt)
            .toString(Charsets.UTF_8)

        require(decrypted.startsWith(PLAIN_PREFIX)) { "Wrong PIN" }
        return JSONObject(decrypted.removePrefix(PLAIN_PREFIX))
    }

    private fun deriveState(pin: String, salt: String): IntArray {
        val text = "$pin|$salt|$VAULT_INFO"
        val state = intArrayOf(0x13579bdf, 0x2468ace0, -0x0e1d2c3c, -0x76543211)

        repeat(32) { round ->
            text.forEachIndexed { index, char ->
                val target = (round + index) and 3
                state[target] = mixValue(state[target], char.code + round + index)
                state[(target + 1) and 3] = state[(target + 1) and 3] xor state[target]
            }
        }

        return state
    }

    private fun cryptBytes(bytes: ByteArray, pin: String, salt: String): ByteArray {
        val state = deriveState(pin, salt)
        val result = ByteArray(bytes.size)

        for (index in bytes.indices) {
            val target = index and 3
            var value = state[target]
            value += 0x9e3779b9.toInt() + index
            value = value xor (value shl 13)
            value = value xor (value ushr 17)
            value = value xor (value shl 5)
            state[target] = value
            result[index] = (bytes[index].toInt() xor ((value ushr ((index and 3) * 8)) and 0xff)).toByte()
        }

        return result
    }

    private fun mixValue(input: Int, charCode: Int): Int {
        var value = input
        value += charCode + (value shl 5)
        value = value xor (value ushr 7) xor (value shl 11)
        return value
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
