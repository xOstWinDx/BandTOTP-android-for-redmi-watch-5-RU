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

        assertEquals(5, vault.getInt("version"))
        assertEquals("PIN-MIX-XOR-32", vault.getString("kdf"))
        assertTrue(vault.getString("salt").isNotBlank())
        assertTrue(vault.getString("ciphertext").isNotBlank())
        assertEquals("GitHub", payload.getJSONArray("meta").getJSONObject(0).getString("name"))
        assertFalse(text.contains("JBSWY3DPEHPK3PXP"))

        val decrypted = VaultCrypto.decryptPayloadForTest(vault, "1234")
        val account = decrypted.getJSONArray("list").getJSONObject(0)
        assertEquals("JBSWY3DPEHPK3PXP", account.getString("key"))
    }
}
