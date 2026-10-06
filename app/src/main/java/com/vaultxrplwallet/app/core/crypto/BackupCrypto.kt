package com.vaultxrplwallet.app.core.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.util.Base64

class BackupCryptoException(message: String) : Exception(message)

object BackupCrypto {
    const val ASSOCIATED_DATA = "VaultXRPBackup.v1"
    const val KDF = "PBKDF2-HMAC-SHA256"
    const val DEFAULT_ITERATIONS = 310_000
    const val MIN_PASSWORD_CHARS = 10
    private const val MAX_PASSWORD_BYTES = 1_024
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val TAG_LEN = 16
    private const val KEY_LEN = 32
    private val aad: ByteArray = ASSOCIATED_DATA.toByteArray(Charsets.UTF_8)

    fun export(
        plaintext: ByteArray,
        password: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        validatePassword(password)
        if (iterations < 100_000) throw BackupCryptoException("invalidEnvelope")
        val salt = Bytes.random(SALT_LEN)
        val nonce = Bytes.random(NONCE_LEN)
        val key = pbkdf2Sha256(password, salt, iterations, KEY_LEN)
        val (ciphertext, tag) = aesGcmSeal(key, nonce, plaintext)
        val json = buildString {
            append("{")
            append("\"ciphertext\":").append(jsonB64(ciphertext)).append(',')
            append("\"iterations\":").append(iterations).append(',')
            append("\"kdf\":").append(jsonQuote(KDF)).append(',')
            append("\"nonce\":").append(jsonB64(nonce)).append(',')
            append("\"salt\":").append(jsonB64(salt)).append(',')
            append("\"tag\":").append(jsonB64(tag)).append(',')
            append("\"version\":1")
            append("}")
        }
        return json.toByteArray(Charsets.UTF_8)
    }

    fun open(envelope: ByteArray, password: String): ByteArray {
        validatePassword(password)
        val json = envelope.toString(Charsets.UTF_8)
        val version = jsonInt(json, "version") ?: throw BackupCryptoException("invalidEnvelope")
        val kdf = jsonField(json, "kdf") ?: throw BackupCryptoException("invalidEnvelope")
        val iterations = jsonInt(json, "iterations") ?: throw BackupCryptoException("invalidEnvelope")
        if (version != 1 || kdf != KDF || iterations !in 100_000..2_000_000) {
            throw BackupCryptoException("invalidEnvelope")
        }
        val salt = b64(jsonField(json, "salt"))
        val nonce = b64(jsonField(json, "nonce"))
        val ciphertext = b64(jsonField(json, "ciphertext"))
        val tag = b64(jsonField(json, "tag"))
        if (salt.size != SALT_LEN || nonce.size != NONCE_LEN || ciphertext.isEmpty() || tag.size != TAG_LEN) {
            throw BackupCryptoException("invalidEnvelope")
        }
        val key = pbkdf2Sha256(password, salt, iterations, KEY_LEN)
        return try {
            aesGcmOpen(key, nonce, ciphertext, tag)
        } catch (_: Exception) {
            throw BackupCryptoException("authenticationFailed")
        }
    }

    fun pbkdf2Sha256(
        password: String,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int,
    ): ByteArray {
        require(iterations > 0 && keyLength > 0)
        val gen = PKCS5S2ParametersGenerator(SHA256Digest())
        gen.init(password.toByteArray(Charsets.UTF_8), salt, iterations)
        return (gen.generateDerivedParameters(keyLength * 8) as KeyParameter).key
    }

    private fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(true, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, n)
        val ciphertext = out.copyOf(out.size - TAG_LEN)
        val tag = out.copyOfRange(out.size - TAG_LEN, out.size)
        return ciphertext to tag
    }

    private fun aesGcmOpen(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray,
    ): ByteArray {
        val packed = ciphertext + tag
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(packed.size))
        val n = cipher.processBytes(packed, 0, packed.size, out, 0)
        val m = cipher.doFinal(out, n)
        return out.copyOf(n + m)
    }

    private fun validatePassword(password: String) {
        if (password.length < MIN_PASSWORD_CHARS || password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES) {
            throw BackupCryptoException("passwordTooShort")
        }
    }

    private fun b64(value: String?): ByteArray {
        if (value.isNullOrEmpty()) throw BackupCryptoException("invalidEnvelope")
        return try {
            Base64.getDecoder().decode(value)
        } catch (_: Exception) {
            throw BackupCryptoException("invalidEnvelope")
        }
    }

    private fun jsonB64(data: ByteArray): String =
        jsonQuote(Base64.getEncoder().encodeToString(data))

    private fun jsonQuote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(c)
            }
        }
        append('"')
    }

    private fun jsonField(json: String, key: String): String? {
        val needle = "\"$key\""
        val start = json.indexOf(needle)
        if (start < 0) return null
        var i = start + needle.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != ':') return null
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        i++
        val out = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            when (c) {
                '\\' -> {
                    if (i + 1 >= json.length) return null
                    out.append(json[i + 1])
                    i += 2
                }
                '"' -> return out.toString()
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return null
    }

    private fun jsonInt(json: String, key: String): Int? {
        val needle = "\"$key\""
        val start = json.indexOf(needle)
        if (start < 0) return null
        var i = start + needle.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != ':') return null
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        val begin = i
        while (i < json.length && (json[i].isDigit() || json[i] == '-')) i++
        return json.substring(begin, i).toIntOrNull()
    }
}
