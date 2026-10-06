package com.vaultxrplwallet.app.core.crypto

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter

object Bip39 {
    fun normalize(phrase: String): String =
        phrase.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    fun generate24(): String = fromEntropy(Bytes.random(32))

    fun fromEntropy(entropy: ByteArray): String {
        require(entropy.size == 16 || entropy.size == 20 || entropy.size == 24 || entropy.size == 32)
        val checksumBits = entropy.size / 4
        val hash = Bytes.sha256(entropy)
        val bits = entropy.toBitString() + hash.toBitString().take(checksumBits)
        val words = Bip39WordList.ENGLISH
        return bits.chunked(11).joinToString(" ") { chunk ->
            words[Integer.parseInt(chunk, 2)]
        }
    }

    fun validate(phrase: String): Boolean {
        val words = normalize(phrase).split(" ")
        if (words.size !in setOf(12, 15, 18, 21, 24)) return false
        val indexes = words.map { Bip39WordList.ENGLISH.indexOf(it) }
        if (indexes.any { it < 0 }) return false
        val bits = indexes.joinToString("") { it.toString(2).padStart(11, '0') }
        val checksumLen = bits.length / 33
        val entropyBits = bits.dropLast(checksumLen)
        val checksum = bits.takeLast(checksumLen)
        val entropy = entropyBits.chunked(8).map { Integer.parseInt(it, 2).toByte() }.toByteArray()
        val expected = Bytes.sha256(entropy).toBitString().take(checksumLen)
        return checksum == expected
    }

    fun toSeed(phrase: String, passphrase: String = ""): ByteArray {
        val mnemonic = normalize(phrase)
        val salt = "mnemonic$passphrase"
        val gen = PKCS5S2ParametersGenerator(SHA512Digest())
        gen.init(mnemonic.toByteArray(Charsets.UTF_8), salt.toByteArray(Charsets.UTF_8), 2048)
        return (gen.generateDerivedParameters(512) as KeyParameter).key
    }

    private fun ByteArray.toBitString(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') }
}
