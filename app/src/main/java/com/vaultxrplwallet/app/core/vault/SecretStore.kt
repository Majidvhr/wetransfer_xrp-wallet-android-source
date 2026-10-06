package com.vaultxrplwallet.app.core.vault

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SecretStore(private val context: Context) {
    @Volatile var authenticating: Boolean = false
        private set

    private val storeDir = File(context.filesDir, "vault").apply { mkdirs() }
    private val currentAlias = "vaultxrp.master.v2"
    private val legacyAlias = "vaultxrp.master.v1"

    fun save(walletId: String, secret: String) {
        writeSealed(walletId, secret, requireKey(currentAlias, createIfMissing = true))
    }

    fun read(walletId: String): String {
        val file = File(storeDir, "$walletId.bin")
        require(file.exists()) { "itemNotFound" }
        val bytes = file.readBytes()
        existingKey(currentAlias)?.let { key ->
            try {
                return decrypt(key, bytes)
            } catch (_: Exception) {
            }
        }
        val legacy = existingKey(legacyAlias) ?: error("itemNotFound")
        val plain = decrypt(legacy, bytes)
        try {
            writeSealed(walletId, plain, requireKey(currentAlias, createIfMissing = true))
        } catch (_: Exception) {
        }
        return plain
    }

    fun contains(walletId: String): Boolean = File(storeDir, "$walletId.bin").exists()

    fun delete(walletId: String) {
        File(storeDir, "$walletId.bin").delete()
    }

    fun deleteAll() {
        storeDir.listFiles()?.forEach { it.delete() }
    }

    fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticate(activity: FragmentActivity, reason: String) {
        if (!canAuthenticate()) {
            throw IllegalStateException("Set a device screen lock to unlock, sign, reveal, or export secrets.")
        }
        authenticating = true
        try {
            suspendCancellableCoroutine { cont ->
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (cont.isActive) cont.resumeWithException(IllegalStateException(errString.toString()))
                        }
                        override fun onAuthenticationFailed() {}
                    },
                )
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("VaultXRP")
                        .setSubtitle(reason)
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        )
                        .build(),
                )
            }
        } finally {
            authenticating = false
        }
    }

    private fun writeSealed(walletId: String, secret: String, key: SecretKey) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val sealed = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        File(storeDir, "$walletId.bin").writeBytes(cipher.iv + sealed)
    }

    private fun decrypt(key: SecretKey, bytes: ByteArray): String {
        val iv = bytes.copyOf(12)
        val sealed = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(sealed).toString(Charsets.UTF_8)
    }

    private fun existingKey(alias: String): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return ks.getKey(alias, null) as? SecretKey
    }

    private fun requireKey(alias: String, createIfMissing: Boolean): SecretKey {
        existingKey(alias)?.let { return it }
        require(createIfMissing) { "itemNotFound" }
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(
                120,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(120)
        }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(builder.build())
        return gen.generateKey()
    }
}

class NameGenerator {
    enum class Role(val defaultName: String) {
        CREATED("Main Wallet"),
        IMPORTED("Imported Wallet"),
        WATCH_ONLY("Watch-Only Wallet"),
    }

    private val qualifiers = listOf(
        "Azure", "Cobalt", "Onyx", "Ember", "Quartz", "Nova", "Lumen", "Harbor",
        "Summit", "Atlas", "Orbit", "Aurora", "Zenith", "Vertex", "Slate", "Ivory",
        "Beacon", "Granite", "Halcyon", "Meridian", "Solstice", "Cipher", "Obsidian",
        "Sterling", "Cardinal", "Lantern", "Tidal", "Northwind",
    )
    private val subjects = listOf(
        "Vault", "Reserve", "Ledger", "Keep", "Treasury", "Holdings", "Cache",
        "Wallet", "Safe", "Deposit",
    )

    fun suggest(role: Role, existing: List<String>, avoiding: String? = null): String {
        val taken = existing.map { it.trim().lowercase() }.toMutableSet()
        avoiding?.let { taken.add(it.trim().lowercase()) }
        if (role.defaultName.lowercase() !in taken) return role.defaultName
        repeat(48) {
            val candidate = "${qualifiers.random()} ${subjects.random()}"
            if (candidate.lowercase() !in taken) return candidate
        }
        var index = 2
        while ("${role.defaultName} $index".lowercase() in taken) index += 1
        return "${role.defaultName} $index"
    }
}
