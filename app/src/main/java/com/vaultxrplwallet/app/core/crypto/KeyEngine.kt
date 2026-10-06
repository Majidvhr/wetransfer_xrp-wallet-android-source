package com.vaultxrplwallet.app.core.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.util.UUID

enum class WalletKeyAlgorithm {
    ED25519,
    SECP256K1,
    UNKNOWN,
}

enum class WalletImportKind {
    ED25519_FAMILY_SEED,
    SECP256K1_FAMILY_SEED,
    BIP39,
    SECRET_NUMBERS,
    RFC1751,
    SEED_HEX,
    PASSPHRASE,
    RAW_PRIVATE_KEY_HEX,
    WATCH_ONLY,
}

enum class WalletBackupStatus {
    UNVERIFIED,
    VERIFIED,
}

data class WalletRecord(
    val id: String,
    val name: String,
    val classicAddress: String,
    val publicKey: String,
    val keyAlgorithm: WalletKeyAlgorithm,
    val importKind: WalletImportKind?,
    val derivationPath: String?,
    val addedAtEpochMs: Long,
    val backupStatus: WalletBackupStatus,
) {
    val resolvedImportKind: WalletImportKind
        get() = importKind ?: if (keyAlgorithm == WalletKeyAlgorithm.ED25519) {
            WalletImportKind.ED25519_FAMILY_SEED
        } else {
            WalletImportKind.BIP39
        }

    val isWatchOnly: Boolean
        get() = resolvedImportKind == WalletImportKind.WATCH_ONLY
}

data class GeneratedWalletMaterial(
    val secret: String,
    val record: WalletRecord,
)

data class DerivedWalletCandidate(
    val path: String,
    val accountIndex: Int,
    val classicAddress: String,
    val publicKey: String,
)

class KeyEngineException(message: String) : Exception(message)

class KeyEngine {
    fun generateEd25519(
        name: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): GeneratedWalletMaterial {
        val cleanName = requireName(name)
        val entropy = Bytes.random(16)
        val secret = encodeEd25519Seed(entropy)
        return materialEd25519(
            seed = entropy,
            storedSecret = secret,
            importKind = WalletImportKind.ED25519_FAMILY_SEED,
            name = cleanName,
            nowEpochMs = nowEpochMs,
        )
    }

    fun importEd25519(
        seed: String,
        name: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): GeneratedWalletMaterial {
        val cleanName = requireName(name)
        val decoded = Base58Xrp.decodeChecked(seed) ?: throw KeyEngineException("invalidSeed")
        if (!decoded.first.contentEquals(ED25519_SEED_PREFIX) || decoded.second.size != 16) {
            throw KeyEngineException("invalidSeed")
        }
        return materialEd25519(
            seed = decoded.second,
            storedSecret = seed,
            importKind = WalletImportKind.ED25519_FAMILY_SEED,
            name = cleanName,
            nowEpochMs = nowEpochMs,
        )
    }

    fun importSecp256k1(
        seed: String,
        name: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): GeneratedWalletMaterial {
        val cleanName = requireName(name)
        val decoded = Base58Xrp.decodeChecked(seed) ?: throw KeyEngineException("invalidSeed")
        if (!decoded.first.contentEquals(SECP_SEED_PREFIX) || decoded.second.size != 16) {
            throw KeyEngineException("invalidSeed")
        }
        return materialSecp(
            entropy = decoded.second,
            storedSecret = seed,
            importKind = WalletImportKind.SECP256K1_FAMILY_SEED,
            name = cleanName,
            nowEpochMs = nowEpochMs,
        )
    }

