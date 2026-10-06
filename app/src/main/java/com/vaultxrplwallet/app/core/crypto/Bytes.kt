package com.vaultxrplwallet.app.core.crypto

import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom

object Bytes {
    private val random = SecureRandom()

    fun random(size: Int): ByteArray {
        val out = ByteArray(size)
        random.nextBytes(out)
        return out
    }

    fun hexEncode(data: ByteArray, upper: Boolean = true): String {
        val hex = if (upper) "0123456789ABCDEF" else "0123456789abcdef"
        val out = StringBuilder(data.size * 2)
        for (b in data) {
            val v = b.toInt() and 0xFF
            out.append(hex[v ushr 4])
            out.append(hex[v and 0x0F])
        }
        return out.toString()
    }

    fun hexDecode(input: String): ByteArray? {
        val hex = input.trim().removePrefix("0x").removePrefix("0X").replace(" ", "").uppercase()
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hex[i * 2].digitToIntOrNull(16) ?: return null
            val lo = hex[i * 2 + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun u16be(value: Int): ByteArray = byteArrayOf(
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    fun u32be(value: Long): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    fun u64be(value: Long): ByteArray = byteArrayOf(
        ((value ushr 56) and 0xFF).toByte(),
        ((value ushr 48) and 0xFF).toByte(),
        ((value ushr 40) and 0xFF).toByte(),
        ((value ushr 32) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    fun sha256(data: ByteArray): ByteArray {
        val d = SHA256Digest()
        d.update(data, 0, data.size)
        val out = ByteArray(32)
        d.doFinal(out, 0)
        return out
    }

    fun sha256d(data: ByteArray): ByteArray = sha256(sha256(data))

    fun sha512(data: ByteArray): ByteArray {
        val d = SHA512Digest()
        d.update(data, 0, data.size)
        val out = ByteArray(64)
        d.doFinal(out, 0)
        return out
    }

    fun sha512Half(data: ByteArray): ByteArray = sha512(data).copyOf(32)

    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA512Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        val out = ByteArray(64)
        mac.doFinal(out, 0)
        return out
    }

    fun ripemd160(data: ByteArray): ByteArray {
        val d = RIPEMD160Digest()
        d.update(data, 0, data.size)
        val out = ByteArray(20)
        d.doFinal(out, 0)
        return out
    }

    fun concat(vararg parts: ByteArray): ByteArray {
        val size = parts.sumOf { it.size }
        val out = ByteArray(size)
        var o = 0
        for (p in parts) {
            p.copyInto(out, o)
            o += p.size
        }
        return out
    }
}
