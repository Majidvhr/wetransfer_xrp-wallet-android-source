package com.vaultxrplwallet.app.core.crypto

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import java.math.BigInteger

object Bip32 {
    private val curve = SECNamedCurves.getByName("secp256k1")
    val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
    private val n: BigInteger = curve.n

    fun derivePrivateKey(seed: ByteArray, path: String): ByteArray {
        val master = Bytes.hmacSha512("Bitcoin seed".toByteArray(), seed)
        var key = master.copyOf(32)
        var chain = master.copyOfRange(32, 64)
        val components = path.split("/").drop(1)
        for (component in components) {
            val hardened = component.endsWith("'")
            val number = component.trimEnd('\'').toLong()
            val index = if (hardened) number or 0x80000000L else number
            val data = if (hardened) {
                Bytes.concat(byteArrayOf(0), key, Bytes.u32be(index))
            } else {
                Bytes.concat(compressedPublicKey(key), Bytes.u32be(index))
            }
            val digest = Bytes.hmacSha512(chain, data)
            val il = digest.copyOf(32)
            key = addScalar(key, il) ?: error("invalid child")
            chain = digest.copyOfRange(32, 64)
        }
        return key
    }

    fun compressedPublicKey(privateKey: ByteArray): ByteArray {
        val d = BigInteger(1, privateKey)
        val q = FixedPointCombMultiplier().multiply(domain.g, d).normalize()
        return q.getEncoded(true)
    }

    fun addScalar(parent: ByteArray, tweak: ByteArray): ByteArray? {
        val sum = BigInteger(1, parent).add(BigInteger(1, tweak)).mod(n)
        if (sum == BigInteger.ZERO) return null
        return sum.toFixed32()
    }

    fun isValidPrivateKey(key: ByteArray): Boolean {
        if (key.size != 32) return false
        val d = BigInteger(1, key)
        return d > BigInteger.ZERO && d < n
    }

    fun privateKeyParams(key: ByteArray): ECPrivateKeyParameters =
        ECPrivateKeyParameters(BigInteger(1, key), domain)
}

fun BigInteger.toFixed32(): ByteArray {
    val raw = toByteArray()
    return when {
        raw.size == 32 -> raw
        raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
        else -> ByteArray(32 - raw.size) + raw
    }
}
