package com.lst.bandtotp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpImportParserTest {
    @Test
    fun parsesOtpAuthLinks() {
        val accounts = TotpImportParser.parse(
            "otpauth://totp/GitHub:user%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&period=30"
        )

        assertEquals(1, accounts.size)
        assertEquals("GitHub", accounts[0].name)
        assertEquals("user@example.com", accounts[0].usr)
        assertEquals("JBSWY3DPEHPK3PXP", accounts[0].key)
        assertEquals("SHA1", accounts[0].algorithm)
        assertEquals(6, accounts[0].digits)
        assertEquals(30, accounts[0].period)
    }

    @Test
    fun parsesJsonListObjects() {
        val accounts = TotpImportParser.parse(
            """
            [
              {
                "issuer": "GitLab",
                "account": "dev@example.com",
                "secret": "jbsw y3dp-ehpk3pxp",
                "algorithm": "SHA256",
                "digits": 8,
                "period": 45
              }
            ]
            """.trimIndent()
        )

        assertEquals(1, accounts.size)
        assertEquals("GitLab", accounts[0].name)
        assertEquals("dev@example.com", accounts[0].usr)
        assertEquals("JBSWY3DPEHPK3PXP", accounts[0].key)
        assertEquals("SHA256", accounts[0].algorithm)
        assertEquals(8, accounts[0].digits)
        assertEquals(45, accounts[0].period)
    }

    @Test
    fun parsesDecodedGoogleAuthenticatorExport() {
        val accounts = TotpImportParser.parse(
            """
            {
              "otp_params": [
                {
                  "issuer": "Example",
                  "name": "person@example.com",
                  "secret": "SGVsbG8h",
                  "algorithm": "ALGORITHM_SHA1",
                  "digits": "DIGIT_COUNT_EIGHT",
                  "type": "OTP_TYPE_TOTP"
                },
                {
                  "issuer": "Skipped",
                  "name": "counter",
                  "secret": "SGVsbG8h",
                  "type": "OTP_TYPE_HOTP"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, accounts.size)
        assertEquals("Example", accounts[0].name)
        assertEquals("person@example.com", accounts[0].usr)
        assertEquals("JBSWY3DPEE", accounts[0].key)
        assertEquals(8, accounts[0].digits)
    }

    @Test
    fun returnsEmptyListForUnsupportedFiles() {
        assertTrue(TotpImportParser.parse("hello world").isEmpty())
    }
}
