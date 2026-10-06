package com.vaultxrplwallet.app.data

import com.vaultxrplwallet.app.core.crypto.AddressCodec
import com.vaultxrplwallet.app.core.crypto.DerivedWalletCandidate
import com.vaultxrplwallet.app.core.crypto.GeneratedWalletMaterial
import com.vaultxrplwallet.app.core.crypto.WalletBackupStatus
import com.vaultxrplwallet.app.core.crypto.WalletImportKind
import com.vaultxrplwallet.app.core.crypto.WalletKeyAlgorithm
import com.vaultxrplwallet.app.core.crypto.WalletRecord
import java.math.BigDecimal
import java.util.UUID

typealias AppWalletRecord = WalletRecord
typealias AppGeneratedWallet = GeneratedWalletMaterial
typealias AppDerivedCandidate = DerivedWalletCandidate

enum class NetworkEnvironment(val displayName: String, val explorerBase: String) {
    MAINNET("Mainnet", "https://livenet.xrpl.org"),
    TESTNET("Testnet", "https://testnet.xrpl.org"),
    DEVNET("Devnet", "https://devnet.xrpl.org"),
}

enum class AppAppearance(val title: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class AutoLockPreference(val title: String, val intervalMs: Long?) {
    BACKGROUND("When app closes", null),
    ONE_MINUTE("After 1 minute", 60_000),
    FIVE_MINUTES("After 5 minutes", 300_000),
}

enum class AppTab(val label: String) {
    WALLET("Wallet"),
    TOKENS("Tokens"),
    ACTIVITY("Activity"),
    STAKE("Stake"),
    EXPLORE("Explore"),
    SETTINGS("Settings"),
}

data class WalletCollection(
    var wallets: MutableList<WalletRecord> = mutableListOf(),
    var activeWalletID: String? = null,
) {
    val active: WalletRecord? get() = wallets.firstOrNull { it.id == activeWalletID } ?: wallets.firstOrNull()

    fun add(wallet: WalletRecord) {
        require(wallets.none { it.classicAddress == wallet.classicAddress }) { "duplicateAddress" }
        wallets.add(wallet)
        activeWalletID = wallet.id
    }

    fun remove(id: String) {
        wallets.removeAll { it.id == id }
        if (activeWalletID == id) {
            activeWalletID = wallets.minByOrNull { it.addedAtEpochMs }?.id
        }
    }
}

data class WalletContact(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var classicAddress: String,
    var destinationTag: Long? = null,
    var note: String = "",
)

data class XrpAmount(val drops: Long) {
    val xrp: BigDecimal get() = BigDecimal(drops).movePointLeft(6)

    fun formatted(): String = xrp.stripTrailingZeros().toPlainString()

    companion object {
        fun parse(text: String): XrpAmount {
            val xrp = BigDecimal(text.trim())
            require(xrp > BigDecimal.ZERO)
            val scaled = xrp.movePointRight(6)
            require(scaled.scale() <= 0 || scaled.stripTrailingZeros().scale() <= 0) {
                "tooManyDecimalPlaces"
            }
            val drops = scaled.setScale(0).longValueExact()
            require(drops > 0 && drops <= 100_000_000_000_000_000L)
            return XrpAmount(drops)
        }
    }
}

data class AccountSnapshot(
    val balance: XrpAmount,
    val ownerCount: Long,
    val sequence: Long,
    val defaultRipple: Boolean,
    val isFunded: Boolean,
    val requireDestTag: Boolean = false,
)

data class TrustLine(
    val currency: String,
    val rawCurrency: String,
    val issuer: String,
    val balance: BigDecimal,
    val limit: BigDecimal,
    val isFrozen: Boolean,
) {
    val id: String get() = "$currency:$issuer"
}

data class ActivityItem(
    val id: String,
    val type: String,
    val account: String,
    val destination: String?,
    val amount: XrpAmount?,
    val issuedCurrency: String?,
    val issuedIssuer: String?,
    val issuedValue: BigDecimal?,
    val fee: XrpAmount?,
    val ledgerIndex: Long?,
    val validated: Boolean,
)

data class WalletLedgerSnapshot(
    val account: AccountSnapshot,
    val tokens: List<TrustLine>,
    val activity: List<ActivityItem>,
    val reserveBaseXRP: BigDecimal,
    val reserveIncXRP: BigDecimal,
    val feeDrops: Long,
    val fetchedAtMs: Long,
) {
    val totalXRP: BigDecimal get() = account.balance.xrp
    val reserveXRP: BigDecimal
        get() = if (!account.isFunded) BigDecimal.ZERO
        else reserveBaseXRP + reserveIncXRP * BigDecimal(account.ownerCount)
    val spendableXRP: BigDecimal get() = maxOf(BigDecimal.ZERO, totalXRP - reserveXRP)
}

data class XrplPaymentPreparation(
    val source: AccountSnapshot,
    val destinationIsFunded: Boolean,
    val feeDrops: Long,
    val reserveBaseXRP: BigDecimal,
    val reserveIncXRP: BigDecimal,
    val validatedLedgerIndex: Long,
    val lastLedgerSequence: Long,
    val destinationRequireDestTag: Boolean = false,
) {
    val sourceReserveXRP: BigDecimal
        get() = reserveBaseXRP + reserveIncXRP * BigDecimal(source.ownerCount)
    val spendableDrops: Long
        get() {
            val reserveDrops = sourceReserveXRP.movePointRight(6).setScale(0).longValueExact()
            return maxOf(0, source.balance.drops - reserveDrops - feeDrops)
        }
}

sealed class XrplSubmissionOutcome {
    data class ValidatedSuccess(val hash: String, val ledgerIndex: Long?) : XrplSubmissionOutcome()
    data class ValidatedFailure(val hash: String, val resultCode: String, val ledgerIndex: Long?) : XrplSubmissionOutcome()
    data class Rejected(val hash: String, val resultCode: String, val message: String) : XrplSubmissionOutcome()
    data class Expired(val hash: String) : XrplSubmissionOutcome()
    data class Unknown(val hash: String) : XrplSubmissionOutcome()
}

data class SignedXrplTransaction(
    val blobHex: String,
    val hash: String,
    val signatureHex: String,
)

data class PendingSignedTransaction(
    val kind: String,
    val account: String,
    val hash: String,
    val blobHex: String,
    val lastLedgerSequence: Long,
    val createdAtMs: Long,
)

data class MarketToken(
    val currency: String,
    val issuer: String,
    val name: String,
    val iconURL: String?,
    val alternateIconURL: String?,
    val priceXRP: BigDecimal?,
    val volume24hXRP: BigDecimal,
    val marketCapXRP: BigDecimal?,
    val holders: Int,
    val trustLevel: Int,
    val supply: BigDecimal?,
    val priceUSD: BigDecimal? = null,
    val volume24hUSD: BigDecimal? = null,
    val marketCapUSD: BigDecimal? = null,
) {
    val id: String get() = "$currency:$issuer"
}

data class XrpMarketQuote(
    val priceUSD: BigDecimal,
    val change24hPercent: BigDecimal?,
    val volume24hUSD: BigDecimal?,
    val marketCapUSD: BigDecimal?,
)

data class XrpEcosystemStats(
    val priceUSD: BigDecimal,
    val marketCapUSD: BigDecimal?,
    val fullyDilutedValuationUSD: BigDecimal?,
    val volume24hUSD: BigDecimal?,
    val change24hPercent: BigDecimal?,
    val change7dPercent: BigDecimal?,
    val change30dPercent: BigDecimal?,
    val high24hUSD: BigDecimal?,
    val low24hUSD: BigDecimal?,
    val athUSD: BigDecimal?,
    val athChangePercent: BigDecimal?,
    val atlUSD: BigDecimal?,
    val circulatingSupply: BigDecimal?,
    val totalSupply: BigDecimal?,
    val maxSupply: BigDecimal?,
    val marketCapRank: Int?,
    val homepage: String?,
    val lastUpdatedMs: Long?,
)

const val RLUSD_ISSUER = "rMxCKbEDwqr76QuheSUMdEGf4B9xJ8m5De"

enum class PriceChartRange(val title: String, val days: Int) {
    DAY("1D", 1),
    WEEK("7D", 7),
    MONTH("30D", 30),
    QUARTER("90D", 90),
    HALF_YEAR("6M", 180),
    YEAR("1Y", 365),
}

data class PriceChartPoint(val dateMs: Long, val priceUSD: Double)

data class WalletBackupItem(val record: WalletRecord, val secret: String?)

fun String.truncatedAddress(): String =
    if (length <= 14) this else "${take(7)}…${takeLast(5)}"

fun String.toClassicOrNull(): String? = if (AddressCodec.isValidClassicAddress(this)) this else null
