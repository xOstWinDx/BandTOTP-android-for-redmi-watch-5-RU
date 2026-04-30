package com.lst.bandtotp

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {
    private const val VAULT_VERSION = 1
    private const val KDF_NAME = "PBKDF2-SHA256"
    private const val ITERATIONS = 1_800
    private const val DERIVED_BITS = 512
    private val random = SecureRandom()

    fun buildPayload(accounts: List<TotpInfo>, pin: String): JSONObject {
        val salt = randomBytes(16)
        val iv = randomBytes(16)
        val keys = deriveKeys(pin, salt)
        val plain = JSONObject()
            .put("list", JSONArray(accounts.map { it.toJson() }))
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.encKey, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plain)

        val saltText = b64(salt)
        val ivText = b64(iv)
        val cipherText = b64(ciphertext)
        val vault = JSONObject()
            .put("version", VAULT_VERSION)
            .put("kdf", KDF_NAME)
            .put("iterations", ITERATIONS)
            .put("salt", saltText)
            .put("iv", ivText)
            .put("ciphertext", cipherText)

        val macText = macHex(
            "${VAULT_VERSION}|$KDF_NAME|$ITERATIONS|$saltText|$ivText|$cipherText",
            keys.macKey
        )
        vault.put("mac", macText)

        return JSONObject()
            .put("vault", vault)
            .put("meta", JSONArray(accounts.map { JSONObject().put("name", it.name).put("usr", it.usr) }))
    }

    private fun deriveKeys(pin: String, salt: ByteArray): VaultKeys {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, DERIVED_BITS)
        val material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded

        return VaultKeys(
            encKey = material.copyOfRange(0, 32),
            macKey = material.copyOfRange(32, 64)
        )
    }

    private fun macHex(text: String, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private data class VaultKeys(
        val encKey: ByteArray,
        val macKey: ByteArray
    )
}
