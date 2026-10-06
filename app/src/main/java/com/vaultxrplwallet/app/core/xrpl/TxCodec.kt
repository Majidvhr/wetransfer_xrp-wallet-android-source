package com.vaultxrplwallet.app.core.xrpl

import com.vaultxrplwallet.app.core.crypto.AddressCodec
import com.vaultxrplwallet.app.core.crypto.Bip32
import com.vaultxrplwallet.app.core.crypto.Bytes
import com.vaultxrplwallet.app.core.crypto.KeyEngine
import com.vaultxrplwallet.app.core.crypto.WalletKeyAlgorithm
import com.vaultxrplwallet.app.core.crypto.WalletRecord
import com.vaultxrplwallet.app.data.SignedXrplTransaction
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.crypto.digests.SHA256Digest
import java.math.BigInteger

data class XrplPayment(
    val account: String,
    val destination: String,
    val amountDrops: Long,
    val feeDrops: Long,
    val sequence: Long,
    val lastLedgerSequence: Long,
    val destinationTag: Long?,
    val memo: String?,
    val binaryMemo: ByteArray?,
    val flags: Long = 0x80000000L,
)

data class XrplTrustSet(
    val account: String,
    val currency: String,
    val issuer: String,
    val limit: String,
    val feeDrops: Long,
    val sequence: Long,
    val lastLedgerSequence: Long,
    val flags: Long = 0x80000000L or 0x00020000L,
)

object TxCodec {
    private const val STX = 0x53545800
    private val stxPrefix = byteArrayOf(0x53, 0x54, 0x58, 0x00)
    private val txnPrefix = byteArrayOf(0x54, 0x58, 0x4E, 0x00)

    fun signingData(payment: XrplPayment, publicKey: ByteArray): ByteArray =
        stxPrefix + serialize(payment, publicKey, null)

    fun signedBlob(payment: XrplPayment, publicKey: ByteArray, signature: ByteArray): ByteArray =
        serialize(payment, publicKey, signature)

    fun signingData(trustSet: XrplTrustSet, publicKey: ByteArray): ByteArray =
        stxPrefix + serialize(trustSet, publicKey, null)

    fun signedBlob(trustSet: XrplTrustSet, publicKey: ByteArray, signature: ByteArray): ByteArray =
        serialize(trustSet, publicKey, signature)

    fun transactionHash(blob: ByteArray): ByteArray = Bytes.sha512Half(txnPrefix + blob)

    fun signPayment(payment: XrplPayment, wallet: WalletRecord, secret: String): SignedXrplTransaction {
        val privateKey = KeyEngine().signingPrivateKey(secret, wallet)
        val publicKey = Bytes.hexDecode(wallet.publicKey) ?: error("invalidPublicKey")
        val signing = signingData(payment, publicKey)
        val signature = signature(signing, privateKey, publicKey, wallet.keyAlgorithm)
        val blob = signedBlob(payment, publicKey, signature)
        return SignedXrplTransaction(
            Bytes.hexEncode(blob),
            Bytes.hexEncode(transactionHash(blob)),
            Bytes.hexEncode(signature),
        )
    }

    fun signTrustSet(trustSet: XrplTrustSet, wallet: WalletRecord, secret: String): SignedXrplTransaction {
        val privateKey = KeyEngine().signingPrivateKey(secret, wallet)
        val publicKey = Bytes.hexDecode(wallet.publicKey) ?: error("invalidPublicKey")
        val signing = signingData(trustSet, publicKey)
        val signature = signature(signing, privateKey, publicKey, wallet.keyAlgorithm)
        val blob = signedBlob(trustSet, publicKey, signature)
        return SignedXrplTransaction(
            Bytes.hexEncode(blob),
            Bytes.hexEncode(transactionHash(blob)),
            Bytes.hexEncode(signature),
        )
    }

