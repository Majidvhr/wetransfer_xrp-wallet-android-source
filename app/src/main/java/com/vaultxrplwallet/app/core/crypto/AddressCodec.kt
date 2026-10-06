package com.vaultxrplwallet.app.core.crypto

data class XrplXAddress(
    val classicAddress: String,
    val destinationTag: Long?,
    val isTestNetwork: Boolean,
)

object AddressCodec {
    fun classicAddress(publicKey: ByteArray): String {
        require(publicKey.size == 33)
        val accountId = Bytes.ripemd160(Bytes.sha256(publicKey))
        return encodeClassicAddress(accountId)
    }

    fun encodeClassicAddress(accountId: ByteArray): String {
        require(accountId.size == 20)
        return Base58Xrp.encodeChecked(byteArrayOf(0x00), accountId)
    }

    fun accountId(classicAddress: String): ByteArray? {
        val decoded = Base58Xrp.decode(classicAddress) ?: return null
        if (decoded.size != 25) return null
        val payload = decoded.copyOf(21)
        val checksum = decoded.copyOfRange(21, 25)
        if (payload[0] != 0.toByte()) return null
        if (!checksum.contentEquals(Bytes.sha256d(payload).copyOf(4))) return null
        return payload.copyOfRange(1, 21)
    }

    fun isValidClassicAddress(address: String): Boolean = accountId(address) != null

    fun decodeXAddress(address: String): XrplXAddress? {
        val decoded = Base58Xrp.decode(address) ?: return null
        if (decoded.size != 35) return null
        val body = decoded.copyOf(31)
        val checksum = decoded.copyOfRange(31, 35)
        if (!checksum.contentEquals(Bytes.sha256d(body).copyOf(4))) return null
        val isTest = when {
            body[0] == 0x05.toByte() && body[1] == 0x44.toByte() -> false
            body[0] == 0x04.toByte() && body[1] == 0x93.toByte() -> true
            else -> return null
        }
        val payload = body.copyOfRange(2, 31)
        if (payload.size != 29 || payload.copyOfRange(25, 29).any { it != 0.toByte() }) return null
        val account = payload.copyOf(20)
        val tagFlag = payload[20].toInt() and 0xFF
        val tagBytes = payload.copyOfRange(21, 25)
        val tag = when (tagFlag) {
            0 -> {
                if (tagBytes.any { it != 0.toByte() }) return null
                null
            }
            1 -> {
                var v = 0L
                tagBytes.forEachIndexed { i, b -> v = v or ((b.toLong() and 0xFF) shl (i * 8)) }
                v
            }
            else -> return null
        }
        return XrplXAddress(encodeClassicAddress(account), tag, isTest)
    }

    fun parseDestination(input: String): Pair<String, Long?>? {
        val trimmed = input.trim()
        if (trimmed.startsWith("X") || trimmed.startsWith("T")) {
            val x = decodeXAddress(trimmed) ?: return null
            if (x.isTestNetwork) return null
            return x.classicAddress to x.destinationTag
        }
        return if (isValidClassicAddress(trimmed)) trimmed to null else null
    }

    fun parsePaymentTarget(input: String): Pair<String, Long?>? {
        var value = input.trim()
        if (value.startsWith("vaultxrp://", ignoreCase = true)) {
            value = value.substringAfter("://")
        }
        value = value.removePrefix("xrpl:").removePrefix("ripple:")
        val queryIndex = value.indexOf('?')
        val addressPart = if (queryIndex >= 0) value.substring(0, queryIndex) else value
        val query = if (queryIndex >= 0) value.substring(queryIndex + 1) else ""
        val tag = Regex("(?:^|&)(?:dt|tag)=(\\d+)", RegexOption.IGNORE_CASE)
            .find(query)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
        val parsed = parseDestination(addressPart) ?: return null
        return parsed.first to (parsed.second ?: tag)
    }
}
