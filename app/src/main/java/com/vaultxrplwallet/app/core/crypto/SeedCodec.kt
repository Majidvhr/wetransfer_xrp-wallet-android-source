package com.vaultxrplwallet.app.core.crypto

enum class XrplSecretFormat {
    FAMILY_SEED,
    SECRET_NUMBERS,
    RFC1751,
    SEED_HEX,
    PASSPHRASE,
    RAW_PRIVATE_KEY_HEX,
}

object SeedCodec {
    fun normalizeHex(input: String): String =
        input.trim().replace("0x", "", ignoreCase = true).replace(" ", "").uppercase()

    fun decodeSecretNumbers(input: String): ByteArray {
        val normalized = input.uppercase().replace(" ", "-").replace(",", "-")
            .split(Regex("[-\\n\\t]+")).filter { it.isNotEmpty() }
        val blocks = if (normalized.size == 8) {
            normalized
        } else {
            val digits = input.filter { it.isDigit() }
            require(digits.length == 48) { "invalidSecretNumbers" }
            digits.chunked(6)
        }
        require(blocks.size == 8)
        val out = ArrayList<Byte>(16)
        blocks.forEachIndexed { index, block ->
            require(block.length == 6 && block.all { it.isDigit() })
            val value = block.toInt()
            val payload = value / 10
            val checksum = value % 10
            require(payload <= 65535)
            val expected = (payload * (index * 2 + 1)) % 9
            require(checksum == expected) { "invalidSecretNumbers" }
            out.add(((payload shr 8) and 0xFF).toByte())
            out.add((payload and 0xFF).toByte())
        }
        return out.toByteArray()
    }

    fun encodeSecretNumbers(entropy: ByteArray): String {
        require(entropy.size == 16) { "invalidSecretNumbers" }
        return (0 until 8).joinToString("-") { index ->
            val payload = ((entropy[index * 2].toInt() and 0xFF) shl 8) or
                (entropy[index * 2 + 1].toInt() and 0xFF)
            val checksum = (payload * (index * 2 + 1)) % 9
            "%06d".format(payload * 10 + checksum)
        }
    }

    fun decodeSeedHex(input: String): ByteArray {
        val hex = normalizeHex(input)
        val data = Bytes.hexDecode(hex)
        require(hex.length == 32 && data != null && data.size == 16) { "invalidSeedHex" }
        return data
    }

    fun seedFromPassphrase(input: String): ByteArray {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "invalidPassphrase" }
        return Bytes.sha512(trimmed.toByteArray(Charsets.UTF_8)).copyOf(16)
    }

    fun decodePrivateKeyHex(input: String): ByteArray {
        val hex = normalizeHex(input)
        val data = Bytes.hexDecode(hex)
        require(hex.length == 64 && data != null && data.size == 32) { "invalidPrivateKeyHex" }
        return data
    }

    fun decodeRfc1751(input: String): ByteArray {
        val words = input.uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(words.isNotEmpty() && words.size % 6 == 0) { "invalidRFC1751" }
        val indexMap = Rfc1751WordList.WORDS.withIndex().associate { it.value to it.index }
        var key = ByteArray(0)
        var offset = 0
        while (offset < words.size) {
            val sublist = words.subList(offset, offset + 6)
            val char = ByteArray(9)
            var bits = 0
            for (word in sublist) {
                val wordIndex = indexMap[word] ?: error("invalidRFC1751")
                val shift = (8 - (bits + 11) % 8) % 8
                val y = wordIndex shl shift
                val cl = ((y shr 16) and 0xFF).toByte()
                val cc = ((y shr 8) and 0xFF).toByte()
                val cr = (y and 0xFF).toByte()
                val base = bits shr 3
                when {
                    shift > 5 -> {
                        char[base] = ((char[base].toInt() and 0xFF) or (cl.toInt() and 0xFF)).toByte()
                        char[base + 1] = ((char[base + 1].toInt() and 0xFF) or (cc.toInt() and 0xFF)).toByte()
                        char[base + 2] = ((char[base + 2].toInt() and 0xFF) or (cr.toInt() and 0xFF)).toByte()
                    }
                    shift > -3 -> {
                        char[base] = ((char[base].toInt() and 0xFF) or (cc.toInt() and 0xFF)).toByte()
                        char[base + 1] = ((char[base + 1].toInt() and 0xFF) or (cr.toInt() and 0xFF)).toByte()
                    }
                    else -> char[base] = ((char[base].toInt() and 0xFF) or (cr.toInt() and 0xFF)).toByte()
                }
                bits += 11
            }
            val subkey = char.copyOf(8)
            val skbin = key2bin(subkey + byteArrayOf(char[8]))
            var p = 0
            var i = 0
            while (i < 64) {
                p += extract(skbin, i, 2)
                i += 2
            }
            require((p and 3) == extract(skbin, 64, 2)) { "invalidRFC1751" }
            key += subkey
            offset += 6
        }
        return key.reversedArray()
    }

    private val binaryNibble = arrayOf(
        "0000", "0001", "0010", "0011", "0100", "0101", "0110", "0111",
        "1000", "1001", "1010", "1011", "1100", "1101", "1110", "1111",
    )

    private fun key2bin(data: ByteArray): String = data.joinToString("") { byte ->
        val v = byte.toInt() and 0xFF
        binaryNibble[v ushr 4] + binaryNibble[v and 0x0F]
    }

    private fun extract(key: String, start: Int, length: Int): Int {
        val slice = key.substring(start, start + length)
        return slice.fold(0) { acc, c -> (acc shl 1) + (c.code - 48) }
    }
}
