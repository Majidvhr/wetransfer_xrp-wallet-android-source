package com.vaultxrplwallet.app.core.xrpl

import com.vaultxrplwallet.app.data.AccountSnapshot
import com.vaultxrplwallet.app.data.ActivityItem
import com.vaultxrplwallet.app.data.TrustLine
import com.vaultxrplwallet.app.data.WalletLedgerSnapshot
import com.vaultxrplwallet.app.data.XrplPaymentPreparation
import com.vaultxrplwallet.app.data.XrplSubmissionOutcome
import com.vaultxrplwallet.app.data.XrpAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class XrplService(
    private val endpoints: List<String> = listOf(
        "https://xrplcluster.com",
        "https://s1.ripple.com:51234",
        "https://s2.ripple.com:51234",
    ),
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private var lastServer: ServerState? = null

    data class ServerState(
        val reserveBaseXRP: BigDecimal,
        val reserveIncXRP: BigDecimal,
        val feeDrops: Long,
        val ledgerIndex: Long,
    )

    suspend fun fetchWallet(address: String): WalletLedgerSnapshot = withContext(Dispatchers.IO) {
        val account = fetchAccount(address)
        val lines = fetchLines(address)
        val activity = fetchActivity(address)
        val server = resolvedServerState()
        val fee = resolvedFeeDrops(allowEscalated = true)
        WalletLedgerSnapshot(account, lines, activity, server.reserveBaseXRP, server.reserveIncXRP, fee, System.currentTimeMillis())
    }

    suspend fun preparePayment(source: String, destination: String): XrplPaymentPreparation = withContext(Dispatchers.IO) {
        val sourceAccount = fetchAccount(source)
        val dest = fetchAccount(destination)
        val server = resolvedServerState()
        val fee = resolvedFeeDrops()
        require(sourceAccount.isFunded) { "sourceAccountUnfunded" }
        XrplPaymentPreparation(
            sourceAccount, dest.isFunded, fee,
            server.reserveBaseXRP, server.reserveIncXRP,
            server.ledgerIndex, server.ledgerIndex + 60,
            dest.requireDestTag,
        )
    }

    suspend fun prepareTrustSet(account: String): XrplPaymentPreparation = withContext(Dispatchers.IO) {
        val sourceAccount = fetchAccount(account)
        val server = resolvedServerState()
        val fee = resolvedFeeDrops()
        require(sourceAccount.isFunded) { "sourceAccountUnfunded" }
        XrplPaymentPreparation(
            sourceAccount, true, fee,
            server.reserveBaseXRP, server.reserveIncXRP,
            server.ledgerIndex, server.ledgerIndex + 60,
        )
    }

    suspend fun submitAndWait(blobHex: String, hash: String, lastLedgerSequence: Long): XrplSubmissionOutcome =
        withContext(Dispatchers.IO) {
            val submit = try {
                request("submit", JSONObject().put("tx_blob", blobHex).put("fail_hard", false))
            } catch (_: Exception) {
                return@withContext XrplSubmissionOutcome.Unknown(hash)
            }
            val resultCode = submit.optJSONObject("result")?.optString("engine_result").orEmpty()
            // temREDUNDANT means this exact signed blob is already known (often already
            // validated). Poll for confirmation instead of treating it as a new failure.
            if (resultCode != "temREDUNDANT" && (resultCode.startsWith("tem") || resultCode.startsWith("tel"))) {
                return@withContext XrplSubmissionOutcome.Rejected(
                    hash, resultCode,
                    submit.optJSONObject("result")?.optString("engine_result_message")
                        ?: "The ledger rejected this transaction.",
                )
            }
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline) {
                try {
                    quorumValidated(hash)?.let { (code, ledger) ->
                        return@withContext if (code == "tesSUCCESS") {
                            XrplSubmissionOutcome.ValidatedSuccess(hash, ledger)
                        } else {
                            XrplSubmissionOutcome.ValidatedFailure(hash, code, ledger)
                        }
                    }
                    if (quorumLedgerPassed(lastLedgerSequence)) {
                        return@withContext XrplSubmissionOutcome.Expired(hash)
                    }
                } catch (_: Exception) {
                }
                delay(3_000)
            }
            XrplSubmissionOutcome.Unknown(hash)
        }

    private fun fetchAccount(address: String): AccountSnapshot {
        val result = request("account_info", JSONObject().put("account", address).put("ledger_index", "validated"))
            .optJSONObject("result") ?: error("accountInfoFailed")
        val rpcError = result.optString("error")
        if (rpcError == "actNotFound") {
            return AccountSnapshot(XrpAmount(0), 0, 0, false, false, false)
        }
        if (rpcError.isNotEmpty()) error("accountInfoFailed:$rpcError")
        val info = result.optJSONObject("account_data") ?: error("accountInfoFailed")
        val drops = info.optString("Balance", "0").toLongOrNull() ?: 0L
        val flags = info.optLong("Flags")
        return AccountSnapshot(
            XrpAmount(drops),
            info.optLong("OwnerCount"),
            info.optLong("Sequence"),
            flags and 0x00800000L != 0L,
            true,
            flags and 0x00020000L != 0L,
        )
    }

    private fun fetchLines(address: String): List<TrustLine> {
        val result = try {
            request("account_lines", JSONObject().put("account", address).put("ledger_index", "validated"))
                .optJSONObject("result")
        } catch (_: Exception) {
            return emptyList()
        }
        val lines = result?.optJSONArray("lines") ?: return emptyList()
        return (0 until lines.length()).mapNotNull { i ->
            val line = lines.optJSONObject(i) ?: return@mapNotNull null
            TrustLine(
                currency = decodeCurrency(line.optString("currency")),
                rawCurrency = line.optString("currency"),
                issuer = line.optString("account"),
                balance = line.optString("balance").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                limit = line.optString("limit").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                isFrozen = line.optBoolean("freeze") || line.optBoolean("freeze_peer"),
            )
        }
    }

    private fun fetchActivity(address: String): List<ActivityItem> {
        val result = try {
            request(
                "account_tx",
                JSONObject().put("account", address).put("ledger_index_min", -1).put("ledger_index_max", -1).put("limit", 30),
            ).optJSONObject("result")
        } catch (_: Exception) {
            return emptyList()
        }
        val txs = result?.optJSONArray("transactions") ?: return emptyList()
        return (0 until txs.length()).mapNotNull { i ->
            val wrapper = txs.optJSONObject(i) ?: return@mapNotNull null
            val tx = wrapper.optJSONObject("tx") ?: wrapper.optJSONObject("tx_json") ?: return@mapNotNull null
            val meta = wrapper.optJSONObject("meta")
            val hash = tx.optString("hash").ifEmpty { wrapper.optString("hash") }
            val amount = tx.opt("Amount")
            val xrp = if (amount is String) amount.toLongOrNull()?.let { XrpAmount(it) } else null
            ActivityItem(
                id = hash,
                type = tx.optString("TransactionType"),
                account = tx.optString("Account"),
                destination = tx.optString("Destination").ifEmpty { null },
                amount = xrp,
                issuedCurrency = (amount as? JSONObject)?.optString("currency"),
                issuedIssuer = (amount as? JSONObject)?.optString("issuer"),
                issuedValue = (amount as? JSONObject)?.optString("value")?.toBigDecimalOrNull(),
                fee = tx.optString("Fee").toLongOrNull()?.let { XrpAmount(it) },
                ledgerIndex = tx.optLong("ledger_index").takeIf { it > 0 },
                validated = wrapper.optBoolean("validated", meta != null),
            )
        }
    }

    private fun resolvedServerState(): ServerState {
        return try {
            fetchServerState().also { lastServer = it }
        } catch (e: Exception) {
            val cached = lastServer ?: throw e
            cached.copy(ledgerIndex = fetchValidatedLedger())
        }
    }

    private fun resolvedFeeDrops(allowEscalated: Boolean = false): Long {
        val fee = try {
            fetchFeeDrops()
        } catch (e: IllegalStateException) {
            if (e.message?.startsWith("feeTooHigh:") == true) {
                if (allowEscalated) return e.message!!.removePrefix("feeTooHigh:").toLong()
                throw e
            }
            lastServer?.feeDrops ?: throw e
        } catch (e: Exception) {
            lastServer?.feeDrops ?: throw e
        }
        if (!allowEscalated && fee > 10_000) error("feeTooHigh:$fee")
        return fee
    }

    private fun fetchServerState(): ServerState {
        val info = request("server_info", JSONObject()).optJSONObject("result")?.optJSONObject("info")
            ?: error("invalidResponse")
        val networkId = info.opt("network_id")
        require(networkId == null || networkId == 0 || networkId == JSONObject.NULL)
        val validated = info.optJSONObject("validated_ledger") ?: error("invalidResponse")
        val reserveBase = validated.optDouble("reserve_base_xrp", 1.0).toBigDecimal()
        val reserveInc = validated.optDouble("reserve_inc_xrp", 0.2).toBigDecimal()
        val baseFee = validated.optDouble("base_fee_xrp", 0.00001)
        val load = info.optDouble("load_factor", 1.0)
        var fee = (baseFee * load * 1_000_000).toLong().coerceAtLeast(10)
        try {
            fee = fetchFeeDrops()
        } catch (e: IllegalStateException) {
            if (e.message?.startsWith("feeTooHigh:") == true) throw e
        } catch (_: Exception) {
        }
        if (fee > 10_000) error("feeTooHigh:$fee")
        return ServerState(reserveBase, reserveInc, fee, validated.optLong("seq"))
    }

    private fun fetchFeeDrops(): Long {
        val drops = request("fee", JSONObject()).optJSONObject("result")?.optJSONObject("drops")
            ?: error("invalidResponse")
        val open = drops.optString("open_ledger_fee").toLongOrNull() ?: 10
        val min = drops.optString("minimum_fee").toLongOrNull() ?: 10
        val base = drops.optString("base_fee").toLongOrNull() ?: 10
        val fee = maxOf(open, min, base)
        if (fee > 10_000) error("feeTooHigh:$fee")
        return fee
    }

    private fun fetchValidatedLedger(): Long {
        val result = request("ledger", JSONObject().put("ledger_index", "validated")).optJSONObject("result")
        return result?.optLong("ledger_index")?.takeIf { it > 0 }
            ?: result?.optJSONObject("ledger")?.optString("ledger_index")?.toLongOrNull()
            ?: error("invalidResponse")
    }

    private fun quorumValidated(hash: String): Pair<String, Long>? {
        val confirmations = mutableMapOf<String, Pair<Int, Pair<String, Long>>>()
        for (endpoint in endpoints) {
            try {
                val result = request("tx", JSONObject().put("transaction", hash).put("binary", false), endpoint)
                    .optJSONObject("result") ?: continue
                if (result.optString("hash").uppercase() != hash.uppercase()) continue
                if (!result.optBoolean("validated")) continue
                val code = result.optJSONObject("meta")?.optString("TransactionResult") ?: continue
                val ledger = result.optLong("ledger_index")
                if (ledger <= 0) continue
                val key = "$code:$ledger"
                val count = (confirmations[key]?.first ?: 0) + 1
                confirmations[key] = count to (code to ledger)
                if (count >= 2) return code to ledger
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun quorumLedgerPassed(last: Long): Boolean {
        var passed = 0
        for (endpoint in endpoints) {
            try {
                val info = request("server_info", JSONObject(), endpoint)
                    .optJSONObject("result")?.optJSONObject("info") ?: continue
                val seq = info.optJSONObject("validated_ledger")?.optLong("seq") ?: continue
                if (seq > last) {
                    passed += 1
                    if (passed >= 2) return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun request(method: String, params: JSONObject, endpoint: String? = null): JSONObject {
        val body = JSONObject()
            .put("method", method)
            .put("params", JSONArray().put(params))
            .toString()
        val hosts = if (endpoint != null) listOf(endpoint) else endpoints
        var last: Exception? = null
        for (host in hosts) {
            try {
                val req = Request.Builder()
                    .url(host)
                    .post(body.toRequestBody(jsonType))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("server ${resp.code}")
                    return JSONObject(resp.body?.string().orEmpty())
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("offline")
    }

    private fun decodeCurrency(code: String): String {
        if (code.length == 3) return code
        if (code.length == 40) {
            val bytes = com.vaultxrplwallet.app.core.crypto.Bytes.hexDecode(code) ?: return code
            val text = bytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.US_ASCII)
            if (text.isNotEmpty() && text.all { it.code in 0x20..0x7E }) return text
        }
        return code
    }
}
