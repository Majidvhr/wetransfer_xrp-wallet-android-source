package com.vaultxrplwallet.app.ui.stake

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.core.stake.FlareMemoCodec
import com.vaultxrplwallet.app.data.XrpAmount
import com.vaultxrplwallet.app.data.XrplSubmissionOutcome
import com.vaultxrplwallet.app.ui.components.AcknowledgementRow
import com.vaultxrplwallet.app.ui.components.HomeNotice
import com.vaultxrplwallet.app.ui.components.PortfolioMetric
import com.vaultxrplwallet.app.ui.components.PremiumCard
import com.vaultxrplwallet.app.ui.components.PriceChart
import com.vaultxrplwallet.app.ui.components.PrimaryGlassButton
import com.vaultxrplwallet.app.ui.components.SecondaryGlassButton
import com.vaultxrplwallet.app.ui.components.XrpLogo
import com.vaultxrplwallet.app.ui.formatCompactUsd
import com.vaultxrplwallet.app.ui.theme.Success
import com.vaultxrplwallet.app.ui.theme.Warning
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.Dimens
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.data.truncatedAddress
import com.vaultxrplwallet.app.ui.theme.Nunito
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakeTab(model: AppViewModel, activity: FragmentActivity) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val stake = ui.stake
    var lots by remember { mutableLongStateOf(1L) }
    var understood by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val lotDrops = stake?.lotSizeDrops ?: FlareMemoCodec.DEFAULT_LOT
    val lotXrp = BigDecimal(lots * lotDrops).movePointLeft(6)
    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { model.refreshAll() }, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.horizontal, vertical = 16.dp)) {
            Text("Stake", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = colors.primary)
            Spacer(Modifier.height(16.dp))
            PremiumCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XrpLogo(52.dp)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Stake XRP", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.primary)
                        Text(FlareMemoCodec.POOL_LABEL, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Accent)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stake?.apy?.let {
                        "${it.multiply(java.math.BigDecimal(100)).setScale(2, java.math.RoundingMode.HALF_UP)}%"
                    } ?: "—",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    letterSpacing = (-0.8).sp,
                    color = colors.primary,
                )
                Text(
                    if ((stake?.apyDays ?: 365) >= 180) "1Y APY" else "30D APY",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.secondary,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    PortfolioMetric("Vault TVL", formatCompactUsd(stake?.tvlUsd), Modifier.weight(1f))
                    PortfolioMetric("Share price", stake?.sharePrice?.stripTrailingZeros()?.toPlainString() ?: "—", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            PremiumCard(padding = 18.dp) {
                Text("${FlareMemoCodec.POOL_TOKEN} TVL", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Spacer(Modifier.height(12.dp))
                if (ui.stakeTvl.size < 2) Text("TVL history is loading…", fontFamily = Nunito, color = colors.secondary)
                else PriceChart(ui.stakeTvl, Accent)
            }
            Spacer(Modifier.height(16.dp))
            PremiumCard(padding = 18.dp) {
                Text("Your position", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    PortfolioMetric("Staked", mask(stake?.stakedXrp, ui.hideBalances), Modifier.weight(1f))
                    PortfolioMetric("Idle FXRP", mask(stake?.idleFxrp, ui.hideBalances), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    PortfolioMetric("Spendable", mask(ui.snapshot?.spendableXRP, ui.hideBalances), Modifier.weight(1f))
                    PortfolioMetric("Unstaking", mask(stake?.unstakingXrp, ui.hideBalances), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    PortfolioMetric("Vault cap left", stake?.vaultCapLeft?.stripTrailingZeros()?.toPlainString() ?: "—", Modifier.weight(1f))
                    PortfolioMetric("Lot size", BigDecimal(lotDrops).movePointLeft(6).stripTrailingZeros().toPlainString(), Modifier.weight(1f))
                }
                if (stake?.positionsResolved != true) {
                    Spacer(Modifier.height(10.dp))
                    Text("On-chain position is unavailable until Flare RPC resolves your personal account balances. Figures show — instead of 0.", fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary)
                }
                if (!stake?.personalAccount.isNullOrBlank() || !stake?.coreVault.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    AddressRow("Personal account", stake?.personalAccount?.truncatedAddress())
                    AddressRow("Core Vault", stake?.coreVault?.truncatedAddress())
                    AddressRow("Operator", stake?.operator?.truncatedAddress())
                }
            }
            Spacer(Modifier.height(16.dp))
            PremiumCard {
                Text("How staking works", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Text("This is third-party Flare vault staking, not native XRPL staking or delegation. Your XRP is sent to Flare’s FAssets Core Vault, minted as FXRP, then deposited into ${FlareMemoCodec.POOL_LABEL} (${FlareMemoCodec.POOL_TOKEN}). VaultXRP never holds FLR, never derives an EVM key, and cannot reverse a validated Payment. Yield is variable. Unstaking waits one vault epoch before XRP can return to this address.", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(10.dp))
                AcknowledgementRow("I understand how staking works", understood) { understood = !understood }
                Spacer(Modifier.height(8.dp))
                AcknowledgementRow("I accept the Stake Terms of Use", terms) { terms = !terms }
                Text("Stake Terms of Use: you alone sign and control this transaction; VaultXRP does not custody funds or promise yield; ${FlareMemoCodec.POOL_LABEL} is a third-party Flare vault; lot size, mint/executor fees, and the unstake epoch delay apply; a validated Payment is final.", fontFamily = Nunito, fontSize = 12.sp, color = colors.tertiary, modifier = Modifier.padding(top = 8.dp))
            }
            if (ui.wallets.active?.isWatchOnly != true) {
                Spacer(Modifier.height(16.dp))
                PremiumCard(padding = 16.dp, radius = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(0.12f)).clickable(enabled = lots > 1) { if (lots > 1) lots -= 1 }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, null, tint = Accent)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$lots lot${if (lots == 1L) "" else "s"}", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                            Text("$lotXrp XRP", fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(0.12f)).clickable { if (lots < 100) lots += 1 }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = Accent)
                        }
                    }
                }
            }
            if (ui.stakeWait != null) {
                Spacer(Modifier.height(16.dp))
                HomeNotice(
                    when (ui.stakeWait) {
                        "mint" -> "Waiting for FXRP"
                        "deposit" -> "Waiting for vault deposit"
                        "unstake" -> "Waiting to lock shares"
                        "claim" -> "Waiting for claimed FXRP"
                        else -> "Waiting on Flare"
                    },
                    when (ui.stakeWait) {
                        "mint" -> "The mint Payment validated. Flare’s operator is minting FXRP into your personal account. This page refreshes automatically, then Deposit remaining FXRP to start earning."
                        "deposit" -> "The deposit instruction validated. Flare’s operator still has to move idle FXRP into earnXRP. Do not send a second deposit until Idle FXRP drops."
                        "unstake" -> "Unstake was submitted. Flare’s operator still has to request the earnXRP redeem. After that, Claim unlocked FXRP when the current epoch ends."
                        "claim" -> "Claim was submitted. When idle FXRP appears, use Return FXRP to XRP to send it back to this address."
                        else -> "Flare’s operator is still executing the last instruction."
                    },
                    Icons.Default.Visibility,
                    Warning,
                )
            }
            val idleLots = FlareMemoCodec.lots(stake?.idleFxrp?.movePointRight(6)?.toLong() ?: 0L, lotDrops)
            if (idleLots > 0L) {
                Spacer(Modifier.height(16.dp))
                HomeNotice(
                    "Deposit FXRP to earn",
                    "Idle FXRP is sitting in your Flare personal account and is not earning yet. Deposit it into ${FlareMemoCodec.POOL_LABEL}, or return it to XRP on this address.",
                    Icons.Default.Visibility,
                    Accent,
                )
            }
            val pendingRows = stake?.pendingWithdrawals.orEmpty()
            pendingRows.forEach { pending ->
                Spacer(Modifier.height(16.dp))
                val unlockLabel = stake?.unlockMs?.let { ms ->
                    java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.US).format(java.util.Date(ms))
                } ?: "the next vault epoch"
                HomeNotice(
                    if (pending.claimable) "Ready to claim" else "Unstake cooldown",
                    if (pending.claimable) {
                        "${mask(BigDecimal(pending.amountDrops).movePointLeft(6), ui.hideBalances)} FXRP is unlocked from earnXRP. Claim it, then return FXRP to this XRPL address."
                    } else {
                        "${mask(BigDecimal(pending.amountDrops).movePointLeft(6), ui.hideBalances)} unlocks after the current earnXRP epoch ends on $unlockLabel."
                    },
                    Icons.Default.Visibility,
                    if (pending.claimable) Success else Warning,
                )
            }
            (status ?: ui.stakeError)?.let { Text(it, fontFamily = Nunito, color = Warning, modifier = Modifier.padding(vertical = 8.dp)) }
            Spacer(Modifier.height(12.dp))
            val canSign = understood && terms && ui.wallets.active?.isWatchOnly != true
            if (idleLots > 0L) {
                PrimaryGlassButton("Deposit remaining FXRP", enabled = canSign && ui.stakeWait != "deposit") {
                    status = model.prepareStakeDeposit()
                }
                Spacer(Modifier.height(8.dp))
            }
            if (stake?.pendingClaimable == true) {
                PrimaryGlassButton("Claim unlocked FXRP", enabled = ui.wallets.active?.isWatchOnly != true && ui.stakeWait != "claim") {
                    status = model.prepareClaim()
                }
                Spacer(Modifier.height(8.dp))
            }
            PrimaryGlassButton("Stake XRP", enabled = canSign && ui.stakeWait != "mint") {
                status = model.prepareStakeMint(lots)
            }
            Spacer(Modifier.height(8.dp))
            SecondaryGlassButton(
                "Unstake",
                enabled = ui.wallets.active?.isWatchOnly != true && (stake?.stakedShares ?: 0L) > 0L && ui.stakeWait != "unstake",
            ) {
                status = model.prepareUnstake()
            }
            if (idleLots > 0L) {
                Spacer(Modifier.height(8.dp))
                SecondaryGlassButton("Return FXRP to XRP", enabled = ui.wallets.active?.isWatchOnly != true) {
                    status = model.prepareReturnToXrp()
                }
            }
            if (ui.wallets.active?.isWatchOnly == true) {
                Spacer(Modifier.height(12.dp))
                HomeNotice("Watch-only", "Import this wallet’s signing secret to stake or unstake. Watch-only addresses cannot sign Flare instructions.", Icons.Default.Visibility, Warning)
            }
            Spacer(Modifier.height(16.dp))
            HomeNotice(
                FlareMemoCodec.POOL_LABEL,
                "This app stakes XRP into ${FlareMemoCodec.POOL_LABEL} (${FlareMemoCodec.POOL_TOKEN}) on Flare. That is the Clearstar earnXRP vault, not the Monarq pool. It is not native XRPL staking or delegation. Amounts snap to whole lots (${BigDecimal(lotDrops).movePointLeft(6)} XRP), yield is variable, and unstaking waits one vault epoch before XRP can return to this address.",
                Icons.Default.Visibility,
                Accent,
            )
        }
    }
}