    private fun signature(
        signingData: ByteArray,
        privateKey: ByteArray,
        publicKey: ByteArray,
        algorithm: WalletKeyAlgorithm,
    ): ByteArray = when (algorithm) {
        WalletKeyAlgorithm.ED25519 -> {
            val key = Ed25519PrivateKeyParameters(privateKey, 0)
            val expected = byteArrayOf(0xED.toByte()) + key.generatePublicKey().encoded
            require(expected.contentEquals(publicKey))
            val signer = Ed25519Signer()
            signer.init(true, key)
            signer.update(signingData, 0, signingData.size)
            signer.generateSignature()
        }
        WalletKeyAlgorithm.SECP256K1 -> {
            require(Bip32.compressedPublicKey(privateKey).contentEquals(publicKey))
            val digest = Bytes.sha512Half(signingData)
            val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
            signer.init(true, Bip32.privateKeyParams(privateKey))
            val parts = signer.generateSignature(digest)
            derEncode(parts[0], parts[1])
        }
        WalletKeyAlgorithm.UNKNOWN -> error("unsupported")
    }

    private fun derEncode(r: BigInteger, s: BigInteger): ByteArray {
        val n = Bip32.domain.n
        val half = n.shiftRight(1)
        val sLow = if (s > half) n.subtract(s) else s
        fun encodeInt(v: BigInteger): ByteArray {
            var bytes = v.toByteArray()
            if (bytes[0] == 0.toByte() && bytes.size > 1 && bytes[1] >= 0) {
                bytes = bytes.copyOfRange(1, bytes.size)
            }
            return byteArrayOf(0x02, bytes.size.toByte()) + bytes
        }
        val body = encodeInt(r) + encodeInt(sLow)
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun serialize(payment: XrplPayment, publicKey: ByteArray, signature: ByteArray?): ByteArray {
        require(payment.amountDrops > 0 && payment.amountDrops <= 100_000_000_000_000_000L)
        require(payment.feeDrops in 1..10_000)
        require(publicKey.size == 33)
        val out = ArrayList<Byte>()
        fun add(bytes: ByteArray) { bytes.forEach { out.add(it) } }
        add(byteArrayOf(0x12) + Bytes.u16be(0))
        add(byteArrayOf(0x22) + Bytes.u32be(payment.flags))
        add(byteArrayOf(0x24) + Bytes.u32be(payment.sequence))
        payment.destinationTag?.let { add(byteArrayOf(0x2E) + Bytes.u32be(it)) }
        add(byteArrayOf(0x20, 0x1B) + Bytes.u32be(payment.lastLedgerSequence))
        add(byteArrayOf(0x61) + Bytes.u64be(payment.amountDrops or 0x4000000000000000L))
        add(byteArrayOf(0x68) + Bytes.u64be(payment.feeDrops or 0x4000000000000000L))
        add(byteArrayOf(0x73) + vl(publicKey))
        if (signature != null) add(byteArrayOf(0x74) + vl(signature))
        add(byteArrayOf(0x81.toByte()) + vl(AddressCodec.accountId(payment.account)!!))
        add(byteArrayOf(0x83.toByte()) + vl(AddressCodec.accountId(payment.destination)!!))
        val binary = payment.binaryMemo
        if (binary != null) {
            require(binary.size == 32 && payment.destinationTag == null)
            appendMemo(out, binary)
        } else {
            val memo = payment.memo?.trim().orEmpty()
            if (memo.isNotEmpty()) {
                val data = memo.toByteArray(Charsets.UTF_8)
                require(data.size <= 256)
                appendMemo(out, data)
            }
        }
        return out.toByteArray()
    }

    private fun serialize(trustSet: XrplTrustSet, publicKey: ByteArray, signature: ByteArray?): ByteArray {
        require(trustSet.feeDrops in 1..10_000 && publicKey.size == 33)
        val out = ArrayList<Byte>()
        fun add(bytes: ByteArray) { bytes.forEach { out.add(it) } }
        add(byteArrayOf(0x12) + Bytes.u16be(20))
        add(byteArrayOf(0x22) + Bytes.u32be(trustSet.flags))
        add(byteArrayOf(0x24) + Bytes.u32be(trustSet.sequence))
        add(byteArrayOf(0x20, 0x1B) + Bytes.u32be(trustSet.lastLedgerSequence))
        add(byteArrayOf(0x63) + issuedAmount(trustSet.limit, trustSet.currency, trustSet.issuer))
        add(byteArrayOf(0x68) + Bytes.u64be(trustSet.feeDrops or 0x4000000000000000L))
        add(byteArrayOf(0x73) + vl(publicKey))
        if (signature != null) add(byteArrayOf(0x74) + vl(signature))
        add(byteArrayOf(0x81.toByte()) + vl(AddressCodec.accountId(trustSet.account)!!))
        return out.toByteArray()
    }

    private fun appendMemo(out: ArrayList<Byte>, memo: ByteArray) {
        out.add(0xF9.toByte())
        out.add(0xEA.toByte())
        out.add(0x7D)
        vl(memo).forEach { out.add(it) }
        out.add(0xE1.toByte())
        out.add(0xF1.toByte())
    }

    private fun vl(value: ByteArray): ByteArray {
        val length = value.size
        require(length <= 918_744)
        val prefix = when {
            length <= 192 -> byteArrayOf(length.toByte())
            length <= 12_480 -> {
                val adjusted = length - 193
                byteArrayOf((193 + (adjusted shr 8)).toByte(), (adjusted and 0xFF).toByte())
            }
            else -> {
                val adjusted = length - 12_481
                byteArrayOf(
                    (241 + (adjusted shr 16)).toByte(),
                    ((adjusted shr 8) and 0xFF).toByte(),
                    (adjusted and 0xFF).toByte(),
                )
            }
        }
        return prefix + value
    }

    private fun issuedAmount(value: String, currency: String, issuer: String): ByteArray {
        val amount = encodeIssuedValue(value)
        val currencyData = encodeCurrency(currency)
        val issuerData = AddressCodec.accountId(issuer) ?: error("invalidAddress")
        return Bytes.u64be(amount) + currencyData + issuerData
    }

    private fun encodeIssuedValue(value: String): Long {
        val text = value.trim()
        require(text.isNotEmpty() && !text.startsWith("-"))
        val parts = text.split(".")
        require(parts.size <= 2 && parts.all { it.all { c -> c.isDigit() } })
        val fractionCount = if (parts.size == 2) parts[1].length else 0
        var digits = parts.joinToString("").trimStart('0')
        if (digits.isEmpty()) return java.lang.Long.parseUnsignedLong("8000000000000000", 16)
        var exponent = -fractionCount
        while (digits.endsWith("0")) {
            digits = digits.dropLast(1)
            exponent += 1
        }
        var normalized = digits
        if (normalized.length < 16) {
            val padding = 16 - normalized.length
            normalized += "0".repeat(padding)
            exponent -= padding
        } else if (normalized.length > 16) {
            require(normalized.drop(16).all { it == '0' })
            exponent += normalized.length - 16
            normalized = normalized.take(16)
        }
        require(exponent in -96..80)
        val mantissa = normalized.toLong()
        require(mantissa in 1_000_000_000_000_000L..9_999_999_999_999_999L)
        return java.lang.Long.parseUnsignedLong("C000000000000000", 16) or
            ((exponent + 97).toLong() shl 54) or mantissa
    }

    fun resolveCurrencyCode(display: String, raw: String? = null): String {
        val candidate = raw?.trim()?.takeIf { it.isNotEmpty() } ?: display.trim()
        if (candidate.length == 3 || candidate.length == 40) return candidate
        val bytes = candidate.toByteArray(Charsets.US_ASCII)
        require(bytes.isNotEmpty() && bytes.size <= 20 && bytes.all { it.toInt() in 0x20..0x7E }) {
            "invalidCurrency"
        }
        return Bytes.hexEncode(bytes + ByteArray(20 - bytes.size), upper = true)
    }

    private fun encodeCurrency(currency: String): ByteArray {
        val normalized = currency.trim()
        if (normalized.length == 3 && !normalized.equals("XRP", ignoreCase = true)) {
            val data = ByteArray(20)
            normalized.uppercase().toByteArray(Charsets.US_ASCII).copyInto(data, 12)
            return data
        }
        if (normalized.length == 40) {
            val data = Bytes.hexDecode(normalized)
            require(data != null && data.size == 20 && data.any { it != 0.toByte() })
            return data
        }
        val ascii = normalized.toByteArray(Charsets.US_ASCII)
        require(ascii.isNotEmpty() && ascii.size <= 20 && ascii.all { it.toInt() in 0x20..0x7E })
        val data = ByteArray(20)
        ascii.copyInto(data)
        return data
    }
}
