package com.vaultxrplwallet.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.vaultxrplwallet.app.core.crypto.AddressCodec
import com.vaultxrplwallet.app.core.crypto.BackupCrypto
import com.vaultxrplwallet.app.core.crypto.DerivedWalletCandidate
import com.vaultxrplwallet.app.core.crypto.GeneratedWalletMaterial
import com.vaultxrplwallet.app.core.crypto.KeyEngine
import com.vaultxrplwallet.app.core.crypto.WalletBackupStatus
import com.vaultxrplwallet.app.core.crypto.WalletImportKind
import com.vaultxrplwallet.app.core.crypto.WalletKeyAlgorithm
import com.vaultxrplwallet.app.core.crypto.WalletRecord
import com.vaultxrplwallet.app.core.crypto.XrplSecretFormat
import com.vaultxrplwallet.app.core.market.MarketService
import com.vaultxrplwallet.app.core.stake.FlareMemoCodec
import com.vaultxrplwallet.app.core.stake.FlarePendingWithdrawal
import com.vaultxrplwallet.app.core.stake.FlareStakeSnapshot
import com.vaultxrplwallet.app.core.stake.StakePendingUnstake
import com.vaultxrplwallet.app.core.stake.StakingService
import com.vaultxrplwallet.app.core.vault.NameGenerator
import com.vaultxrplwallet.app.core.vault.SecretStore
import com.vaultxrplwallet.app.core.xrpl.TxCodec
import com.vaultxrplwallet.app.core.xrpl.XrplPayment
import com.vaultxrplwallet.app.core.xrpl.XrplService
import com.vaultxrplwallet.app.core.xrpl.XrplTrustSet
import com.vaultxrplwallet.app.data.AppAppearance
import com.vaultxrplwallet.app.data.AppTab
import com.vaultxrplwallet.app.data.AutoLockPreference
import com.vaultxrplwallet.app.data.MarketToken
import com.vaultxrplwallet.app.data.NetworkEnvironment
import com.vaultxrplwallet.app.data.PendingSignedTransaction
import com.vaultxrplwallet.app.data.PreferencesStore
import com.vaultxrplwallet.app.data.TrustLine
import com.vaultxrplwallet.app.data.PriceChartPoint
import com.vaultxrplwallet.app.data.WalletCollection
import com.vaultxrplwallet.app.data.WalletContact
import com.vaultxrplwallet.app.data.WalletLedgerSnapshot
import com.vaultxrplwallet.app.data.XrpAmount
import com.vaultxrplwallet.app.data.XrpEcosystemStats
import com.vaultxrplwallet.app.data.XrpMarketQuote
import com.vaultxrplwallet.app.data.XrplPaymentPreparation
import com.vaultxrplwallet.app.data.XrplSubmissionOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class AppState { LOADING, ONBOARDING, WALLET }
enum class Sheet { NONE, WALLETS, SEND, RECEIVE, ADD_WALLET, NETWORK, SCAN, ADD_TOKENS, XRP_DETAIL, TOKEN_DETAIL, TRUST, STAKE_REVIEW }

data class StakePlan(
    val action: String,
    val title: String,
    val explanation: String,
    val destination: String,
    val amountDrops: Long,
    val memo: ByteArray,
    val lots: Long,
    val period: Long? = null,
    val netDrops: Long? = null,
    val mintingFeeDrops: Long? = null,
    val executorFeeDrops: Long? = null,
    val personalAccount: String? = null,
)

