package com.lst.bandtotp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCryptoTest {
    @Test
    fun buildsEncryptedVaultPayloadWithoutPlainSecrets() {
        val payload = VaultCrypto.buildPayload(
            listOf(
                TotpInfo(
                    name = "GitHub",
                    usr = "user@example.com",
                    key = "JBSWY3DPEHPK3PXP"
                )
            ),
            pin = "1234"
        )

        val vault = payload.getJSONObject("vault")
        val text = payload.toString()

        assertEquals(1, vault.getInt("version"))
        assertEquals("PBKDF2-SHA256", vault.getString("kdf"))
        assertTrue(vault.getInt("iterations") > 0)
        assertTrue(vault.getString("salt").isNotBlank())
        assertTrue(vault.getString("iv").isNotBlank())
        assertTrue(vault.getString("ciphertext").isNotBlank())
        assertEquals(64, vault.getString("mac").length)
        assertEquals("GitHub", payload.getJSONArray("meta").getJSONObject(0).getString("name"))
        assertFalse(text.contains("JBSWY3DPEHPK3PXP"))
    }
}
