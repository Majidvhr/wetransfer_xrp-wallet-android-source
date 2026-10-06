package com.vaultxrplwallet.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vaultxrplwallet.app.AppState
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.Sheet
import com.vaultxrplwallet.app.data.AppTab
import com.vaultxrplwallet.app.data.NetworkEnvironment
import com.vaultxrplwallet.app.data.truncatedAddress
import com.vaultxrplwallet.app.ui.components.AnimatedVaultMark
import com.vaultxrplwallet.app.ui.components.DesignedEmptyState
import com.vaultxrplwallet.app.ui.components.PremiumBackground
import com.vaultxrplwallet.app.ui.components.PremiumCard
import com.vaultxrplwallet.app.ui.components.PrimaryGlassButton
import com.vaultxrplwallet.app.ui.components.QuickActionButton
import com.vaultxrplwallet.app.ui.components.StatusPill
import com.vaultxrplwallet.app.ui.components.VaultBottomBar
import com.vaultxrplwallet.app.ui.components.XrpLogo
import com.vaultxrplwallet.app.ui.home.WalletHome
import com.vaultxrplwallet.app.ui.lock.LockView
import com.vaultxrplwallet.app.ui.onboarding.OnboardingFlow
import com.vaultxrplwallet.app.ui.send.ReceiveSheet
import com.vaultxrplwallet.app.ui.send.ScanSheet
import com.vaultxrplwallet.app.ui.send.SendSheet
import com.vaultxrplwallet.app.ui.settings.SettingsTab
import com.vaultxrplwallet.app.ui.settings.WalletSwitcherSheet
import com.vaultxrplwallet.app.ui.stake.StakeReviewSheet
import com.vaultxrplwallet.app.ui.stake.StakeTab
import com.vaultxrplwallet.app.ui.tokens.ActivityTab
import com.vaultxrplwallet.app.ui.tokens.AddTokensSheet
import com.vaultxrplwallet.app.ui.tokens.ExploreTab
import com.vaultxrplwallet.app.ui.tokens.TokenDetailSheet
import com.vaultxrplwallet.app.ui.tokens.TokensTab
import com.vaultxrplwallet.app.ui.tokens.TrustLineSheet
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.Dimens
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import com.vaultxrplwallet.app.ui.theme.Success
import com.vaultxrplwallet.app.ui.theme.Warning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultRoot(model: AppViewModel, activity: FragmentActivity) {
    val ui by model.ui.collectAsState()
    LaunchedEffect(Unit) {
        delay(1250)
        model.dismissSplash()
    }
    PremiumBackground {
        when (ui.appState) {
            AppState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Opening wallet…", fontFamily = Nunito, color = LocalVaultColors.current.secondary)
                }
            }
            AppState.ONBOARDING -> OnboardingFlow(model, activity)
            AppState.WALLET -> WalletShell(model, activity)
        }
        if (ui.isLocked && ui.appState == AppState.WALLET) {
            LockView(model, activity)
        }
        if (ui.showSplash) LaunchSplash()
        if (ui.privacyShield) PrivacyShield()
    }
}

@Composable
private fun LaunchSplash() {
    val colors = LocalVaultColors.current
    Box(Modifier.fillMaxSize().background(colors.canvas), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVaultMark(116.dp)
            Spacer(Modifier.height(16.dp))
            Text("VaultXRP", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp, color = colors.primary)
            Text("PRIVATE · ON-DEVICE · XRPL", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.6.sp, color = colors.secondary)
        }
    }
}