private fun mask(value: BigDecimal?, hidden: Boolean): String {
    if (hidden) return "••••"
    return value?.stripTrailingZeros()?.toPlainString() ?: "—"
}

@Composable
private fun AddressRow(title: String, value: String?) {
    val colors = LocalVaultColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, fontFamily = Nunito, color = colors.secondary, modifier = Modifier.weight(1f))
        Text(value ?: "—", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
    }
}

@Composable
fun StakeReviewSheet(model: AppViewModel, activity: FragmentActivity) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val plan = ui.stakePlan
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(plan?.title ?: "Review stake", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
        Spacer(Modifier.height(12.dp))
        if (ui.sendOutcome != null) {
            val o = ui.sendOutcome!!
            Text(
                when (o) {
                    is XrplSubmissionOutcome.ValidatedSuccess -> "Instruction validated on XRPL"
                    is XrplSubmissionOutcome.ValidatedFailure -> "Instruction failed"
                    is XrplSubmissionOutcome.Rejected -> "Instruction rejected"
                    is XrplSubmissionOutcome.Expired -> "Instruction expired"
                    is XrplSubmissionOutcome.Unknown -> "Status unknown — do not send again until this hash settles"
                },
                fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary,
            )
            val hash = when (o) {
                is XrplSubmissionOutcome.ValidatedSuccess -> o.hash
                is XrplSubmissionOutcome.ValidatedFailure -> o.hash
                is XrplSubmissionOutcome.Rejected -> o.hash
                is XrplSubmissionOutcome.Expired -> o.hash
                is XrplSubmissionOutcome.Unknown -> o.hash
            }
            Text(hash, fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            PrimaryGlassButton("Done") { model.closeSheet() }
        } else if (plan == null) {
            Text("Stake details are missing. Close this sheet and try again.", fontFamily = Nunito, color = colors.secondary)
        } else {
            Text(plan.explanation, fontFamily = Nunito, color = colors.secondary)
            val needsConsent = plan.action == "mint" || plan.action == "deposit"
            var understood by remember { mutableStateOf(false) }
            var terms by remember { mutableStateOf(false) }
            if (needsConsent) {
                Spacer(Modifier.height(16.dp))
                Text("How staking works", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Text("This is staking, not delegation. Your XRP is sent to Flare’s FAssets Core Vault, minted as FXRP, then deposited into ${FlareMemoCodec.POOL_LABEL} (${FlareMemoCodec.POOL_TOKEN}) with a second instruction. Yield is variable. A validated Payment cannot be reversed.", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(10.dp))
                AcknowledgementRow("I understand how staking works", understood) { understood = !understood }
                Spacer(Modifier.height(8.dp))
                AcknowledgementRow("I accept the Stake Terms of Use", terms) { terms = !terms }
            }
            Spacer(Modifier.height(16.dp))
            AddressRow("Pool", FlareMemoCodec.POOL_LABEL)
            AddressRow("Token", FlareMemoCodec.POOL_TOKEN)
            AddressRow("Destination", plan.destination)
            AddressRow("Personal account", plan.personalAccount?.truncatedAddress())
            if (plan.netDrops != null) {
                AddressRow("Net stake", "${XrpAmount(plan.netDrops).formatted()} XRP")
                AddressRow("Mint fee", "${XrpAmount(plan.mintingFeeDrops ?: 0L).formatted()} XRP")
                AddressRow("Executor fee", "${XrpAmount(plan.executorFeeDrops ?: 0L).formatted()} XRP")
                AddressRow("You send", "${XrpAmount(plan.amountDrops).formatted()} XRP")
            } else {
                AddressRow("Instruction fee", "${XrpAmount(plan.amountDrops).formatted()} XRP")
                if (plan.lots > 0L) AddressRow("Lots", plan.lots.toString())
                plan.period?.let { AddressRow("Period", it.toString()) }
            }
            AddressRow("From", ui.wallets.active?.classicAddress)
            error?.let { Text(it, fontFamily = Nunito, color = Warning, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(16.dp))
            PrimaryGlassButton(
                if (ui.sendBusy) "Submitting…" else when (plan.action) {
                    "unstake" -> "Authenticate & unstake"
                    "claim" -> "Authenticate & claim"
                    "redeem" -> "Authenticate & return"
                    else -> "Authenticate & stake"
                },
                enabled = !ui.sendBusy && (!needsConsent || (understood && terms)),
            ) {
                scope.launch {
                    try {
                        model.secrets.authenticate(activity, "Authenticate to sign this Flare instruction")
                        val secret = model.reveal(ui.wallets.active!!.id) ?: error("No secret")
                        model.submitStakePlan(secret)
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            }
        }
    }
}