    fun importSeedMaterial(
        input: String,
        format: XrplSecretFormat,
        algorithm: WalletKeyAlgorithm = WalletKeyAlgorithm.SECP256K1,
        name: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): GeneratedWalletMaterial {
        val cleanName = requireName(name)
        val trimmed = input.trim()
        if (trimmed.isEmpty()) throw KeyEngineException("invalidSeed")
        val resolved = if (algorithm == WalletKeyAlgorithm.ED25519) {
            WalletKeyAlgorithm.ED25519
        } else {
            WalletKeyAlgorithm.SECP256K1
        }
        return when (format) {
            XrplSecretFormat.FAMILY_SEED -> {
                if (trimmed.startsWith("sEd")) {
                    importEd25519(trimmed, cleanName, nowEpochMs)
                } else {
                    importSecp256k1(trimmed, cleanName, nowEpochMs)
                }
            }
            XrplSecretFormat.SECRET_NUMBERS -> materialFromEntropy(
                SeedCodec.decodeSecretNumbers(trimmed),
                trimmed,
                WalletImportKind.SECRET_NUMBERS,
                resolved,
                cleanName,
                nowEpochMs,
            )
            XrplSecretFormat.RFC1751 -> materialFromEntropy(
                SeedCodec.decodeRfc1751(trimmed),
                trimmed,
                WalletImportKind.RFC1751,
                resolved,
                cleanName,
                nowEpochMs,
            )
            XrplSecretFormat.SEED_HEX -> materialFromEntropy(
                SeedCodec.decodeSeedHex(trimmed),
                SeedCodec.normalizeHex(trimmed),
                WalletImportKind.SEED_HEX,
                resolved,
                cleanName,
                nowEpochMs,
            )
            XrplSecretFormat.PASSPHRASE -> materialFromEntropy(
                SeedCodec.seedFromPassphrase(trimmed),
                trimmed,
                WalletImportKind.PASSPHRASE,
                resolved,
                cleanName,
                nowEpochMs,
            )
            XrplSecretFormat.RAW_PRIVATE_KEY_HEX -> materialFromRawPrivateKey(
                SeedCodec.decodePrivateKeyHex(trimmed),
                SeedCodec.normalizeHex(trimmed),
                resolved,
                cleanName,
                nowEpochMs,
            )
        }
    }

    fun importSeedMaterial(
        input: String,
        kind: WalletImportKind,
        algorithm: WalletKeyAlgorithm = WalletKeyAlgorithm.SECP256K1,
        name: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): GeneratedWalletMaterial {
        val format = when (kind) {
            WalletImportKind.ED25519_FAMILY_SEED,
            WalletImportKind.SECP256K1_FAMILY_SEED,
            -> XrplSecretFormat.FAMILY_SEED
            WalletImportKind.SECRET_NUMBERS -> XrplSecretFormat.SECRET_NUMBERS
            WalletImportKind.RFC1751 -> XrplSecretFormat.RFC1751
            WalletImportKind.SEED_HEX -> XrplSecretFormat.SEED_HEX
            WalletImportKind.PASSPHRASE -> XrplSecretFormat.PASSPHRASE
            WalletImportKind.RAW_PRIVATE_KEY_HEX -> XrplSecretFormat.RAW_PRIVATE_KEY_HEX
            WalletImportKind.BIP39,
            WalletImportKind.WATCH_ONLY,
            -> throw KeyEngineException("invalidSeed")
        }
        return importSeedMaterial(input, format, algorithm, name, nowEpochMs)
    }

    fun encodeEd25519Seed(entropy: ByteArray): String {
        if (entropy.size != 16) throw KeyEngineException("invalidSeed")
        return Base58Xrp.encodeChecked(ED25519_SEED_PREFIX, entropy)
    }

    fun encodeSecp256k1Seed(entropy: ByteArray): String {
        if (entropy.size != 16) throw KeyEngineException("invalidSeed")
        return Base58Xrp.encodeChecked(SECP_SEED_PREFIX, entropy)
    }

    fun generateMnemonic(): String = Bip39.generate24()

    fun deriveMnemonicCandidates(
        phrase: String,
        count: Int = 5,
        passphrase: String = "",
    ): List<DerivedWalletCandidate> {
        val n = maxOf(1, count)
        return (0 until n).map { accountIndex ->
            deriveMnemonicCandidate(
                phrase = phrase,
                passphrase = passphrase,
                path = "m/44'/144'/$accountIndex'/0/0",
            )
        }
    }

