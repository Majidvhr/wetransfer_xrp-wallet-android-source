package com.vaultxrplwallet.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.Sheet
import com.vaultxrplwallet.app.data.AppTab
import com.vaultxrplwallet.app.data.RLUSD_ISSUER
import com.vaultxrplwallet.app.data.truncatedAddress
import com.vaultxrplwallet.app.ui.components.HomeNotice
import com.vaultxrplwallet.app.ui.components.PortfolioMetric
import com.vaultxrplwallet.app.ui.components.PremiumCard
import com.vaultxrplwallet.app.ui.components.QuickActionButton
import com.vaultxrplwallet.app.ui.components.RlusdLogo
import com.vaultxrplwallet.app.ui.components.TokenMark
import com.vaultxrplwallet.app.ui.components.XrpLogo
import com.vaultxrplwallet.app.ui.formatAmount
import com.vaultxrplwallet.app.ui.formatTokenPrice
import com.vaultxrplwallet.app.ui.formatUsd
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.AccentSoft
import com.vaultxrplwallet.app.ui.theme.Dimens
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import com.vaultxrplwallet.app.ui.theme.Success
import com.vaultxrplwallet.app.ui.theme.Warning
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletHome(model: AppViewModel) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val wallet = ui.wallets.active
    val snap = ui.snapshot
    val hidden = ui.hideBalances
    val rlusdMarket = ui.featured.firstOrNull { it.currency == "RLUSD" && it.issuer == RLUSD_ISSUER }
    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { model.refreshAll() }, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.horizontal, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Accent.copy(alpha = 0.10f))
                        .clickable { model.openSheet(Sheet.WALLETS) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                        Text((wallet?.name ?: "V").take(1), color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(7.dp))
                    Text(wallet?.name ?: "VaultXRP", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.primary)
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.secondary, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(50)).background(colors.elevated).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) "Show balances" else "Hide balances", tint = colors.primary, modifier = Modifier.size(20.dp).clickable { model.toggleHide() })
                    Spacer(Modifier.size(10.dp))
                    Icon(Icons.Default.Lock, "Lock wallet", tint = colors.primary, modifier = Modifier.size(20.dp).clickable { model.lock() })
                }
            }
            Spacer(Modifier.height(18.dp))
            PremiumCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XrpLogo(26.dp)
                    Spacer(Modifier.size(9.dp))
                    Text("Total balance", fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = colors.secondary)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.clip(RoundedCornerShape(50)).background(colors.elevatedStrong).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (snap != null && ui.ledgerError == null) Success else Warning))
                        Spacer(Modifier.size(6.dp))
                        Text("MAINNET", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = colors.secondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (ui.refreshing && snap == null) {
                    CircularProgressIndicator(color = colors.secondary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (hidden) "••••" else (snap?.totalXRP ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString(), fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = colors.primary)
                        Spacer(Modifier.size(6.dp))
                        Text("XRP", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = colors.secondary)
                    }
                    if (!hidden) {
                        val fiat = ui.xrpMarket?.priceUSD?.let { (snap?.totalXRP ?: BigDecimal.ZERO).multiply(it) }
                        Text(formatUsd(fiat), fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = colors.secondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = colors.line)
                Spacer(Modifier.height(12.dp))
                Row {
                    PortfolioMetric("Spendable", if (hidden) "••••" else "${(snap?.spendableXRP ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString()} XRP", Modifier.weight(1f))
                    PortfolioMetric("Reserved", if (hidden) "••••" else "${(snap?.reserveXRP ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString()} XRP", Modifier.weight(1f))
                }
                wallet?.classicAddress?.let { addr ->
                    var copied by remember { mutableStateOf(false) }
                    val ctx = LocalContext.current
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.canvas)
                            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                            .clickable {
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("address", addr))
                                copied = true
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy, null, tint = colors.secondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (copied) "Address copied" else addr.truncatedAddress(), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.secondary)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { QuickActionButton("Send", Icons.Default.ArrowUpward) { model.openSheet(Sheet.SEND) } }
                Box(Modifier.weight(1f)) { QuickActionButton("Receive", Icons.Default.ArrowDownward) { model.openSheet(Sheet.RECEIVE) } }
                Box(Modifier.weight(1f)) { QuickActionButton("Stake", Icons.Default.BarChart) { model.selectTab(AppTab.STAKE) } }
                Box(Modifier.weight(1f)) { QuickActionButton("Scan", Icons.Default.QrCodeScanner) { model.openSheet(Sheet.SCAN) } }
            }
            ui.pendingNotice?.let {
                Spacer(Modifier.height(18.dp))
                HomeNotice("Pending transaction", it, Icons.Default.History, Warning)
            }
            ui.ledgerError?.let {
                Spacer(Modifier.height(18.dp))
                HomeNotice("Can't reach the ledger", it, Icons.Default.WifiOff, Warning)
            }
            if (snap != null && !snap.account.isFunded) {
                Spacer(Modifier.height(18.dp))
                val reserve = if (snap.reserveBaseXRP > BigDecimal.ZERO) "${snap.reserveBaseXRP.stripTrailingZeros().toPlainString()} XRP" else "current"
                HomeNotice("Activate this account", "This address is valid but unfunded. Receive at least the $reserve account reserve in XRP to activate it on Mainnet.", Icons.Default.History, AccentSoft)
            }
            Spacer(Modifier.height(18.dp))
            PremiumCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Assets", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.primary)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.clip(RoundedCornerShape(50)).background(Accent.copy(0.12f)).clickable { model.openSheet(Sheet.ADD_TOKENS) }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Add tokens", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Accent)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val xrpFiat = ui.xrpMarket?.priceUSD?.let { (snap?.totalXRP ?: BigDecimal.ZERO).multiply(it) }
                AssetLine(
                    name = "XRP",
                    price = formatTokenPrice(ui.xrpMarket?.priceUSD),
                    holding = if (hidden) "••••" else (snap?.totalXRP ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString(),
                    holdingFiat = if (hidden) null else formatUsd(xrpFiat),
                    xrp = true,
                    onClick = { model.openXrpDetail() },
                )
                HorizontalDivider(color = colors.line)
                val rlusd = snap?.tokens?.firstOrNull { it.issuer == RLUSD_ISSUER && it.currency == "RLUSD" }
                val rlusdFiat = if (rlusd != null && rlusdMarket?.priceUSD != null) rlusd.balance.multiply(rlusdMarket.priceUSD) else null
                AssetLine(
                    name = "RLUSD",
                    price = formatTokenPrice(rlusdMarket?.priceUSD),
                    holding = if (hidden) "••••" else rlusd?.balance?.stripTrailingZeros()?.toPlainString() ?: "—",
                    holdingFiat = if (hidden) null else formatUsd(rlusdFiat),
                    status = if (rlusd == null) "Not enabled" else null,
                    rlusd = true,
                    iconURL = rlusdMarket?.iconURL,
                    onClick = {
                        if (rlusd != null) model.openIssuedLine(rlusd) else {
                            model.openToken(
                                rlusdMarket ?: com.vaultxrplwallet.app.data.MarketToken(
                                    currency = "RLUSD",
                                    issuer = RLUSD_ISSUER,
                                    name = "Ripple USD",
                                    iconURL = null,
                                    alternateIconURL = null,
                                    priceXRP = null,
                                    volume24hXRP = BigDecimal.ZERO,
                                    marketCapXRP = null,
                                    holders = 0,
                                    trustLevel = 0,
                                    supply = null,
                                ),
                            )
                        }
                    },
                )
                snap?.tokens.orEmpty().filter { !(it.issuer == RLUSD_ISSUER && it.currency == "RLUSD") }.forEach { token ->
                    HorizontalDivider(color = colors.line)
                    val market = ui.featured.firstOrNull { it.currency == token.currency && it.issuer == token.issuer }
                    AssetLine(
                        name = token.currency,
                        price = formatTokenPrice(market?.priceUSD),
                        holding = if (hidden) "••••" else formatAmount(token.balance),
                        status = if (token.isFrozen) "Frozen" else null,
                        iconURL = market?.iconURL,
                        onClick = { model.openIssuedLine(token) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            PremiumCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent activity", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.primary)
                    Spacer(Modifier.weight(1f))
                    Text("See all", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = Accent, modifier = Modifier.clickable { model.selectTab(AppTab.ACTIVITY) })
                }
                val activity = snap?.activity.orEmpty().take(5)
                if (activity.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, tint = colors.secondary)
                        Spacer(Modifier.size(8.dp))
                        Text("No validated transactions yet.", fontFamily = Nunito, color = colors.secondary)
                    }
                } else {
                    activity.forEach { item ->
                        val out = item.account == wallet?.classicAddress
                        Row(Modifier.padding(vertical = 10.dp).clickable { model.selectTab(AppTab.ACTIVITY) }, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.type, fontFamily = Nunito, modifier = Modifier.weight(1f), color = colors.primary)
                            Text((if (out) "−" else "+") + (item.amount?.formatted() ?: "—") + " XRP", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.tertiary, modifier = Modifier.size(14.dp).padding(start = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AssetLine(
    name: String,
    price: String,
    holding: String,
    holdingFiat: String? = null,
    status: String? = null,
    xrp: Boolean = false,
    rlusd: Boolean = false,
    iconURL: String? = null,
    onClick: () -> Unit,
) {
    val colors = LocalVaultColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            xrp -> XrpLogo(40.dp)
            rlusd -> RlusdLogo(40.dp, iconURL)
            else -> TokenMark(name, iconURL, 40.dp)
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Text(price, fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(holding, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Text(status ?: holdingFiat ?: "", fontFamily = Nunito, fontSize = 12.sp, color = if (status == "Frozen") Warning else colors.secondary)
        }
    }
}