@Composable
private fun PrivacyShield() {
    val colors = LocalVaultColors.current
    Box(Modifier.fillMaxSize().background(colors.canvas), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVaultMark(86.dp)
            Spacer(Modifier.height(12.dp))
            Text("VaultXRP", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = colors.primary)
            Text("Protected while the app is inactive", fontFamily = Nunito, color = colors.secondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletShell(model: AppViewModel, activity: FragmentActivity) {
    val ui by model.ui.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (ui.tab) {
                AppTab.WALLET -> WalletHome(model)
                AppTab.TOKENS -> TokensTab(model)
                AppTab.ACTIVITY -> ActivityTab(model)
                AppTab.STAKE -> StakeTab(model, activity)
                AppTab.EXPLORE -> ExploreTab(model)
                AppTab.SETTINGS -> SettingsTab(model, activity)
            }
        }
        VaultBottomBar(ui.tab, model::selectTab)
    }
    if (ui.sheet != Sheet.NONE && !ui.isLocked && !ui.privacyShield) {
        ModalBottomSheet(
            onDismissRequest = { if (!ui.sendBusy) model.closeSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = LocalVaultColors.current.canvas,
        ) {
            when (ui.sheet) {
                Sheet.SEND -> SendSheet(model, activity)
                Sheet.RECEIVE -> ReceiveSheet(model)
                Sheet.WALLETS, Sheet.ADD_WALLET -> WalletSwitcherSheet(model, activity)
                Sheet.NETWORK -> NetworkSheet(model)
                Sheet.SCAN -> ScanSheet(model::applyScan)
                Sheet.ADD_TOKENS -> AddTokensSheet(model, activity)
                Sheet.XRP_DETAIL -> TokenDetailSheet(model, activity, xrp = true)
                Sheet.TOKEN_DETAIL -> TokenDetailSheet(model, activity, xrp = false)
                Sheet.TRUST -> ui.selectedToken?.let { TrustLineSheet(model, activity, it, ui.snapshot?.tokens?.firstOrNull { line -> line.currency == it.currency && line.issuer == it.issuer }) { model.closeSheet() } }
                Sheet.STAKE_REVIEW -> StakeReviewSheet(model, activity)
                Sheet.NONE -> {}
            }
        }
    }
}

@Composable
fun NetworkSheet(model: AppViewModel) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val connected = ui.snapshot != null && ui.ledgerError == null
    Column(Modifier.padding(24.dp).statusBarsPadding()) {
        Text("Network", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
        Spacer(Modifier.height(16.dp))
        PremiumCard {
            Text("Active network", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Spacer(Modifier.height(8.dp))
            NetworkRow("Network", model.network.displayName)
            NetworkRow("Status", if (connected) "Connected" else "Reconnecting…")
            NetworkRow("Explorer", model.network.explorerBase.removePrefix("https://"))
        }
        Spacer(Modifier.height(12.dp))
        PremiumCard {
            Text("Endpoints", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Spacer(Modifier.height(8.dp))
            listOf("xrplcluster.com", "s1.ripple.com:51234", "s2.ripple.com:51234").forEach {
                Text(it, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp, color = colors.primary, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Text(
            "VaultXRP Wallet queries public XRP Ledger servers over JSON-RPC. Balance requests send public addresses; transaction submission sends the locally signed public transaction. Seeds, mnemonics, passphrases, and private keys are never transmitted by the app. This build is Mainnet only.",
            fontFamily = Nunito,
            fontSize = 13.sp,
            color = colors.secondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun NetworkRow(title: String, value: String) {
    val colors = LocalVaultColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontFamily = Nunito, color = colors.secondary, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
    }
}

fun money(value: BigDecimal?, hidden: Boolean, suffix: String = ""): String {
    if (hidden) return "••••"
    if (value == null) return "—"
    return value.stripTrailingZeros().toPlainString() + suffix
}

fun formatUsd(value: BigDecimal?): String {
    if (value == null) return "—"
    return "$" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
}

fun formatPct(value: BigDecimal?): String {
    if (value == null) return "—"
    val sign = if (value >= BigDecimal.ZERO) "+" else ""
    return sign + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%"
}

fun formatTokenPrice(value: BigDecimal?): String {
    if (value == null) return "—"
    if (value.compareTo(BigDecimal.ZERO) == 0) return "$0"
    if (value >= BigDecimal.ONE) return formatUsd(value)
    if (value >= BigDecimal("0.01")) {
        return "$" + value.setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
    return "$" + value.setScale(8, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

fun formatCompactUsd(value: BigDecimal?): String {
    if (value == null) return "—"
    val number = value.toDouble()
    val abs = kotlin.math.abs(number)
    fun compact(divisor: Double, suffix: String): String {
        return "$" + BigDecimal(number / divisor).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + suffix
    }
    return when {
        abs >= 1_000_000_000 -> compact(1_000_000_000.0, "B")
        abs >= 1_000_000 -> compact(1_000_000.0, "M")
        abs >= 1_000 -> compact(1_000.0, "K")
        else -> formatUsd(value)
    }
}

fun formatSupply(value: BigDecimal?): String {
    if (value == null) return "—"
    return formatCompactUsd(value).removePrefix("$") + " XRP"
}

fun formatAmount(value: BigDecimal): String {
    val abs = value.abs()
    val scale = when {
        abs.compareTo(BigDecimal.ZERO) == 0 -> 0
        abs >= BigDecimal("1000000") -> 0
        abs >= BigDecimal.ONE -> 4
        else -> 6
    }
    return value.setScale(scale, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
