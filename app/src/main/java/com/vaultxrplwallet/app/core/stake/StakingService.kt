package com.vaultxrplwallet.app.core.stake

import com.vaultxrplwallet.app.core.crypto.Bytes
import com.vaultxrplwallet.app.data.PriceChartPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

data class FlarePendingWithdrawal(
    val period: Long,
    val amountDrops: Long,
    val claimable: Boolean,
)

data class StakePendingUnstake(
    val xrplAddress: String,
    val period: Long,
    val amountDrops: Long,
    val createdAtMs: Long,
)

data class FlareStakeSnapshot(
    val apy: BigDecimal?,
    val apy30d: BigDecimal?,
    val apyDays: Int,
    val tvlUsd: BigDecimal?,
    val sharePrice: BigDecimal?,
    val stakedXrp: BigDecimal?,
    val idleFxrp: BigDecimal?,
    val unstakingXrp: BigDecimal?,
    val lotSizeDrops: Long,
    val vaultCapLeft: BigDecimal?,
    val personalAccount: String?,
    val coreVault: String?,
    val operator: String?,
    val unlockMs: Long?,
    val pendingClaimable: Boolean,
    val pendingWithdrawals: List<FlarePendingWithdrawal>,
    val currentPeriod: Long,
    val vaultId: Int,
    val stakedShares: Long,
    val depositFeeDrops: Long,
    val unstakeFeeDrops: Long,
    val claimFeeDrops: Long,
    val redeemFeeDrops: Long,
    val mintFeeBips: Int,
    val minimumMintFeeDrops: Long,
    val executorFeeDrops: Long,
    val positionsResolved: Boolean,
)

