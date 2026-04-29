package com.lst.bandtotp

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

data class TotpInfo(
    val name: String,
    val usr: String,
    val key: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("usr", usr)
        .put("key", key)
        .put("algorithm", algorithm)
        .put("digits", digits)
        .put("period", period)
}

object TotpImportParser {
    private val otpAuthRegex = Regex("""otpauth://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val base32Regex = Regex("""^[A-Z2-7]+=*$""")
    private val containerKeys = arrayOf(
        "list",
        "accounts",
        "tokens",
        "items",
        "entries",
        "otp_params",
        "otpParams",
        "otp_parameters",
        "otpParameters"
    )

    fun parse(text: String): List<TotpInfo> {
        val result = LinkedHashMap<String, TotpInfo>()
        parseJson(text).forEach { result[it.identityKey()] = it }
        parseOtpAuthLinks(text).forEach { result[it.identityKey()] = it }
        return result.values.toList()
    }

    private fun parseJson(text: String): List<TotpInfo> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || (trimmed.first() != '{' && trimmed.first() != '[')) {
            return emptyList()
        }

        return runCatching {
            val value = JSONTokener(trimmed).nextValue()
            val accounts = mutableListOf<TotpInfo>()
            collectFromJson(value, accounts)
            accounts
        }.getOrDefault(emptyList())
    }

    private fun collectFromJson(value: Any?, accounts: MutableList<TotpInfo>) {
        when (value) {
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    collectFromJson(value.opt(index), accounts)
                }
            }

            is JSONObject -> {
                parseObject(value)?.let(accounts::add)

                for (key in containerKeys) {
                    if (value.has(key) && !value.isNull(key)) {
                        collectFromJson(value.opt(key), accounts)
                    }
                }
            }
        }
    }

    private fun parseObject(json: JSONObject): TotpInfo? {
        val uriValue = json.optText("url", "uri", "otpauth", "otpAuth", "link")
        val fromUri = uriValue?.let(::parseOtpAuthUri)

        if (isHotp(json.optText("type", "otp_type", "otpType"))) {
            return null
        }

        val secret = json.extractSecret() ?: return fromUri
        val issuer = json.optText(
            "issuer",
            "issuerName",
            "provider",
            "service",
            "serviceName",
            "app"
        ) ?: fromUri?.name

        val account = json.optText(
            "usr",
            "user",
            "username",
            "account",
            "accountName",
            "account_name",
            "email",
            "login",
            "name"
        ) ?: fromUri?.usr

        val title = issuer ?: json.optText("label", "title", "name") ?: account ?: "TOTP"
        return TotpInfo(
            name = title.cleanTitle(),
            usr = (account ?: title).cleanTitle(),
            key = secret,
            algorithm = parseAlgorithm(json.optText("algorithm", "algo") ?: fromUri?.algorithm),
            digits = parseDigits(json.opt("digits") ?: json.opt("digitCount") ?: json.opt("digit_count"))
                ?: fromUri?.digits
                ?: 6,
            period = parseInt(json.opt("period") ?: json.opt("interval") ?: json.opt("timeStep") ?: json.opt("time_step"))
                ?: fromUri?.period
                ?: 30
        )
    }

    private fun parseOtpAuthLinks(text: String): List<TotpInfo> =
        otpAuthRegex.findAll(text)
            .mapNotNull { parseOtpAuthUri(it.value.trimEnd(',', ';', ']', '}')) }
            .toList()

    private fun parseOtpAuthUri(value: String): TotpInfo? {
        return runCatching {
            val uri = URI(value.trim())
            if (uri.scheme?.lowercase(Locale.ROOT) != "otpauth") {
                return@runCatching null
            }

            val type = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching null
            if (type == "hotp") {
                return@runCatching null
            }
            if (type != "totp" && type != "steam") {
                return@runCatching null
            }

            val label = uri.rawPath
                ?.trimStart('/')
                ?.substringBefore('/')
                ?.urlDecode()
                .orEmpty()
            val parts = label.split(":", limit = 2)
            val issuerFromPath = parts.getOrNull(0)?.takeIf { parts.size == 2 }?.cleanTitle()
            val account = parts.getOrNull(if (parts.size == 2) 1 else 0)
                ?.cleanTitle()
                ?.takeIf { it.isNotBlank() }

            val query = parseQuery(uri.rawQuery)
            val secret = query["secret"]?.let(::normalizeSecret) ?: return@runCatching null
            val issuer = query["issuer"]?.cleanTitle()
                ?: issuerFromPath
                ?: if (type == "steam") "Steam" else account
                ?: "TOTP"

            TotpInfo(
                name = issuer,
                usr = account ?: issuer,
                key = secret,
                algorithm = if (type == "steam") "STEAM" else parseAlgorithm(query["algorithm"]),
                digits = if (type == "steam") {
                    5
                } else {
                    query["digits"]?.toIntOrNull() ?: 6
                },
                period = query["period"]?.toIntOrNull() ?: 30
            )
        }.getOrNull()
    }

    private fun JSONObject.extractSecret(): String? {
        optText("key", "secret", "secretKey", "secret_key", "otp_secret", "otpSecret")?.let {
            normalizeSecret(it)?.let { secret -> return secret }
        }

        val bytes = extractSecretBytes("secretBytes", "secret_bytes", "secretData", "secret_data")
        return bytes?.takeIf { it.isNotEmpty() }?.let(::bytesToBase32)
    }

    private fun JSONObject.extractSecretBytes(vararg keys: String): ByteArray? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            when (val value = opt(key)) {
                is JSONArray -> {
                    return ByteArray(value.length()) { index ->
                        (value.optInt(index) and 0xff).toByte()
                    }
                }

                is String -> {
                    val decoded = decodeBase64(value)
                    if (decoded != null) return decoded
                }
            }
        }
        return null
    }

    private fun normalizeSecret(value: String): String? {
        val trimmed = value.trim().trim('"').replace(Regex("""[\s-]"""), "")
        if (trimmed.isBlank()) return null

        val upper = trimmed.uppercase(Locale.ROOT)
        if (base32Regex.matches(upper)) {
            return upper.trimEnd('=')
        }

        return decodeBase64(trimmed)?.takeIf { it.isNotEmpty() }?.let(::bytesToBase32)
    }

    private fun decodeBase64(value: String): ByteArray? {
        val compactValue = value.replace(Regex("""\s"""), "")
        return runCatching { Base64.getDecoder().decode(compactValue.withBase64Padding()) }
            .recoverCatching { Base64.getUrlDecoder().decode(compactValue.withBase64Padding()) }
            .getOrNull()
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()

        return rawQuery.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', missingDelimiterValue = "").urlDecode()
                if (key.isBlank()) return@mapNotNull null
                val value = part.substringAfter('=', missingDelimiterValue = "").urlDecode()
                key to value
            }
            .toMap()
    }

    private fun bytesToBase32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0

        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8

            while (bitsLeft >= 5) {
                result.append(alphabet[(buffer shr (bitsLeft - 5)) and 0x1f])
                bitsLeft -= 5
            }

            if (bitsLeft > 0) {
                buffer = buffer and ((1 shl bitsLeft) - 1)
            } else {
                buffer = 0
            }
        }

        if (bitsLeft > 0) {
            result.append(alphabet[(buffer shl (5 - bitsLeft)) and 0x1f])
        }

        return result.toString()
    }

    private fun parseAlgorithm(value: String?): String {
        val normalized = value.orEmpty().uppercase(Locale.ROOT)
        return when {
            "STEAM" in normalized -> "STEAM"
            "SHA512" in normalized -> "SHA512"
            "SHA256" in normalized -> "SHA256"
            else -> "SHA1"
        }
    }

    private fun parseDigits(value: Any?): Int? {
        if (value is String) {
            val normalized = value.uppercase(Locale.ROOT)
            return when {
                "EIGHT" in normalized -> 8
                "SIX" in normalized -> 6
                else -> normalized.filter(Char::isDigit).toIntOrNull()
            }
        }
        return parseInt(value)
    }

    private fun parseInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.filter { it.isDigit() }.toIntOrNull()
        else -> null
    }

    private fun isHotp(value: String?): Boolean =
        value?.uppercase(Locale.ROOT)?.contains("HOTP") == true

    private fun JSONObject.optText(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            when (val value = opt(key)) {
                is String -> value.trim().takeIf { it.isNotBlank() }?.let { return it }
                is Number, is Boolean -> return value.toString()
            }
        }
        return null
    }

    private fun TotpInfo.identityKey(): String =
        "${name.lowercase(Locale.ROOT)}|${usr.lowercase(Locale.ROOT)}|$key"

    private fun String.cleanTitle(): String =
        trim().trim('/').takeIf { it.isNotBlank() } ?: "TOTP"

    private fun String.urlDecode(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())

    private fun String.withBase64Padding(): String =
        this + "=".repeat((4 - length % 4) % 4)
}
