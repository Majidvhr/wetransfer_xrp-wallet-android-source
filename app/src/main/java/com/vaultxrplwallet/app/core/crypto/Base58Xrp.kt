package com.vaultxrplwallet.app.core.crypto

object Base58Xrp {
    const val ALPHABET = "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz"
    private val indexes: IntArray = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { i, c -> table[c.code] = i }
    }

    fun encode(data: ByteArray): String {
        val digits = ByteArray(data.size * 138 / 100 + 1)
        var length = 0
        for (byte in data) {
            var carry = byte.toInt() and 0xFF
            var index = 0
            var position = digits.lastIndex
            while (carry != 0 || index < length) {
                if (position < 0) break
                carry += 256 * (digits[position].toInt() and 0xFF)
                digits[position] = (carry % 58).toByte()
                carry /= 58
                index += 1
                position -= 1
            }
            length = index
        }
        val zeroCount = data.takeWhile { it == 0.toByte() }.size
        val significant = digits.takeLast(length).joinToString("") { ALPHABET[it.toInt() and 0xFF].toString() }
        return ALPHABET[0].toString().repeat(zeroCount) + significant
    }

    fun decode(string: String): ByteArray? {
        if (string.isEmpty()) return null
        val bytes = ByteArray(string.length)
        var length = 0
        for (character in string) {
            val value = if (character.code < 128) indexes[character.code] else -1
            if (value < 0) return null
            var carry = value
            var index = 0
            var position = bytes.lastIndex
            while (carry != 0 || index < length) {
                if (position < 0) return null
                carry += 58 * (bytes[position].toInt() and 0xFF)
                bytes[position] = (carry % 256).toByte()
                carry /= 256
                index += 1
                position -= 1
            }
            if (carry != 0) return null
            length = index
        }
        val zeroCount = string.takeWhile { it == ALPHABET[0] }.length
        return ByteArray(zeroCount) + bytes.takeLast(length).toByteArray()
    }

    fun encodeChecked(prefix: ByteArray, payload: ByteArray): String {
        val value = prefix + payload
        val checksum = Bytes.sha256d(value).copyOf(4)
        return encode(value + checksum)
    }

    fun decodeChecked(string: String): Pair<ByteArray, ByteArray>? {
        val decoded = decode(string) ?: return null
        if (decoded.size <= 4) return null
        val value = decoded.copyOf(decoded.size - 4)
        val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
        val expected = Bytes.sha256d(value).copyOf(4)
        if (!checksum.contentEquals(expected)) return null
        return if (value.size >= 3 && value[0] == 0x01.toByte() && value[1] == 0xE1.toByte() && value[2] == 0x4B.toByte()) {
            byteArrayOf(0x01, 0xE1.toByte(), 0x4B) to value.copyOfRange(3, value.size)
        } else if (value.isNotEmpty() && value[0] == 0x21.toByte()) {
            byteArrayOf(0x21) to value.copyOfRange(1, value.size)
        } else if (value.isNotEmpty() && value[0] == 0x00.toByte()) {
            byteArrayOf(0x00) to value.copyOfRange(1, value.size)
        } else {
            null
        }
    }
}