    fun deriveMnemonicCandidate(
        phrase: String,
        passphrase: String = "",
        path: String,
    ): DerivedWalletCandidate {
        val normalized = Bip39.normalize(phrase)
        if (!Bip39.validate(normalized)) throw KeyEngineException("invalidMnemonic")
        val seed = Bip39.toSeed(normalized, passphrase)
        val key = try {
            Bip32.derivePrivateKey(seed, path)
        } catch (_: Exception) {
            throw KeyEngineException("invalidDerivation")
        }
        val publicKey = Bip32.compressedPublicKey(key)
        val accountIndex = path.split("/")
            .drop(3)
            .firstOrNull()
            ?.trim('\'')
            ?.toIntOrNull()
            ?: 0
        return DerivedWalletCandidate(
            path = path,
            accountIndex = accountIndex,
            classicAddress = AddressCodec.classicAddress(publicKey),
            publicKey = Bytes.hexEncode(publicKey, upper = true),
        )
    }

    fun material(
        phrase: String,
        candidate: DerivedWalletCandidate,
        name: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): GeneratedWalletMaterial = material(phrase, "", candidate, name, nowEpochMs)

    fun material(
        phrase: String,
        passphrase: String,
        candidate: DerivedWalletCandidate,
        name: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): GeneratedWalletMaterial {
        val cleanName = requireName(name)
        val verified = deriveMnemonicCandidate(phrase, passphrase, candidate.path)
        if (verified != candidate) throw KeyEngineException("invalidDerivation")
        val normalizedPhrase = Bip39.normalize(phrase)
        val storedSecret = if (passphrase.isEmpty()) {
            normalizedPhrase
        } else {
            WalletSecretEnvelope.encode(normalizedPhrase, passphrase)
        }
        return GeneratedWalletMaterial(
            secret = storedSecret,
            record = WalletRecord(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                classicAddress = candidate.classicAddress,
                publicKey = candidate.publicKey,
                keyAlgorithm = WalletKeyAlgorithm.SECP256K1,
                importKind = WalletImportKind.BIP39,
                derivationPath = candidate.path,
                addedAtEpochMs = nowEpochMs,
                backupStatus = WalletBackupStatus.UNVERIFIED,
            ),
        )
    }

    fun signingPrivateKey(secret: String, wallet: WalletRecord): ByteArray {
        return when (wallet.resolvedImportKind) {
            WalletImportKind.ED25519_FAMILY_SEED -> {
                val decoded = Base58Xrp.decodeChecked(secret) ?: throw KeyEngineException("invalidSeed")
                if (!decoded.first.contentEquals(ED25519_SEED_PREFIX) || decoded.second.size != 16) {
                    throw KeyEngineException("invalidSeed")
                }
                Bytes.sha512(decoded.second).copyOf(32)
            }
            WalletImportKind.SECP256K1_FAMILY_SEED -> {
                val decoded = Base58Xrp.decodeChecked(secret) ?: throw KeyEngineException("invalidSeed")
                if (!decoded.first.contentEquals(SECP_SEED_PREFIX) || decoded.second.size != 16) {
                    throw KeyEngineException("invalidSeed")
                }
                deriveFamilySeedPrivateKey(decoded.second, 0)
            }
            WalletImportKind.SECRET_NUMBERS ->
                signingKeyFromEntropy(SeedCodec.decodeSecretNumbers(secret), wallet.keyAlgorithm)
            WalletImportKind.RFC1751 ->
                signingKeyFromEntropy(SeedCodec.decodeRfc1751(secret), wallet.keyAlgorithm)
            WalletImportKind.SEED_HEX ->
                signingKeyFromEntropy(SeedCodec.decodeSeedHex(secret), wallet.keyAlgorithm)
            WalletImportKind.PASSPHRASE ->
                signingKeyFromEntropy(SeedCodec.seedFromPassphrase(secret), wallet.keyAlgorithm)
            WalletImportKind.RAW_PRIVATE_KEY_HEX ->
                SeedCodec.decodePrivateKeyHex(secret)
            WalletImportKind.BIP39 -> {
                val envelope = WalletSecretEnvelope.decode(secret)
                val normalized = Bip39.normalize(envelope?.phrase ?: secret)
                if (!Bip39.validate(normalized)) throw KeyEngineException("invalidMnemonic")
                val seed = Bip39.toSeed(normalized, envelope?.passphrase ?: "")
                try {
                    Bip32.derivePrivateKey(seed, wallet.derivationPath ?: DEFAULT_XRPL_PATH)
                } catch (_: Exception) {
                    throw KeyEngineException("invalidDerivation")
                }
            }
            WalletImportKind.WATCH_ONLY -> throw KeyEngineException("invalidSeed")
        }
    }