class StakingService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()
    private val rpcs = listOf(
        "https://flare-api.flare.network/ext/C/rpc",
        "https://rpc.ankr.com/flare",
    )

    suspend fun fetchSnapshot(xrplAddress: String? = null): FlareStakeSnapshot = withContext(Dispatchers.IO) {
        val vault = try {
            get("https://api.upshift.finance/v1/tokenized_vaults/${FlareMemoCodec.VAULT}")
        } catch (_: Exception) {
            JSONObject()
        }
        val historical = vault.optJSONObject("historical_apy")
        val snapshotPoints = snapshotPoints(vault.optJSONArray("historical_snapshots"))
        val monthApy = historicalApy(historical, 30)
            ?: FlareMemoCodec.fromSnapshots(snapshotPoints, 30)?.toBigDecimal()
        val yearApy = FlareMemoCodec.fromSnapshots(snapshotPoints, 365)?.toBigDecimal()
        val preferred = FlareMemoCodec.preferredApy(monthApy, yearApy)
        val tvl = vault.optDouble("tvl").takeIf { !it.isNaN() }?.toBigDecimal()
            ?: vault.optDouble("latest_reported_tvl").takeIf { !it.isNaN() }?.toBigDecimal()
        val share = vault.optDouble("share_price").takeIf { !it.isNaN() }?.toBigDecimal()
        val controller = contractAddress("MasterAccountController")
        val assetManager = contractAddress("AssetManagerFXRP")
        val personal = xrplAddress?.takeIf { it.isNotBlank() }?.let { address ->
            controller?.let { personalAccount(it, address) }
        }
        val core = assetManager?.let { coreVaultAddress(it) }
        val operator = controller?.let { providerWallets(it).firstOrNull() }
        val lotSize = assetManager?.let { uint256(it, LOT_SIZE) } ?: FlareMemoCodec.DEFAULT_LOT
        val minFee = assetManager?.let { uint256(it, MIN_MINT_FEE) } ?: 100_000L
        val feeBips = assetManager?.let { uint256(it, MINT_FEE_BIPS) } ?: 10L
        val executor = assetManager?.let { uint256(it, EXECUTOR_FEE) } ?: 200_000L
        val depositFee = controller?.let { instructionFee(it, 0x21) } ?: 500_000L
        val unstakeFee = controller?.let { instructionFee(it, 0x22) } ?: 500_000L
        val claimFee = controller?.let { instructionFee(it, 0x23) } ?: 500_000L
        val redeemFee = controller?.let { instructionFee(it, 0x02) } ?: 950_000L
        val maxDepositDrops = uint256(FlareMemoCodec.VAULT, MAX_DEPOSIT_AMOUNT)
        val epoch = withdrawalEpoch(FlareMemoCodec.VAULT)
        val lagSeconds = maxOf(86_400L, uint256(FlareMemoCodec.VAULT, LAG_DURATION))
        val unlockMs = when {
            epoch.second > 1_000_000_000L -> epoch.second * 1000L
            epoch.first > 0L -> System.currentTimeMillis() + lagSeconds * 1000L
            else -> null
        }
        var positionsResolved = false
        var stakedShares = 0L
        var idleDrops = 0L
        var stakedAssets = 0L
        var pending = emptyList<FlarePendingWithdrawal>()
        var onChainShare: BigDecimal? = null
        if (!personal.isNullOrBlank() && personal != ZERO_ADDRESS && assetManager != null) {
            try {
                val fxrp = evmAddress(assetManager, F_ASSET)
                val lp = evmAddress(FlareMemoCodec.VAULT, LP_TOKEN) ?: FlareMemoCodec.VAULT
                if (fxrp != null) {
                    idleDrops = tokenBalance(fxrp, personal)
                    stakedShares = tokenBalance(lp, personal)
                    val price = uint256(FlareMemoCodec.VAULT, GET_SHARE_PRICE)
                    stakedAssets = if (price > 0) stakedShares * price / 1_000_000L else stakedShares
                    if (price > 0) onChainShare = BigDecimal(price).movePointLeft(6)
                    pending = burnableWithdrawals(FlareMemoCodec.VAULT, personal, epoch.first, price, unlockMs)
                    positionsResolved = true
                }
            } catch (_: Exception) {
                positionsResolved = false
            }
        }
        val pendingDrops = pending.sumOf { it.amountDrops }
        FlareStakeSnapshot(
            apy = preferred.first,
            apy30d = monthApy,
            apyDays = preferred.second,
            tvlUsd = tvl,
            sharePrice = onChainShare ?: share,
            stakedXrp = if (positionsResolved) BigDecimal(stakedAssets).movePointLeft(6) else null,
            idleFxrp = if (positionsResolved) BigDecimal(idleDrops).movePointLeft(6) else null,
            unstakingXrp = when {
                pendingDrops > 0L -> BigDecimal(pendingDrops).movePointLeft(6)
                positionsResolved -> BigDecimal.ZERO
                else -> null
            },
            lotSizeDrops = if (lotSize > 0) lotSize else FlareMemoCodec.DEFAULT_LOT,
            vaultCapLeft = if (maxDepositDrops > 0) BigDecimal(maxDepositDrops).movePointLeft(6) else null,
            personalAccount = personal,
            coreVault = core,
            operator = operator,
            unlockMs = unlockMs,
            pendingClaimable = pending.any { it.claimable },
            pendingWithdrawals = pending,
            currentPeriod = epoch.first,
            vaultId = FlareMemoCodec.DEFAULT_VAULT_ID,
            stakedShares = stakedShares,
            depositFeeDrops = if (depositFee > 0) depositFee else 500_000L,
            unstakeFeeDrops = if (unstakeFee > 0) unstakeFee else 500_000L,
            claimFeeDrops = if (claimFee > 0) claimFee else 500_000L,
            redeemFeeDrops = if (redeemFee > 0) redeemFee else 950_000L,
            mintFeeBips = feeBips.toInt().coerceAtLeast(0),
            minimumMintFeeDrops = if (minFee > 0) minFee else 100_000L,
            executorFeeDrops = if (executor > 0) executor else 200_000L,
            positionsResolved = positionsResolved,
        )
    }

    suspend fun fetchTvl(): List<PriceChartPoint> = withContext(Dispatchers.IO) {
        val json = try {
            get("https://api.llama.fi/protocol/upshift")
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        val chainTvls = json.optJSONObject("chainTvls")?.optJSONObject("Flare")?.optJSONArray("tvl")
            ?: json.optJSONArray("tvl")
            ?: return@withContext emptyList()
        val points = (0 until chainTvls.length()).mapNotNull { i ->
            val row = chainTvls.optJSONObject(i) ?: return@mapNotNull null
            PriceChartPoint(row.optLong("date") * 1000, row.optDouble("totalLiquidityUSD"))
        }
        if (points.size <= 120) points else points.filterIndexed { i, _ -> i % (points.size / 120) == 0 }
    }

    private fun ethCallSync(to: String, data: String): String? {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "eth_call")
            .put("params", JSONArray().put(JSONObject().put("to", to).put("data", data)).put("latest"))
            .toString()
        for (rpc in rpcs) {
            try {
                val req = Request.Builder().url(rpc).post(body.toRequestBody(jsonType)).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val result = json.optString("result")
                        if (result.isNotEmpty() && result != "null") return result
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun contractAddress(name: String): String? {
        val result = call(REGISTRY, REGISTRY_NAME, encodeString(name)) ?: return null
        return decodeAddress(result)
    }

    private fun personalAccount(controller: String, xrplAddress: String): String? {
        val result = call(controller, PERSONAL_ACCOUNT, encodeString(xrplAddress)) ?: return null
        return decodeAddress(result)
    }

    private fun coreVaultAddress(assetManager: String): String? {
        val result = call(assetManager, CORE_VAULT) ?: return null
        return decodeString(result).takeIf { it.startsWith("r") }
    }

    private fun providerWallets(controller: String): List<String> {
        val result = call(controller, PROVIDER_WALLETS) ?: return emptyList()
        return decodeStringArray(result)
    }

    private fun uint256(to: String, selector: String): Long {
        val result = call(to, selector) ?: return 0
        return decodeUInt(result, 0)
    }

    private fun evmAddress(to: String, selector: String): String? {
        val result = call(to, selector) ?: return null
        return decodeAddress(result)
    }

    private fun instructionFee(controller: String, id: Long): Long {
        val result = call(controller, INSTRUCTION_FEE, padded32(id)) ?: return 0
        return decodeUInt(result, 0)
    }

    private fun withdrawalEpoch(vault: String): Pair<Long, Long> {
        val result = call(vault, GET_WITHDRAWAL_EPOCH) ?: return 0L to 0L
        val year = decodeUInt(result, 0)
        val month = decodeUInt(result, 32)
        val day = decodeUInt(result, 64)
        val claimableTimestamp = decodeUInt(result, 96)
        return (year * 10_000L + month * 100L + day) to claimableTimestamp
    }

    private fun burnableWithdrawals(
        vault: String,
        accountHex: String,
        currentPeriod: Long,
        sharePrice: Long,
        periodEndMs: Long?,
    ): List<FlarePendingWithdrawal> {
        if (currentPeriod <= 0L) return emptyList()
        val account = try {
            FlareMemoCodec.addressData(accountHex)
        } catch (_: Exception) {
            return emptyList()
        }
        val pending = ArrayList<FlarePendingWithdrawal>()
        for (date in FlareEpochDates.precedingDays(currentPeriod)) {
            val parts = FlareEpochDates.components(date) ?: continue
            val extra = padded32(parts.first) + padded32(parts.second) + padded32(parts.third) +
                ByteArray(32).also { account.copyInto(it, 12) }
            val result = call(vault, GET_BURNABLE_AMOUNT, extra) ?: continue
            val shares = decodeUInt(result, 0)
            if (shares <= 0L) continue
            val assets = redemptionAssets(vault, shares, sharePrice)
            pending.add(
                FlarePendingWithdrawal(
                    date,
                    assets,
                    FlareEpochDates.isClaimable(date, currentPeriod, periodEndMs = periodEndMs),
                ),
            )
        }
        return pending
    }

    private fun redemptionAssets(vault: String, shares: Long, sharePrice: Long): Long {
        val extra = padded32(shares) + padded32(0)
        val result = call(vault, PREVIEW_REDEMPTION, extra)
        if (result != null) {
            val afterFee = decodeUInt(result, 32)
            if (afterFee > 0L) return afterFee
            val beforeFee = decodeUInt(result, 0)
            if (beforeFee > 0L) return beforeFee
        }
        return if (sharePrice > 0L) shares * sharePrice / 1_000_000L else shares
    }

    private fun snapshotPoints(rows: JSONArray?): List<Pair<Long, Double>> {
        if (rows == null) return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val millis = FlareMemoCodec.parseSnapshotMillis(row.optString("snapshot_datetime"))
                ?: return@mapNotNull null
            val ratio = row.optDouble("asset_share_ratio")
            if (ratio.isNaN() || ratio <= 0.0) null else millis to ratio
        }
    }

    private fun historicalApy(obj: JSONObject?, days: Int): BigDecimal? {
        if (obj == null) return null
        for (key in listOf("$days", "${days}d", "${days}D")) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.optDouble(key)
            if (!value.isNaN() && value.isFinite()) return value.toBigDecimal()
        }
        return null
    }

    private fun tokenBalance(token: String, accountHex: String): Long {
        val account = FlareMemoCodec.addressData(accountHex)
        val extra = ByteArray(32)
        account.copyInto(extra, 12)
        val result = call(token, BALANCE_OF, extra) ?: return 0
        return decodeUInt(result, 0)
    }

    private fun call(to: String, selector: String, extra: ByteArray = ByteArray(0)): ByteArray? {
        val data = "0x$selector${Bytes.hexEncode(extra, upper = false)}"
        val result = ethCallSync(to, data) ?: return null
        val hex = result.removePrefix("0x").removePrefix("0X")
        return Bytes.hexDecode(if (hex.length % 2 == 0) hex else "0$hex")
    }

    private fun encodeString(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val pad = (32 - (bytes.size % 32)) % 32
        return padded32(32) + padded32(bytes.size.toLong()) + bytes + ByteArray(pad)
    }

    private fun padded32(value: Long): ByteArray {
        val out = ByteArray(32)
        var remaining = value
        for (index in 31 downTo 24) {
            out[index] = (remaining and 0xFF).toByte()
            remaining = remaining ushr 8
        }
        return out
    }

    private fun decodeUInt(data: ByteArray, offset: Int): Long {
        if (data.size < offset + 32) return 0
        var value = 0L
        for (index in offset + 24 until offset + 32) {
            value = (value shl 8) or (data[index].toLong() and 0xFF)
        }
        return value
    }

    private fun decodeAddress(data: ByteArray): String? {
        val bytes = if (data.size >= 32) data.copyOfRange(data.size - 20, data.size) else data
        if (bytes.all { it == 0.toByte() }) return null
        return "0x" + Bytes.hexEncode(bytes, upper = false)
    }

    private fun decodeString(data: ByteArray): String {
        if (data.size < 64) return ""
        val offset = decodeUInt(data, 0).toInt()
        if (data.size < offset + 32) return ""
        val length = decodeUInt(data, offset).toInt()
        val start = offset + 32
        if (length < 0 || data.size < start + length) return ""
        return String(data.copyOfRange(start, start + length), Charsets.UTF_8)
    }

    private fun decodeStringArray(data: ByteArray): List<String> {
        if (data.size < 64) return emptyList()
        val arrayOffset = decodeUInt(data, 0).toInt()
        if (data.size < arrayOffset + 32) return emptyList()
        val count = decodeUInt(data, arrayOffset).toInt()
        val values = ArrayList<String>()
        for (index in 0 until count) {
            val head = arrayOffset + 32 + index * 32
            if (data.size < head + 32) break
            val relative = decodeUInt(data, head).toInt()
            val stringOffset = arrayOffset + 32 + relative
            if (data.size < stringOffset + 32) break
            val length = decodeUInt(data, stringOffset).toInt()
            val start = stringOffset + 32
            if (data.size < start + length) break
            values.add(String(data.copyOfRange(start, start + length), Charsets.UTF_8))
        }
        return values
    }

    private fun get(url: String): JSONObject {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("unavailable")
            return JSONObject(resp.body?.string().orEmpty())
        }
    }

    companion object {
        private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
        private const val REGISTRY = "0xaD67FE66660Fb8dFE9d6b1b4240d8650e30F6019"
        private const val REGISTRY_NAME = "82760fca"
        private const val PERSONAL_ACCOUNT = "d09318d4"
        private const val PROVIDER_WALLETS = "76a940e5"
        private const val CORE_VAULT = "020c8cd8"
        private const val LOT_SIZE = "4942f65f"
        private const val MIN_MINT_FEE = "b59c4d93"
        private const val MINT_FEE_BIPS = "8369ae6f"
        private const val EXECUTOR_FEE = "d561f156"
        private const val F_ASSET = "7c7db1a5"
        private const val LP_TOKEN = "f5ae497a"
        private const val BALANCE_OF = "70a08231"
        private const val GET_SHARE_PRICE = "5b1dac60"
        private const val INSTRUCTION_FEE = "9c8ccb34"
        private const val MAX_DEPOSIT_AMOUNT = "8ed83271"
        private const val GET_WITHDRAWAL_EPOCH = "ca55a557"
        private const val LAG_DURATION = "24e86d67"
        private const val GET_BURNABLE_AMOUNT = "be1f9214"
        private const val PREVIEW_REDEMPTION = "b4db4be8"
    }
}
