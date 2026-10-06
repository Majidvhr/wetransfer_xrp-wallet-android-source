package com.vaultxrplwallet.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.vaultxrplwallet.app.core.crypto.WalletBackupStatus
import com.vaultxrplwallet.app.core.crypto.WalletImportKind
import com.vaultxrplwallet.app.core.crypto.WalletKeyAlgorithm
import com.vaultxrplwallet.app.core.crypto.WalletRecord
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("vaultxrp.prefs")

class PreferencesStore(private val context: Context) {
    private val walletsKey = stringPreferencesKey("wallet.metadata.v1")
    private val contactsKey = stringPreferencesKey("vaultxrp.contacts.v1")
    private val appearanceKey = stringPreferencesKey("appearance.preference")
    private val autolockKey = stringPreferencesKey("security.autolock")
    private val hideKey = booleanPreferencesKey("privacy.hideBalances")
    private val pendingKey = stringPreferencesKey("transaction.pending.v1")
    private val stakePendingKey = stringPreferencesKey("stake.pending.unstake.v1")

    suspend fun loadWallets(): WalletCollection {
        val raw = context.dataStore.data.map { it[walletsKey] }.first() ?: return WalletCollection()
        return decodeWallets(raw)
    }

    suspend fun saveWallets(collection: WalletCollection) {
        context.dataStore.edit { it[walletsKey] = encodeWallets(collection) }
    }

    suspend fun clearWallets() {
        context.dataStore.edit { it.remove(walletsKey) }
    }

    suspend fun clearAllLocalData() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun loadContacts(): List<WalletContact> {
        val raw = context.dataStore.data.map { it[contactsKey] }.first() ?: return emptyList()
        return decodeContacts(raw)
    }

    suspend fun saveContacts(contacts: List<WalletContact>) {
        context.dataStore.edit { it[contactsKey] = encodeContacts(contacts) }
    }

    suspend fun appearance(): AppAppearance =
        AppAppearance.entries.firstOrNull { it.name.equals(context.dataStore.data.map { p -> p[appearanceKey] }.first(), true) }
            ?: AppAppearance.SYSTEM

    suspend fun setAppearance(value: AppAppearance) {
        context.dataStore.edit { it[appearanceKey] = value.name.lowercase() }
    }

    suspend fun autoLock(): AutoLockPreference =
        AutoLockPreference.entries.firstOrNull { it.name.equals(context.dataStore.data.map { p -> p[autolockKey] }.first(), true) }
            ?: AutoLockPreference.BACKGROUND

    suspend fun setAutoLock(value: AutoLockPreference) {
        context.dataStore.edit { it[autolockKey] = value.name.lowercase() }
    }

    suspend fun hideBalances(): Boolean = context.dataStore.data.map { it[hideKey] ?: false }.first()
    suspend fun setHideBalances(value: Boolean) { context.dataStore.edit { it[hideKey] = value } }

    suspend fun pending(): PendingSignedTransaction? {
        val raw = context.dataStore.data.map { it[pendingKey] }.first() ?: return null
        return decodePending(raw)
    }

    suspend fun savePending(value: PendingSignedTransaction?) {
        context.dataStore.edit {
            if (value == null) it.remove(pendingKey) else it[pendingKey] = encodePending(value)
        }
    }

    suspend fun loadStakePending(): com.vaultxrplwallet.app.core.stake.StakePendingUnstake? {
        val raw = context.dataStore.data.map { it[stakePendingKey] }.first() ?: return null
        return try {
            val o = JSONObject(raw)
            val pending = com.vaultxrplwallet.app.core.stake.StakePendingUnstake(
                xrplAddress = o.getString("xrplAddress"),
                period = o.getLong("period"),
                amountDrops = o.getLong("amountDrops"),
                createdAtMs = o.getLong("createdAtMs"),
            )
            if (System.currentTimeMillis() - pending.createdAtMs > 14L * 24 * 60 * 60 * 1000) null else pending
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveStakePending(value: com.vaultxrplwallet.app.core.stake.StakePendingUnstake?) {
        context.dataStore.edit {
            if (value == null) {
                it.remove(stakePendingKey)
            } else {
                it[stakePendingKey] = JSONObject()
                    .put("xrplAddress", value.xrplAddress)
                    .put("period", value.period)
                    .put("amountDrops", value.amountDrops)
                    .put("createdAtMs", value.createdAtMs)
                    .toString()
            }
        }
    }

    private fun encodeWallets(c: WalletCollection): String {
        val arr = JSONArray()
        c.wallets.forEach { w ->
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("name", w.name)
                    .put("classicAddress", w.classicAddress)
                    .put("publicKey", w.publicKey)
                    .put("keyAlgorithm", w.keyAlgorithm.name)
                    .put("importKind", w.importKind?.name)
                    .put("derivationPath", w.derivationPath)
                    .put("addedAtEpochMs", w.addedAtEpochMs)
                    .put("backupStatus", w.backupStatus.name),
            )
        }
        return JSONObject().put("wallets", arr).put("activeWalletID", c.activeWalletID).toString()
    }

    private fun decodeWallets(raw: String): WalletCollection {
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("wallets") ?: JSONArray()
        val wallets = (0 until arr.length()).map { i ->
            val w = arr.getJSONObject(i)
            WalletRecord(
                id = w.getString("id"),
                name = w.getString("name"),
                classicAddress = w.getString("classicAddress"),
                publicKey = w.optString("publicKey"),
                keyAlgorithm = WalletKeyAlgorithm.valueOf(w.optString("keyAlgorithm", "UNKNOWN")),
                importKind = w.optString("importKind").takeIf { it.isNotEmpty() }?.let { WalletImportKind.valueOf(it) },
                derivationPath = w.optString("derivationPath").takeIf { it.isNotEmpty() },
                addedAtEpochMs = w.optLong("addedAtEpochMs"),
                backupStatus = WalletBackupStatus.valueOf(w.optString("backupStatus", "UNVERIFIED")),
            )
        }.toMutableList()
        return WalletCollection(wallets, obj.optString("activeWalletID").ifEmpty { wallets.firstOrNull()?.id })
    }

    private fun encodeContacts(contacts: List<WalletContact>): String {
        val arr = JSONArray()
        contacts.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("classicAddress", it.classicAddress)
                    .put("destinationTag", it.destinationTag)
                    .put("note", it.note),
            )
        }
        return arr.toString()
    }

    private fun decodeContacts(raw: String): List<WalletContact> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            WalletContact(
                id = c.getString("id"),
                name = c.getString("name"),
                classicAddress = c.getString("classicAddress"),
                destinationTag = if (c.isNull("destinationTag")) null else c.optLong("destinationTag"),
                note = c.optString("note"),
            )
        }
    }

    private fun encodePending(p: PendingSignedTransaction): String =
        JSONObject()
            .put("kind", p.kind)
            .put("account", p.account)
            .put("hash", p.hash)
            .put("blobHex", p.blobHex)
            .put("lastLedgerSequence", p.lastLedgerSequence)
            .put("createdAtMs", p.createdAtMs)
            .toString()

    private fun decodePending(raw: String): PendingSignedTransaction {
        val o = JSONObject(raw)
        return PendingSignedTransaction(
            o.getString("kind"),
            o.getString("account"),
            o.getString("hash"),
            o.getString("blobHex"),
            o.getLong("lastLedgerSequence"),
            o.getLong("createdAtMs"),
        )
    }
}