    fun classicAddress(publicKey: ByteArray): String {
        if (publicKey.size != 33) throw KeyEngineException("invalidDerivation")
        return AddressCodec.classicAddress(publicKey)
    }

    private fun materialFromEntropy(
        entropy: ByteArray,
        storedSecret: String,
        importKind: WalletImportKind,
        algorithm: WalletKeyAlgorithm,
        name: String,
        nowEpochMs: Long,
    ): GeneratedWalletMaterial {
        return when (algorithm) {
            WalletKeyAlgorithm.ED25519 -> materialEd25519(
                seed = entropy,
                storedSecret = storedSecret,
                importKind = importKind,
                name = name,
                nowEpochMs = nowEpochMs,
            )
            WalletKeyAlgorithm.SECP256K1,
            WalletKeyAlgorithm.UNKNOWN,
            -> materialSecp(
                entropy = entropy,
                storedSecret = storedSecret,
                importKind = importKind,
                name = name,
                nowEpochMs = nowEpochMs,
            )
        }
    }

    private fun materialFromRawPrivateKey(
        key: ByteArray,
        storedSecret: String,
        algorithm: WalletKeyAlgorithm,
        name: String,
        nowEpochMs: Long,
    ): GeneratedWalletMaterial {
        return when (algorithm) {
            WalletKeyAlgorithm.ED25519 -> {
                val publicKey = ed25519PublicKey(key)
                GeneratedWalletMaterial(
                    secret = storedSecret,
                    record = WalletRecord(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        classicAddress = AddressCodec.classicAddress(publicKey),
                        publicKey = Bytes.hexEncode(publicKey, upper = true),
                        keyAlgorithm = WalletKeyAlgorithm.ED25519,
                        importKind = WalletImportKind.RAW_PRIVATE_KEY_HEX,
                        derivationPath = null,
                        addedAtEpochMs = nowEpochMs,
                        backupStatus = WalletBackupStatus.UNVERIFIED,
                    ),
                )
            }
            WalletKeyAlgorithm.SECP256K1,
            WalletKeyAlgorithm.UNKNOWN,
            -> {
                if (!Bip32.isValidPrivateKey(key)) throw KeyEngineException("invalidSeed")
                val publicKey = Bip32.compressedPublicKey(key)
                GeneratedWalletMaterial(
                    secret = storedSecret,
                    record = WalletRecord(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        classicAddress = AddressCodec.classicAddress(publicKey),
                        publicKey = Bytes.hexEncode(publicKey, upper = true),
                        keyAlgorithm = WalletKeyAlgorithm.SECP256K1,
                        importKind = WalletImportKind.RAW_PRIVATE_KEY_HEX,
                        derivationPath = null,
                        addedAtEpochMs = nowEpochMs,
                        backupStatus = WalletBackupStatus.UNVERIFIED,
                    ),
                )
            }
        }
    }

    private fun materialSecp(
        entropy: ByteArray,
        storedSecret: String,
        importKind: WalletImportKind,
        name: String,
        nowEpochMs: Long,
    ): GeneratedWalletMaterial {
        val privateKey = deriveFamilySeedPrivateKey(entropy, 0)
        val publicKey = Bip32.compressedPublicKey(privateKey)
        return GeneratedWalletMaterial(
            secret = storedSecret,
            record = WalletRecord(
                id = UUID.randomUUID().toString(),
                name = name,
                classicAddress = AddressCodec.classicAddress(publicKey),
                publicKey = Bytes.hexEncode(publicKey, upper = true),
                keyAlgorithm = WalletKeyAlgorithm.SECP256K1,
                importKind = importKind,
                derivationPath = null,
                addedAtEpochMs = nowEpochMs,
                backupStatus = WalletBackupStatus.UNVERIFIED,
            ),
        )
    }

