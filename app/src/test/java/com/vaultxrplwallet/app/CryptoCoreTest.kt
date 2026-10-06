package com.vaultxrplwallet.app

import com.vaultxrplwallet.app.core.crypto.AddressCodec
import com.vaultxrplwallet.app.core.crypto.BackupCrypto
import com.vaultxrplwallet.app.core.crypto.BackupCryptoException
import com.vaultxrplwallet.app.core.crypto.Base58Xrp
import com.vaultxrplwallet.app.core.crypto.Bip39
import com.vaultxrplwallet.app.core.crypto.Bytes
import com.vaultxrplwallet.app.core.crypto.KeyEngine
import com.vaultxrplwallet.app.core.crypto.KeyEngineException
import com.vaultxrplwallet.app.core.crypto.SeedCodec
import com.vaultxrplwallet.app.core.crypto.WalletImportKind
import com.vaultxrplwallet.app.core.crypto.WalletKeyAlgorithm
import com.vaultxrplwallet.app.core.crypto.XrplSecretFormat
import com.vaultxrplwallet.app.core.stake.FlareEpochDates
import com.vaultxrplwallet.app.core.stake.FlareMemoCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CryptoCoreTest {
    private val engine = KeyEngine()

    @Test
    fun base58RoundtripKnownPayload() {
        val payload = byteArrayOf(
            0x00,
            0x4C, 0x3A, 0x1D, 0x21, 0x3F.toByte(), 0xBD.toByte(), 0xFB.toByte(), 0x14,
            0xC7.toByte(), 0xC2.toByte(), 0x8D.toByte(), 0x60, 0x94.toByte(), 0x69, 0xB3.toByte(), 0x41,
            0x11, 0x22, 0x33, 0x44,
        )
        val encoded = Base58Xrp.encode(payload)
        val decoded = Base58Xrp.decode(encoded)
        assertNotNull(decoded)
        assertArrayEquals(payload, decoded)
        assertTrue(encoded.all { it in Base58Xrp.ALPHABET })
    }

    @Test
    fun ed25519FamilySeedVectors() {
        val zero = ByteArray(16)
        val high = ByteArray(16) { 0xFF.toByte() }
        val arbitrary = byteArrayOf(
            0x4C, 0x3A, 0x1D, 0x21, 0x3F, 0xBD.toByte(), 0xFB.toByte(), 0x14,
            0xC7.toByte(), 0xC2.toByte(), 0x8D.toByte(), 0x60, 0x94.toByte(), 0x69, 0xB3.toByte(), 0x41,
        )
        assertEquals("sEdSJHS4oiAdz7w2X2ni1gFiqtbJHqE", engine.encodeEd25519Seed(zero))
        assertEquals("sEdV19BLfeQeKdEXyYA4NhjPJe6XBfG", engine.encodeEd25519Seed(high))
        assertEquals("sEdTM1uX8pu2do5XvTnutH6HsouMaM2", engine.encodeEd25519Seed(arbitrary))
    }

    @Test
    fun generateEd25519ClassicAddressIsValid() {
        val material = engine.generateEd25519("Primary")
        assertTrue(material.secret.startsWith("sEd"))
        assertTrue(material.record.classicAddress.startsWith("r"))
        assertTrue(AddressCodec.isValidClassicAddress(material.record.classicAddress))
        assertEquals(WalletKeyAlgorithm.ED25519, material.record.keyAlgorithm)
        val imported = engine.importEd25519(material.secret, "Imported")
        assertEquals(material.record.classicAddress, imported.record.classicAddress)
        assertEquals(material.record.publicKey, imported.record.publicKey)
    }

    @Test
    fun familySeedEncodeDecodeReimportsSameAddress() {
        val material = engine.generateEd25519("Vault")
        assertTrue(material.secret.startsWith("sEd"))
        val again = engine.importSeedMaterial(
            material.secret,
            XrplSecretFormat.FAMILY_SEED,
            WalletKeyAlgorithm.ED25519,
            "Again",
        )
        assertEquals(material.record.classicAddress, again.record.classicAddress)
        assertEquals(WalletImportKind.ED25519_FAMILY_SEED, again.record.importKind)
    }

    @Test
    fun invalidEd25519ChecksumIsRejected() {
        try {
            engine.importEd25519("sEdTM1uX8pu2do5XvTnutH6HsouMaM3", "Invalid")
            fail("checksum should fail")
        } catch (_: KeyEngineException) {
        } catch (_: Exception) {
        }
    }

    @Test
    fun bip39KnownVectorDerivesCanonicalXrplAccount() {
        val phrase =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        assertTrue(Bip39.validate(phrase))
        val candidates = engine.deriveMnemonicCandidates(phrase, 1)
        assertEquals(1, candidates.size)
        assertEquals("m/44'/144'/0'/0/0", candidates[0].path)
        assertEquals("rHsMGQEkVNJmpGWs8XUBoTBiAAbwxZN5v3", candidates[0].classicAddress)
        assertTrue(AddressCodec.isValidClassicAddress(candidates[0].classicAddress))
    }

    @Test
    fun bip39GenerateValidateAndDeterministicSeed() {
        val entropy = ByteArray(32) { it.toByte() }
        val phrase = Bip39.fromEntropy(entropy)
        assertEquals(24, phrase.split(" ").size)
        assertTrue(Bip39.validate(phrase))
        val seedA = Bip39.toSeed(phrase, "")
        val seedB = Bip39.toSeed("  $phrase  ", "")
        assertEquals(64, seedA.size)
        assertArrayEquals(seedA, seedB)
        assertFalse(Bip39.validate(phrase.split(" ").dropLast(1).joinToString(" ") + " abandon"))
    }

    @Test
    fun bip39InvalidChecksumIsRejected() {
        val phrase =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
        assertFalse(Bip39.validate(phrase))
        try {
            engine.deriveMnemonicCandidates(phrase, 1)
            fail("invalid mnemonic should throw")
        } catch (_: KeyEngineException) {
        }
    }

    @Test
    fun secretNumbersGenesisEncodeDecode() {
        val numbers = "570521-598543-265488-209520-212450-201006-286214-581400"
        val entropy = SeedCodec.decodeSecretNumbers(numbers)
        assertEquals("DEDCE9CE67B451D852FD4E846FCDE31C", Bytes.hexEncode(entropy))
        assertEquals(numbers, SeedCodec.encodeSecretNumbers(entropy))
        val rebuilt = SeedCodec.decodeSecretNumbers(SeedCodec.encodeSecretNumbers(entropy))
        assertArrayEquals(entropy, rebuilt)
    }

    @Test
    fun secretNumbersImportGenesisAddress() {
        val material = engine.importSeedMaterial(
            "570521-598543-265488-209520-212450-201006-286214-581400",
            XrplSecretFormat.SECRET_NUMBERS,
            WalletKeyAlgorithm.SECP256K1,
            "Genesis",
        )
        assertEquals("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh", material.record.classicAddress)
        assertEquals(WalletImportKind.SECRET_NUMBERS, material.record.importKind)
        assertEquals(WalletKeyAlgorithm.SECP256K1, material.record.keyAlgorithm)
    }

    @Test
    fun passphraseSeedIsFirst16OfSha512() {
        val entropy = SeedCodec.seedFromPassphrase("masterpassphrase")
        assertEquals("DEDCE9CE67B451D852FD4E846FCDE31C", Bytes.hexEncode(entropy))
        assertArrayEquals(Bytes.sha512("masterpassphrase".toByteArray()).copyOf(16), entropy)
    }

    @Test
    fun seedHexAndPassphraseYieldGenesis() {
        val fromHex = engine.importSeedMaterial(
            "DEDCE9CE67B451D852FD4E846FCDE31C",
            XrplSecretFormat.SEED_HEX,
            WalletKeyAlgorithm.SECP256K1,
            "Hex",
        )
        val fromPhrase = engine.importSeedMaterial(
            "masterpassphrase",
            XrplSecretFormat.PASSPHRASE,
            WalletKeyAlgorithm.SECP256K1,
            "Pass",
        )
        assertEquals("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh", fromHex.record.classicAddress)
        assertEquals(fromHex.record.classicAddress, fromPhrase.record.classicAddress)
        assertEquals(WalletImportKind.SEED_HEX, fromHex.record.importKind)
        assertEquals(WalletImportKind.PASSPHRASE, fromPhrase.record.importKind)
    }

    @Test
    fun rfc1751GenesisVector() {
        val words = "I IRE BOND BOW TRIO LAID SEAT GOAL HEN IBIS IBIS DARE"
        val entropy = SeedCodec.decodeRfc1751(words)
        assertEquals("DEDCE9CE67B451D852FD4E846FCDE31C", Bytes.hexEncode(entropy))
        val material = engine.importSeedMaterial(
            words,
            XrplSecretFormat.RFC1751,
            WalletKeyAlgorithm.SECP256K1,
            "Genesis",
        )
        assertEquals("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh", material.record.classicAddress)
        assertEquals(WalletImportKind.RFC1751, material.record.importKind)
    }

    @Test
    fun secp256k1FamilySeedGenesis() {
        val material = engine.importSecp256k1("snoPBrXtMeMyMHUVTgbuqAfg1SUTb", "Genesis")
        assertEquals("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh", material.record.classicAddress)
        assertEquals(
            "0330E7FC9D56BB25D6893BA3F317AE5BCF33B3291BD63DB32654A313222F7FD020",
            material.record.publicKey,
        )
    }

    @Test
    fun classicAndXAddressVectors() {
        assertTrue(AddressCodec.isValidClassicAddress("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh"))
        assertTrue(AddressCodec.isValidClassicAddress("rHsMGQEkVNJmpGWs8XUBoTBiAAbwxZN5v3"))
        assertFalse(AddressCodec.isValidClassicAddress("rHsMGQEkVNJmpGWs8XUBoTBiAAbwxZN5v4"))
        assertFalse(AddressCodec.isValidClassicAddress("not-an-xrpl-address"))

        val mainnet = AddressCodec.decodeXAddress("XVPcpSm47b1CZkf5AkKM9a84dQHe3mTAxgxfLw2qYoe7Boa")
        assertNotNull(mainnet)
        assertEquals("rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh", mainnet!!.classicAddress)
        assertEquals(12345L, mainnet.destinationTag)
        assertFalse(mainnet.isTestNetwork)

        val testnet = AddressCodec.decodeXAddress("TVK3SYvMLZR6rEtLDZh3saYHaqFSeMfyuiuZdqF49Mhpsws")
        assertNotNull(testnet)
        assertEquals(mainnet.classicAddress, testnet!!.classicAddress)
        assertEquals(12345L, testnet.destinationTag)
        assertTrue(testnet.isTestNetwork)
    }

    @Test
    fun backupCryptoRoundtrip() {
        val plaintext = """{"wallets":[{"name":"Primary"}]}""".toByteArray()
        val password = "correct horse battery"
        val envelope = BackupCrypto.export(plaintext, password)
        val opened = BackupCrypto.open(envelope, password)
        assertArrayEquals(plaintext, opened)
        try {
            BackupCrypto.open(envelope, "incorrect password")
            fail("wrong password should fail")
        } catch (e: BackupCryptoException) {
            assertEquals("authenticationFailed", e.message)
        }
    }

    @Test
    fun pbkdf2MatchesRfcVectors() {
        val first = BackupCrypto.pbkdf2Sha256("password", "salt".toByteArray(), 1, 32)
        val second = BackupCrypto.pbkdf2Sha256("password", "salt".toByteArray(), 2, 32)
        assertEquals(
            "120FB6CFFCF8B32C43E7225256C4F837A86548C92CCC35480805987CB70BE17B",
            Bytes.hexEncode(first),
        )
        assertEquals(
            "AE4D0C95AF6B46D32D0ADFF928F06DD02A303F8EF3C251DFD6E2D85A95474C43",
            Bytes.hexEncode(second),
        )
    }

    @Test
    fun bip39MaterialUsesSecpAndPassphraseEnvelope() {
        val phrase =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val candidate = engine.deriveMnemonicCandidate(phrase, "TREZOR", "m/44'/144'/0'/0/0")
        val material = engine.material(phrase, "TREZOR", candidate, "Phrase")
        assertEquals(WalletKeyAlgorithm.SECP256K1, material.record.keyAlgorithm)
        assertEquals(WalletImportKind.BIP39, material.record.importKind)
        assertTrue(material.secret.contains("\"phrase\""))
        assertTrue(material.secret.contains("\"passphrase\""))
        val key = engine.signingPrivateKey(material.secret, material.record)
        assertEquals(32, key.size)
    }

    @Test
    fun hexHelpersUpperAndLower() {
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals("DEADBEEF", Bytes.hexEncode(data, upper = true))
        assertEquals("deadbeef", Bytes.hexEncode(data, upper = false))
        assertArrayEquals(data, Bytes.hexDecode("0xdead beef"))
        assertEquals(2, Bytes.u16be(0x0102).size)
        assertEquals(4, Bytes.u32be(1).size)
        assertEquals(8, Bytes.u64be(1).size)
        assertEquals(32, Bytes.sha512Half(data).size)
        assertEquals(20, Bytes.ripemd160(data).size)
    }

    @Test
    fun flareMintMemoIs32Bytes() {
        val memo = FlareMemoCodec.mintMemo("0x373d7d201c8134d4a2f7b5c63560da217e3dea28")
        assertEquals(32, memo.size)
        assertTrue(memo.copyOf(8).contentEquals(FlareMemoCodec.DIRECT_MINTING_PREFIX))
        val deposit = FlareMemoCodec.upshiftDeposit(2, 2)
        assertEquals(32, deposit.size)
        assertEquals(0x21.toByte(), deposit[0])
    }

    @Test
    fun flareInstructionMemosMatchIosVectors() {
        val redeem = FlareMemoCodec.upshiftRequestRedeem(10_000_000, 2)
        assertEquals(32, redeem.size)
        assertEquals("2200000000000000009896800000000200000000000000000000000000000000", Bytes.hexEncode(redeem, upper = true))
        val claim = FlareMemoCodec.upshiftClaim(2026_08_15, 2)
        assertEquals(32, claim.size)
        assertEquals("2300000000000000013527CF0000000200000000000000000000000000000000", Bytes.hexEncode(claim, upper = true))
        val fxrp = FlareMemoCodec.fxrpRedeem(2)
        assertEquals(32, fxrp.size)
        assertEquals("0200000000000000000000020000000000000000000000000000000000000000", Bytes.hexEncode(fxrp, upper = true))
    }

    @Test
    fun flareEpochDatesWalkCalendarDays() {
        assertEquals(
            listOf(2026_03_01L, 2026_02_28L, 2026_02_27L),
            FlareEpochDates.precedingDays(2026_03_01L, 3),
        )
        assertTrue(FlareEpochDates.isClaimable(2026_02_28L, 2026_03_01L))
        assertFalse(FlareEpochDates.isClaimable(2026_03_01L, 2026_03_01L))
        val yearWins = FlareMemoCodec.preferredApy(java.math.BigDecimal("0.0107"), java.math.BigDecimal("0.0154"))
        assertEquals(365, yearWins.second)
        assertEquals(java.math.BigDecimal("0.0154"), yearWins.first)
        val monthWins = FlareMemoCodec.preferredApy(java.math.BigDecimal("0.020"), java.math.BigDecimal("0.015"))
        assertEquals(30, monthWins.second)
        assertTrue(FlareEpochDates.isClaimable(2026_03_01L, 2026_03_01L, nowMs = 2_000L, periodEndMs = 1_000L))
        val year = FlareMemoCodec.fromSnapshots(
            listOf(
                1_765_238_400_000L to 1.0,
                1_796_774_400_000L to 1.0148,
            ),
            365,
        )
        assertNotNull(year)
        assertTrue(year!! > 0.01)
    }
}