data class UiState(
    val appState: AppState = AppState.LOADING,
    val isLocked: Boolean = false,
    val showSplash: Boolean = true,
    val privacyShield: Boolean = false,
    val tab: AppTab = AppTab.WALLET,
    val sheet: Sheet = Sheet.NONE,
    val hideBalances: Boolean = false,
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val autoLock: AutoLockPreference = AutoLockPreference.BACKGROUND,
    val wallets: WalletCollection = WalletCollection(),
    val snapshot: WalletLedgerSnapshot? = null,
    val ledgerError: String? = null,
    val refreshing: Boolean = false,
    val xrpMarket: XrpMarketQuote? = null,
    val ecosystem: XrpEcosystemStats? = null,
    val featured: List<MarketToken> = emptyList(),
    val marketError: String? = null,
    val chart: List<PriceChartPoint> = emptyList(),
    val contacts: List<WalletContact> = emptyList(),
    val stake: FlareStakeSnapshot? = null,
    val stakeTvl: List<PriceChartPoint> = emptyList(),
    val stakeError: String? = null,
    val sendDestination: String = "",
    val sendTag: Long? = null,
    val sendAmount: String = "",
    val sendMemo: String = "",
    val sendPrep: XrplPaymentPreparation? = null,
    val sendOutcome: XrplSubmissionOutcome? = null,
    val sendBusy: Boolean = false,
    val selectedToken: MarketToken? = null,
    val stakePlan: StakePlan? = null,
    val stakeWait: String? = null,
    val pendingNotice: String? = null,
    val message: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PreferencesStore(app)
    val secrets = SecretStore(app)
    private val keys = KeyEngine()
    private val xrpl = XrplService()
    private val market = MarketService()
    private val staking = StakingService()
    val names = NameGenerator()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    var lastInteraction = System.currentTimeMillis()
    var backgroundedAt: Long? = null
    private var stakeWaitJob: Job? = null
    val network: NetworkEnvironment = NetworkEnvironment.MAINNET

    val active: WalletRecord? get() = _ui.value.wallets.active

    init {
        viewModelScope.launch { boot() }
    }

    private suspend fun boot() {
        val wallets = prefs.loadWallets()
        val appearance = prefs.appearance()
        val autoLock = prefs.autoLock()
        val hide = prefs.hideBalances()
        val contacts = prefs.loadContacts()
        _ui.update {
            it.copy(
                wallets = wallets,
                appearance = appearance,
                autoLock = autoLock,
                hideBalances = hide,
                contacts = contacts,
                appState = if (wallets.wallets.isEmpty()) AppState.ONBOARDING else AppState.WALLET,
                isLocked = wallets.wallets.isNotEmpty(),
            )
        }
        if (wallets.wallets.isNotEmpty()) {
            resumePending()
            refreshAll()
        }
    }

    private suspend fun resumePending() {
        val pending = prefs.pending() ?: return
        _ui.update {
            it.copy(pendingNotice = "A signed ${pending.kind} is still waiting on the ledger (${pending.hash.take(8)}…). The app will not offer a second payment until this settles.")
        }
        try {
            val outcome = xrpl.submitAndWait(pending.blobHex, pending.hash, pending.lastLedgerSequence)
            if (outcome !is XrplSubmissionOutcome.Unknown) prefs.savePending(null)
            _ui.update {
                it.copy(
                    sendOutcome = outcome,
                    pendingNotice = if (outcome is XrplSubmissionOutcome.Unknown) it.pendingNotice else null,
                )
            }
        } catch (_: Exception) {
        }
    }

    fun dismissSplash() { _ui.update { it.copy(showSplash = false) } }

    fun setPrivacy(visible: Boolean) { _ui.update { it.copy(privacyShield = visible) } }

    fun interact() { lastInteraction = System.currentTimeMillis() }

    @Volatile var awaitingExternalDocument: Boolean = false
        private set
    private var pendingBackupPassword: String? = null
    private var pendingExportBytes: ByteArray? = null

    fun onBackground() {
        if (secrets.authenticating || awaitingExternalDocument) return
        backgroundedAt = System.currentTimeMillis()
        if (_ui.value.autoLock == AutoLockPreference.BACKGROUND && _ui.value.appState == AppState.WALLET) {
            lock()
        }
    }

    fun onForeground() {
        val pref = _ui.value.autoLock
        val bg = backgroundedAt
        if (pref.intervalMs != null && bg != null && System.currentTimeMillis() - bg >= pref.intervalMs) {
            lock()
        }
        backgroundedAt = null
        if (_ui.value.appState == AppState.WALLET && !_ui.value.isLocked) {
            viewModelScope.launch { refreshAll() }
        }
    }

    fun lock() {
        if (_ui.value.appState != AppState.WALLET) return
        _ui.update {
            it.copy(
                isLocked = true,
                sheet = if (it.sendBusy) it.sheet else Sheet.NONE,
                sendPrep = if (it.sendBusy) it.sendPrep else null,
                sendOutcome = if (it.sendBusy) it.sendOutcome else null,
                stakePlan = if (it.sendBusy) it.stakePlan else null,
            )
        }
    }

    fun beginExternalDocument(password: String? = null) {
        pendingBackupPassword = password
        awaitingExternalDocument = true
    }

    fun holdExportBytes(bytes: ByteArray) {
        pendingExportBytes = bytes
        awaitingExternalDocument = true
    }

    fun takeExportBytes(): ByteArray? {
        val bytes = pendingExportBytes
        pendingExportBytes = null
        awaitingExternalDocument = false
        return bytes
    }

    fun consumeBackupPassword(): String {
        val password = pendingBackupPassword.orEmpty()
        pendingBackupPassword = null
        awaitingExternalDocument = false
        return password
    }

    fun cancelExternalDocument() {
        pendingBackupPassword = null
        pendingExportBytes = null
        awaitingExternalDocument = false
    }

    fun unlock() { _ui.update { it.copy(isLocked = false) }; lastInteraction = System.currentTimeMillis() }

    fun selectTab(tab: AppTab) { _ui.update { it.copy(tab = tab) } }

    fun openSheet(sheet: Sheet) { _ui.update { it.copy(sheet = sheet) } }

    fun closeSheet() {
        if (_ui.value.sendBusy) return
        _ui.update { it.copy(sheet = Sheet.NONE, sendOutcome = null, sendPrep = null, stakePlan = null) }
    }

    fun toggleHide() {
        viewModelScope.launch {
            val next = !_ui.value.hideBalances
            prefs.setHideBalances(next)
            _ui.update { it.copy(hideBalances = next) }
        }
    }

    fun setAppearance(value: AppAppearance) {
        viewModelScope.launch {
            prefs.setAppearance(value)
            _ui.update { it.copy(appearance = value) }
        }
    }

    fun setAutoLock(value: AutoLockPreference) {
        viewModelScope.launch {
            prefs.setAutoLock(value)
            _ui.update { it.copy(autoLock = value) }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true, ledgerError = null) }
            val address = active?.classicAddress
            try {
                if (address != null) {
                    val snap = xrpl.fetchWallet(address)
                    _ui.update { it.copy(snapshot = snap) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(ledgerError = e.message ?: "Can't reach the ledger") }
            }
            try {
                val quote = market.fetchXrpMarket()
                val eco = market.fetchXrpEcosystem()
                val tokens = market.fetchFeaturedTokens().map { t ->
                    val px = t.priceXRP
                    t.copy(
                        priceUSD = px?.multiply(quote.priceUSD),
                        volume24hUSD = t.volume24hXRP.multiply(quote.priceUSD),
                        marketCapUSD = t.marketCapXRP?.multiply(quote.priceUSD),
                    )
                }
                val chart = market.fetchXrpChart(7)
                _ui.update { it.copy(xrpMarket = quote, ecosystem = eco, featured = tokens, chart = chart, marketError = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(marketError = e.message ?: "Market data unavailable") }
            }
            try {
                val stake = mergeLocalPending(staking.fetchSnapshot(address), address)
                val tvl = staking.fetchTvl()
                _ui.update { it.copy(stake = stake, stakeTvl = tvl, stakeError = null) }
                resolveStakeWait(stake)
            } catch (e: Exception) {
                _ui.update { it.copy(stakeError = e.message) }
            }
            _ui.update { it.copy(refreshing = false) }
        }
    }

    suspend fun completeCreate(material: GeneratedWalletMaterial) {
        secrets.save(material.record.id, material.secret)
        val collection = prefs.loadWallets()
        collection.add(material.record)
        prefs.saveWallets(collection)
        _ui.update { it.copy(wallets = collection, appState = AppState.WALLET, isLocked = false) }
        refreshAll()
    }

    suspend fun completeWatchOnly(record: WalletRecord) {
        val collection = prefs.loadWallets()
        collection.add(record)
        prefs.saveWallets(collection)
        _ui.update { it.copy(wallets = collection, appState = AppState.WALLET, isLocked = false) }
        refreshAll()
    }

    fun activateWallet(id: String) {
        viewModelScope.launch {
            val c = _ui.value.wallets.copy(wallets = _ui.value.wallets.wallets.toMutableList(), activeWalletID = id)
            prefs.saveWallets(c)
            _ui.update { it.copy(wallets = c, sheet = Sheet.NONE) }
            refreshAll()
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            val c = _ui.value.wallets
            c.wallets = c.wallets.map { if (it.id == id) it.copy(name = name.trim()) else it }.toMutableList()
            prefs.saveWallets(c)
            _ui.update { it.copy(wallets = c) }
        }
    }

    suspend fun deleteWallet(id: String) {
        val wallet = _ui.value.wallets.wallets.first { it.id == id }
        if (!wallet.isWatchOnly) secrets.delete(id)
        val c = _ui.value.wallets
        c.remove(id)
        prefs.saveWallets(c)
        _ui.update {
            it.copy(
                wallets = c,
                appState = if (c.wallets.isEmpty()) AppState.ONBOARDING else AppState.WALLET,
                sheet = Sheet.NONE,
            )
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun deleteAll() {
        secrets.deleteAll()
        prefs.clearAllLocalData()
        getApplication<Application>().imageLoader.memoryCache?.clear()
        getApplication<Application>().imageLoader.diskCache?.clear()
        _ui.update { UiState(appState = AppState.ONBOARDING) }
    }

    fun setSend(dest: String = _ui.value.sendDestination, amount: String = _ui.value.sendAmount, tag: Long? = _ui.value.sendTag, memo: String = _ui.value.sendMemo) {
        _ui.update { it.copy(sendDestination = dest, sendAmount = amount, sendTag = tag, sendMemo = memo) }
    }

    fun applyScan(raw: String) {
        val parsed = AddressCodec.parsePaymentTarget(raw) ?: return
        _ui.update {
            it.copy(
                sendDestination = parsed.first,
                sendTag = parsed.second,
                sheet = Sheet.SEND,
            )
        }
    }

    fun openXrpDetail() {
        _ui.update { it.copy(sheet = Sheet.XRP_DETAIL, selectedToken = null) }
    }

    fun openToken(token: MarketToken) {
        _ui.update { it.copy(sheet = Sheet.TOKEN_DETAIL, selectedToken = token) }
    }

    fun openIssuedLine(line: TrustLine) {
        val token = _ui.value.featured.firstOrNull { it.currency == line.currency && it.issuer == line.issuer }
            ?: MarketToken(
                currency = line.currency,
                issuer = line.issuer,
                name = line.currency,
                iconURL = null,
                alternateIconURL = null,
                priceXRP = null,
                volume24hXRP = java.math.BigDecimal.ZERO,
                marketCapXRP = null,
                holders = 0,
                trustLevel = 0,
                supply = null,
            )
        openToken(token)
    }

    fun loadChart(days: Int) {
        viewModelScope.launch {
            try {
                _ui.update { it.copy(chart = market.fetchXrpChart(days)) }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun reviewSend(): String? {
        val wallet = active ?: return "No wallet"
        if (wallet.isWatchOnly) return "Watch-only wallets cannot send. Import the signing secret first."
        if (prefs.pending() != null) return "A previous signed transaction is still pending. Wait for it to finish."
        val destIn = _ui.value.sendDestination.trim()
        val parsed = AddressCodec.parseDestination(destIn)
            ?: return "Enter a Mainnet classic address (r…) or Mainnet X-address. Testnet addresses are rejected."
        val tagText = _ui.value.sendTag
        if (tagText != null && tagText !in 0L..0xFFFF_FFFFL) {
            return "Destination tag must be an unsigned 32-bit integer (0–4294967295)."
        }
        val amount = try { XrpAmount.parse(_ui.value.sendAmount) } catch (_: Exception) { return "Enter a valid XRP amount" }
        val prep = try { xrpl.preparePayment(wallet.classicAddress, parsed.first) } catch (e: Exception) { return e.message }
        val destTag = parsed.second ?: tagText
        if (prep.destinationRequireDestTag && destTag == null) {
            return "This destination requires a destination tag. Payments without a tag are irreversible losses at exchanges."
        }
        if (!prep.destinationIsFunded) {
            val reserveDrops = prep.reserveBaseXRP.movePointRight(6).setScale(0).longValueExact()
            if (amount.drops < reserveDrops) {
                return "This address is unfunded. Send at least ${prep.reserveBaseXRP.stripTrailingZeros().toPlainString()} XRP to activate it."
            }
        }
        if (amount.drops > prep.spendableDrops) return "Amount exceeds spendable XRP"
        _ui.update { it.copy(sendPrep = prep, sendDestination = parsed.first, sendTag = destTag) }
        return null
    }

    suspend fun submitSend(secret: String): XrplSubmissionOutcome = withContext(Dispatchers.IO) {
        val wallet = active ?: error("no wallet")
        require(!wallet.isWatchOnly) { "watchOnly" }
        require(prefs.pending() == null) { "pending" }
        _ui.update { it.copy(sendBusy = true) }
        try {
            val dest = _ui.value.sendDestination
            val amount = XrpAmount.parse(_ui.value.sendAmount)
            val tag = _ui.value.sendTag
            if (tag != null && tag !in 0L..0xFFFF_FFFFL) error("invalidDestinationTag")
            val prep = xrpl.preparePayment(wallet.classicAddress, dest)
            if (prep.destinationRequireDestTag && tag == null) error("destinationTagRequired")
            if (!prep.destinationIsFunded) {
                val reserveDrops = prep.reserveBaseXRP.movePointRight(6).setScale(0).longValueExact()
                require(amount.drops >= reserveDrops) { "unfundedReserve" }
            }
            require(amount.drops <= prep.spendableDrops) { "amountExceedsSpendable" }
            val payment = XrplPayment(
                account = wallet.classicAddress,
                destination = dest,
                amountDrops = amount.drops,
                feeDrops = prep.feeDrops,
                sequence = prep.source.sequence,
                lastLedgerSequence = prep.lastLedgerSequence,
                destinationTag = tag,
                memo = _ui.value.sendMemo.ifBlank { null },
                binaryMemo = null,
            )
            val signed = TxCodec.signPayment(payment, wallet, secret)
            prefs.savePending(
                PendingSignedTransaction("xrpPayment", wallet.classicAddress, signed.hash, signed.blobHex, prep.lastLedgerSequence, System.currentTimeMillis()),
            )
            val outcome = xrpl.submitAndWait(signed.blobHex, signed.hash, prep.lastLedgerSequence)
            if (outcome !is XrplSubmissionOutcome.Unknown) prefs.savePending(null)
            _ui.update { it.copy(sendOutcome = outcome, sendPrep = prep) }
            refreshAll()
            outcome
        } finally {
            _ui.update { it.copy(sendBusy = false) }
        }
    }

    suspend fun submitTrust(currency: String, issuer: String, activate: Boolean, secret: String, rawCurrency: String? = null): XrplSubmissionOutcome =
        withContext(Dispatchers.IO) {
            val wallet = active ?: error("no wallet")
            require(!wallet.isWatchOnly) { "watchOnly" }
            require(prefs.pending() == null) { "pending" }
            val encoded = TxCodec.resolveCurrencyCode(currency, rawCurrency)
            val prep = xrpl.prepareTrustSet(wallet.classicAddress)
            val trust = XrplTrustSet(
                account = wallet.classicAddress,
                currency = encoded,
                issuer = issuer,
                limit = if (activate) "1000000000" else "0",
                feeDrops = prep.feeDrops,
                sequence = prep.source.sequence,
                lastLedgerSequence = prep.lastLedgerSequence,
            )
            val signed = TxCodec.signTrustSet(trust, wallet, secret)
            prefs.savePending(
                PendingSignedTransaction("trustSet", wallet.classicAddress, signed.hash, signed.blobHex, prep.lastLedgerSequence, System.currentTimeMillis()),
            )
            val outcome = xrpl.submitAndWait(signed.blobHex, signed.hash, prep.lastLedgerSequence)
            if (outcome !is XrplSubmissionOutcome.Unknown) prefs.savePending(null)
            refreshAll()
            outcome
        }

    fun prepareStakeMint(lots: Long): String? {
        val wallet = active ?: return "No wallet"
        if (_ui.value.pendingNotice != null) return "A previous signed transaction is still pending. Wait for it to finish."
        if (wallet.isWatchOnly) return "Watch-only wallets cannot stake."
        val stake = _ui.value.stake ?: return "Stake data is still loading. Pull to refresh."
        val dest = stake.coreVault?.takeIf { it.startsWith("r") } ?: return "Core Vault address could not be resolved. Pull to refresh."
        val account = stake.personalAccount?.takeIf { it != "0x0000000000000000000000000000000000000000" }
            ?: return "Flare personal account is not ready for this XRPL address yet."
        val quote = FlareMemoCodec.mintQuote(
            lots,
            stake.lotSizeDrops,
            stake.mintFeeBips,
            stake.minimumMintFeeDrops,
            stake.executorFeeDrops,
        )
        val capDrops = stake.vaultCapLeft?.movePointRight(6)?.toLong()
        if (capDrops != null && capDrops > 0) {
            val depositableLots = FlareMemoCodec.lots(capDrops, stake.lotSizeDrops)
            if (lots > depositableLots) {
                return "This mint exceeds vault capacity. At most $depositableLots lot${if (depositableLots == 1L) "" else "s"} can be deposited right now."
            }
        }
        val spendableDrops = _ui.value.snapshot?.spendableXRP?.movePointRight(6)?.toLong()
        if (spendableDrops != null && quote.grossDrops + stake.depositFeeDrops > spendableDrops) {
            return "This wallet needs more spendable XRP for the mint amount, earnXRP deposit instruction fee, and network fee."
        }
        _ui.update {
            it.copy(
                sheet = Sheet.STAKE_REVIEW,
                stakePlan = StakePlan(
                    action = "mint",
                    title = "Stake XRP",
                    explanation = "This Payment mints FXRP into your Flare personal account. It goes to the FAssets Core Vault with a mint memo. It does not deposit into ${FlareMemoCodec.POOL_LABEL} yet — after Idle FXRP appears, send Deposit remaining FXRP. A validated Payment cannot be reversed.",
                    destination = dest,
                    amountDrops = quote.grossDrops,
                    memo = FlareMemoCodec.mintMemo(account),
                    lots = lots,
                    netDrops = quote.netDrops,
                    mintingFeeDrops = quote.mintingFeeDrops,
                    executorFeeDrops = quote.executorFeeDrops,
                    personalAccount = account,
                ),
            )
        }
        return null
    }

    fun prepareStakeDeposit(): String? {
        val wallet = active ?: return "No wallet"
        if (_ui.value.pendingNotice != null) return "A previous signed transaction is still pending. Wait for it to finish."
        if (wallet.isWatchOnly) return "Watch-only wallets cannot stake."
        val stake = _ui.value.stake ?: return "Stake data is still loading. Pull to refresh."
        if (!stake.positionsResolved) return "On-chain position is unavailable. Deposit is disabled until balances load."
        val idleDrops = stake.idleFxrp?.movePointRight(6)?.toLong() ?: 0L
        val lots = FlareMemoCodec.lots(idleDrops, stake.lotSizeDrops)
        if (lots <= 0L) return "No idle FXRP lots to deposit yet. Wait for the mint to finish, then deposit."
        val dest = stake.operator?.takeIf { it.startsWith("r") } ?: return "Operator address could not be resolved."
        _ui.update {
            it.copy(
                sheet = Sheet.STAKE_REVIEW,
                stakePlan = StakePlan(
                    action = "deposit",
                    title = "Deposit FXRP",
                    explanation = "This Payment tells Flare’s operator to deposit idle FXRP into ${FlareMemoCodec.POOL_LABEL}. You receive ${FlareMemoCodec.POOL_TOKEN} shares in the personal account. Yield is variable. A validated Payment cannot be reversed.",
                    destination = dest,
                    amountDrops = stake.depositFeeDrops,
                    memo = FlareMemoCodec.upshiftDeposit(lots, stake.vaultId),
                    lots = lots,
                    personalAccount = stake.personalAccount,
                ),
            )
        }
        return null
    }

    fun prepareUnstake(): String? {
        val wallet = active ?: return "No wallet"
        if (_ui.value.pendingNotice != null) return "A previous signed transaction is still pending. Wait for it to finish."
        if (wallet.isWatchOnly) return "Watch-only wallets cannot unstake."
        val stake = _ui.value.stake ?: return "Stake data is still loading. Pull to refresh."
        if (!stake.positionsResolved) return "On-chain position is unavailable. Unstake is disabled until balances load."
        if (stake.stakedShares <= 0L) return "No staked position to unstake."
        val dest = stake.operator?.takeIf { it.startsWith("r") } ?: return "Operator address could not be resolved."
        _ui.update {
            it.copy(
                sheet = Sheet.STAKE_REVIEW,
                stakePlan = StakePlan(
                    action = "unstake",
                    title = "Unstake",
                    explanation = "This starts an earnXRP withdrawal and burns vault shares now. After the current vault epoch, Claim unlocked FXRP, then Return FXRP to XRP. VaultXRP does not hold the funds.",
                    destination = dest,
                    amountDrops = stake.unstakeFeeDrops,
                    memo = FlareMemoCodec.upshiftRequestRedeem(stake.stakedShares, stake.vaultId),
                    lots = FlareMemoCodec.lots(stake.stakedXrp?.movePointRight(6)?.toLong() ?: 0L, stake.lotSizeDrops),
                    personalAccount = stake.personalAccount,
                ),
            )
        }
        return null
    }

    fun prepareClaim(): String? {
        val wallet = active ?: return "No wallet"
        if (_ui.value.pendingNotice != null) return "A previous signed transaction is still pending. Wait for it to finish."
        if (wallet.isWatchOnly) return "Watch-only wallets cannot claim."
        val stake = _ui.value.stake ?: return "Stake data is still loading. Pull to refresh."
        val item = stake.pendingWithdrawals.firstOrNull { it.claimable }
            ?: return "No unlocked withdrawal to claim yet. Unstaking waits one vault epoch."
        val dest = stake.operator?.takeIf { it.startsWith("r") } ?: return "Operator address could not be resolved."
        _ui.update {
            it.copy(
                sheet = Sheet.STAKE_REVIEW,
                stakePlan = StakePlan(
                    action = "claim",
                    title = "Claim unlocked FXRP",
                    explanation = "This Payment asks the Flare operator to release FXRP from the finished earnXRP epoch into your personal account. After it validates, use Return FXRP to XRP to send XRP back to this address. VaultXRP does not hold the funds.",
                    destination = dest,
                    amountDrops = stake.claimFeeDrops,
                    memo = FlareMemoCodec.upshiftClaim(item.period, stake.vaultId),
                    lots = FlareMemoCodec.lots(item.amountDrops, stake.lotSizeDrops),
                    period = item.period,
                    personalAccount = stake.personalAccount,
                ),
            )
        }
        return null
    }

    fun prepareReturnToXrp(): String? {
        val wallet = active ?: return "No wallet"
        if (_ui.value.pendingNotice != null) return "A previous signed transaction is still pending. Wait for it to finish."
        if (wallet.isWatchOnly) return "Watch-only wallets cannot redeem FXRP."
        val stake = _ui.value.stake ?: return "Stake data is still loading. Pull to refresh."
        if (!stake.positionsResolved) return "On-chain position is unavailable. Redeem is disabled until balances load."
        val idleDrops = stake.idleFxrp?.movePointRight(6)?.toLong() ?: 0L
        val lots = FlareMemoCodec.lots(idleDrops, stake.lotSizeDrops)
        if (lots <= 0L) return "No idle FXRP lots to return yet. Claim an unlocked withdrawal first."
        val dest = stake.operator?.takeIf { it.startsWith("r") } ?: return "Operator address could not be resolved."
        _ui.update {
            it.copy(
                sheet = Sheet.STAKE_REVIEW,
                stakePlan = StakePlan(
                    action = "redeem",
                    title = "Return FXRP to XRP",
                    explanation = "This Payment redeems idle FXRP lots back to XRP on this XRPL address. Lot size applies. A validated Payment cannot be reversed. VaultXRP does not hold the funds.",
                    destination = dest,
                    amountDrops = stake.redeemFeeDrops,
                    memo = FlareMemoCodec.fxrpRedeem(lots),
                    lots = lots,
                    personalAccount = stake.personalAccount,
                ),
            )
        }
        return null
    }

    suspend fun submitStakePlan(secret: String): XrplSubmissionOutcome = withContext(Dispatchers.IO) {
        val wallet = active ?: error("no wallet")
        val plan = _ui.value.stakePlan ?: error("not prepared")
        require(!wallet.isWatchOnly) { "watchOnly" }
        require(prefs.pending() == null) { "pending" }
        _ui.update { it.copy(sendBusy = true) }
        try {
            val prep = xrpl.preparePayment(wallet.classicAddress, plan.destination)
            val extraReserve = if (plan.action == "mint") (_ui.value.stake?.depositFeeDrops ?: 0L) else 0L
            require(plan.amountDrops + extraReserve <= prep.spendableDrops) { "amountExceedsSpendable" }
            val payment = XrplPayment(
                account = wallet.classicAddress,
                destination = plan.destination,
                amountDrops = plan.amountDrops,
                feeDrops = prep.feeDrops,
                sequence = prep.source.sequence,
                lastLedgerSequence = prep.lastLedgerSequence,
                destinationTag = null,
                memo = null,
                binaryMemo = plan.memo,
            )
            val signed = TxCodec.signPayment(payment, wallet, secret)
            prefs.savePending(
                PendingSignedTransaction(plan.action, wallet.classicAddress, signed.hash, signed.blobHex, prep.lastLedgerSequence, System.currentTimeMillis()),
            )
            val outcome = xrpl.submitAndWait(signed.blobHex, signed.hash, prep.lastLedgerSequence)
            if (outcome !is XrplSubmissionOutcome.Unknown) prefs.savePending(null)
            _ui.update { it.copy(sendOutcome = outcome) }
            if (outcome is XrplSubmissionOutcome.ValidatedSuccess) {
                handleStakeSuccess(plan.action)
            }
            refreshAll()
            outcome
        } finally {
            _ui.update { it.copy(sendBusy = false) }
        }
    }

    private suspend fun handleStakeSuccess(action: String) {
        val stake = _ui.value.stake
        val address = active?.classicAddress
        when (action) {
            "mint" -> _ui.update { it.copy(stakeWait = "mint") }
            "deposit" -> _ui.update { it.copy(stakeWait = "deposit") }
            "unstake" -> {
                if (address != null && stake != null) {
                    prefs.saveStakePending(
                        StakePendingUnstake(
                            xrplAddress = address,
                            period = stake.currentPeriod,
                            amountDrops = stake.stakedXrp?.movePointRight(6)?.toLong() ?: 0L,
                            createdAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
                _ui.update { it.copy(stakeWait = "unstake") }
            }
            "claim" -> {
                prefs.saveStakePending(null)
                _ui.update { it.copy(stakeWait = "claim") }
            }
            else -> _ui.update { it.copy(stakeWait = null) }
        }
        startStakeWaitPolling()
    }

    private suspend fun mergeLocalPending(snapshot: FlareStakeSnapshot, address: String?): FlareStakeSnapshot {
        val local = prefs.loadStakePending() ?: return snapshot
        if (address == null || local.xrplAddress != address) return snapshot
        if (snapshot.pendingWithdrawals.isNotEmpty()) {
            prefs.saveStakePending(null)
            return snapshot
        }
        return snapshot.copy(
            pendingWithdrawals = listOf(FlarePendingWithdrawal(local.period, local.amountDrops, false)),
            pendingClaimable = false,
            unstakingXrp = java.math.BigDecimal(local.amountDrops).movePointLeft(6),
        )
    }

    private suspend fun resolveStakeWait(snapshot: FlareStakeSnapshot) {
        when (_ui.value.stakeWait) {
            "mint" -> {
                val idleLots = FlareMemoCodec.lots(
                    snapshot.idleFxrp?.movePointRight(6)?.toLong() ?: 0L,
                    snapshot.lotSizeDrops,
                )
                if (idleLots > 0L) _ui.update { it.copy(stakeWait = null) }
            }
            "deposit" -> {
                val idleLots = FlareMemoCodec.lots(
                    snapshot.idleFxrp?.movePointRight(6)?.toLong() ?: 0L,
                    snapshot.lotSizeDrops,
                )
                if (idleLots == 0L) _ui.update { it.copy(stakeWait = null) }
            }
            "unstake" -> {
                if (prefs.loadStakePending() == null) _ui.update { it.copy(stakeWait = null) }
            }
            "claim" -> {
                val idleLots = FlareMemoCodec.lots(
                    snapshot.idleFxrp?.movePointRight(6)?.toLong() ?: 0L,
                    snapshot.lotSizeDrops,
                )
                if (idleLots > 0L) _ui.update { it.copy(stakeWait = null) }
            }
        }
    }

    private fun startStakeWaitPolling() {
        if (_ui.value.stakeWait == null) return
        stakeWaitJob?.cancel()
        stakeWaitJob = viewModelScope.launch {
            repeat(18) {
                delay(8_000)
                if (_ui.value.stakeWait == null) return@launch
                val address = active?.classicAddress
                try {
                    val stake = mergeLocalPending(staking.fetchSnapshot(address), address)
                    _ui.update { it.copy(stake = stake, stakeError = null) }
                    resolveStakeWait(stake)
                } catch (_: Exception) {
                }
                if (_ui.value.stakeWait == null) return@launch
            }
            _ui.update { it.copy(stakeWait = null) }
        }
    }

    fun saveContact(contact: WalletContact) {
        viewModelScope.launch {
            val next = (_ui.value.contacts.filterNot { it.id == contact.id } + contact).sortedBy { it.name.lowercase() }
            prefs.saveContacts(next)
            _ui.update { it.copy(contacts = next) }
        }
    }

    suspend fun exportBackup(password: String): ByteArray = withContext(Dispatchers.IO) {
        val items = JSONArray()
        _ui.value.wallets.wallets.forEach { w ->
            val secret = if (w.isWatchOnly) null else secrets.read(w.id)
            items.put(
                JSONObject()
                    .put("record", JSONObject()
                        .put("id", w.id)
                        .put("name", w.name)
                        .put("classicAddress", w.classicAddress)
                        .put("publicKey", w.publicKey)
                        .put("keyAlgorithm", w.keyAlgorithm.name)
                        .put("importKind", w.importKind?.name)
                        .put("derivationPath", w.derivationPath)
                        .put("addedAtEpochMs", w.addedAtEpochMs)
                        .put("backupStatus", w.backupStatus.name))
                    .put("secret", secret),
            )
        }
        val payload = JSONObject().put("createdAt", System.currentTimeMillis()).put("items", items).toString()
        BackupCrypto.export(payload.toByteArray(), password)
    }

    suspend fun importBackup(data: ByteArray, password: String) = withContext(Dispatchers.IO) {
        val plain = BackupCrypto.open(data, password).toString(Charsets.UTF_8)
        val items = JSONObject(plain).getJSONArray("items")
        val collection = prefs.loadWallets()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val r = item.getJSONObject("record")
            val record = WalletRecord(
                id = r.getString("id"),
                name = r.getString("name"),
                classicAddress = r.getString("classicAddress"),
                publicKey = r.optString("publicKey"),
                keyAlgorithm = WalletKeyAlgorithm.valueOf(r.getString("keyAlgorithm")),
                importKind = r.optString("importKind").takeIf { it.isNotEmpty() }?.let { WalletImportKind.valueOf(it) },
                derivationPath = r.optString("derivationPath").takeIf { it.isNotEmpty() },
                addedAtEpochMs = r.optLong("addedAtEpochMs"),
                backupStatus = WalletBackupStatus.VERIFIED,
            )
            if (collection.wallets.any { it.classicAddress == record.classicAddress }) continue
            if (!record.isWatchOnly) {
                secrets.save(record.id, item.getString("secret"))
            }
            collection.add(record)
        }
        prefs.saveWallets(collection)
        _ui.update { it.copy(wallets = collection, appState = AppState.WALLET) }
    }

    fun createBip39(name: String): GeneratedWalletMaterial {
        val phrase = keys.generateMnemonic()
        val candidate = keys.deriveMnemonicCandidates(phrase).first()
        return keys.material(phrase, "", candidate, name)
    }
    fun importFormat(input: String, format: XrplSecretFormat, algo: WalletKeyAlgorithm, name: String) =
        keys.importSeedMaterial(input, format, algo, name)
    fun bip39Candidates(phrase: String, passphrase: String = "") =
        keys.deriveMnemonicCandidates(phrase, passphrase = passphrase)
    fun bip39Material(phrase: String, pass: String, candidate: DerivedWalletCandidate, name: String) =
        keys.material(phrase, pass, candidate, name)
    fun watch(address: String, name: String): WalletRecord {
        val parsed = AddressCodec.parseDestination(address) ?: error("invalidAddress")
        return WalletRecord(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            classicAddress = parsed.first,
            publicKey = "",
            keyAlgorithm = WalletKeyAlgorithm.UNKNOWN,
            importKind = WalletImportKind.WATCH_ONLY,
            derivationPath = null,
            addedAtEpochMs = System.currentTimeMillis(),
            backupStatus = WalletBackupStatus.UNVERIFIED,
        )
    }
    fun reveal(id: String): String? = if (secrets.contains(id)) secrets.read(id) else null
}