    private fun materialEd25519(
        seed: ByteArray,
        storedSecret: String,
        importKind: WalletImportKind,
        name: String,
        nowEpochMs: Long,
    ): GeneratedWalletMaterial {
        val privateBytes = Bytes.sha512(seed).copyOf(32)
        val publicKey = ed25519PublicKey(privateBytes)
        return GeneratedWalletMaterial(
            secret = storedSecret,
            record = WalletRecord(
                id = UUID.randomUUID().toString(),
                name = name,
                classicAddress = AddressCodec.classicAddress(publicKey),
                publicKey = Bytes.hexEncode(publicKey, upper = true),
                keyAlgorithm = WalletKeyAlgorithm.ED25519,
                importKind = importKind,
                derivationPath = null,
                addedAtEpochMs = nowEpochMs,
                backupStatus = WalletBackupStatus.UNVERIFIED,
            ),
        )
    }

    private fun signingKeyFromEntropy(entropy: ByteArray, algorithm: WalletKeyAlgorithm): ByteArray {
        return when (algorithm) {
            WalletKeyAlgorithm.ED25519 -> Bytes.sha512(entropy).copyOf(32)
            WalletKeyAlgorithm.SECP256K1,
            WalletKeyAlgorithm.UNKNOWN,
            -> deriveFamilySeedPrivateKey(entropy, 0)
        }
    }

    fun deriveFamilySeedPrivateKey(entropy: ByteArray, account: Long = 0): ByteArray {
        var privateGenerator: ByteArray? = null
        var index = 0L
        while (index <= 0xFFFFL) {
            val candidate = Bytes.sha512(Bytes.concat(entropy, Bytes.u32be(index))).copyOf(32)
            if (Bip32.isValidPrivateKey(candidate)) {
                privateGenerator = candidate
                break
            }
            index += 1
        }
        val generator = privateGenerator ?: throw KeyEngineException("invalidDerivation")
        val publicGenerator = Bip32.compressedPublicKey(generator)
        index = 0L
        while (index <= 0xFFFFL) {
            val tweak = Bytes.sha512(
                Bytes.concat(publicGenerator, Bytes.u32be(account), Bytes.u32be(index)),
            ).copyOf(32)
            if (!Bip32.isValidPrivateKey(tweak)) {
                index += 1
                continue
            }
            val accountKey = Bip32.addScalar(generator, tweak)
            if (accountKey != null) return accountKey
            index += 1
        }
        throw KeyEngineException("invalidDerivation")
    }

    private fun ed25519PublicKey(seed32: ByteArray): ByteArray {
        val privateKey = Ed25519PrivateKeyParameters(seed32, 0)
        return byteArrayOf(0xED.toByte()) + privateKey.generatePublicKey().encoded
    }

    private fun requireName(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) throw KeyEngineException("invalidName")
        return clean
    }

    companion object {
        val ED25519_SEED_PREFIX = byteArrayOf(0x01, 0xE1.toByte(), 0x4B)
        val SECP_SEED_PREFIX = byteArrayOf(0x21)
        const val DEFAULT_XRPL_PATH = "m/44'/144'/0'/0/0"
    }
}

internal data class WalletSecretEnvelope(
    val version: Int,
    val phrase: String,
    val passphrase: String,
) {
    companion object {
        fun encode(phrase: String, passphrase: String): String =
            """{"version":1,"phrase":${jsonString(phrase)},"passphrase":${jsonString(passphrase)}}"""

        fun decode(secret: String): WalletSecretEnvelope? {
            val trimmed = secret.trim()
            if (!trimmed.startsWith("{") || !trimmed.contains("\"phrase\"")) return null
            val version = jsonInt(trimmed, "version") ?: return null
            val phrase = jsonField(trimmed, "phrase") ?: return null
            val passphrase = jsonField(trimmed, "passphrase") ?: return null
            if (version != 1) return null
            return WalletSecretEnvelope(version, phrase, passphrase)
        }

        private fun jsonString(value: String): String = buildString {
            append('"')
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
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
                        when (val n = json[i + 1]) {
                            '"', '\\', '/' -> out.append(n)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            else -> out.append(n)
                        }
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
}
