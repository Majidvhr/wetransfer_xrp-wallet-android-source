package com.vaultxrplwallet.app.ui.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.data.ActivityItem
import com.vaultxrplwallet.app.data.MarketToken
import com.vaultxrplwallet.app.ui.components.DesignedEmptyState
import com.vaultxrplwallet.app.ui.components.HomeNotice
import com.vaultxrplwallet.app.ui.components.PremiumCard
import com.vaultxrplwallet.app.ui.components.TokenMark
import com.vaultxrplwallet.app.ui.components.XrpLogo
import com.vaultxrplwallet.app.ui.formatCompactUsd
import com.vaultxrplwallet.app.ui.formatPct
import com.vaultxrplwallet.app.ui.formatSupply
import com.vaultxrplwallet.app.ui.formatTokenPrice
import com.vaultxrplwallet.app.ui.formatUsd
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.Danger
import com.vaultxrplwallet.app.ui.theme.Dimens
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import com.vaultxrplwallet.app.ui.theme.Success
import com.vaultxrplwallet.app.ui.theme.Warning
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensTab(model: AppViewModel) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val eco = ui.ecosystem
    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { model.refreshAll() }, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.horizontal, vertical = 16.dp)) {
            Text("XRP Market", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = colors.primary)
            Spacer(Modifier.height(16.dp))
            PremiumCard {
                Row(verticalAlignment = Alignment.Top) {
                    XrpLogo(52.dp)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("XRP", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.primary)
                            eco?.marketCapRank?.let {
                                Spacer(Modifier.size(8.dp))
                                Text("#$it", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Accent, modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent.copy(0.12f)).padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                        Text("XRP Ledger native asset", fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
                        Spacer(Modifier.height(6.dp))
                        Text(formatTokenPrice(eco?.priceUSD ?: ui.xrpMarket?.priceUSD), fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = colors.primary)
                        val ch = eco?.change24hPercent ?: ui.xrpMarket?.change24hPercent
                        Text("${formatPct(ch)} 24h", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = if ((ch ?: BigDecimal.ZERO) >= BigDecimal.ZERO) Success else Danger)
                    }
                }
            }
            if (ui.marketError != null && eco == null) {
                Spacer(Modifier.height(16.dp))
                HomeNotice("Market data unavailable", ui.marketError!!, Icons.AutoMirrored.Filled.ShowChart, Warning)
            } else if (eco == null) {
                Spacer(Modifier.height(16.dp))
                PremiumCard {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(8.dp))
                        Text("Loading XRP ecosystem…", fontFamily = Nunito, color = colors.secondary)
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Market cap", formatCompactUsd(eco.marketCapUSD), Modifier.weight(1f))
                    StatTile("24h volume", formatCompactUsd(eco.volume24hUSD), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("FDV", formatCompactUsd(eco.fullyDilutedValuationUSD), Modifier.weight(1f))
                    StatTile("7d change", formatPct(eco.change7dPercent), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("30d change", formatPct(eco.change30dPercent), Modifier.weight(1f))
                    StatTile("Rank", eco.marketCapRank?.let { "#$it" } ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                PremiumCard {
                    Text("Supply", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Stat("Circulating", formatSupply(eco.circulatingSupply))
                    Stat("Total", formatSupply(eco.totalSupply))
                    Stat("Max", formatSupply(eco.maxSupply))
                }
                Spacer(Modifier.height(16.dp))
                PremiumCard {
                    Text("Price range", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Stat("24h high", formatTokenPrice(eco.high24hUSD))
                    Stat("24h low", formatTokenPrice(eco.low24hUSD))
                    Stat("All-time high", formatTokenPrice(eco.athUSD))
                    eco.athChangePercent?.let { Stat("From ATH", formatPct(it)) }
                    Stat("All-time low", formatTokenPrice(eco.atlUSD))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Live XRP market data from CoinGecko. Not investment advice. VaultXRP is not affiliated with Ripple or the XRP Ledger Foundation.", fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary)
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalVaultColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.elevated)
            .padding(14.dp),
    ) {
        Text(title.uppercase(), fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = colors.secondary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.primary, maxLines = 1)
    }
}

@Composable
private fun Stat(k: String, v: String) {
    val colors = LocalVaultColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, fontFamily = Nunito, color = colors.secondary, modifier = Modifier.weight(1f))
        Text(v, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreTab(model: AppViewModel) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { model.refreshAll() }, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.horizontal, vertical = 16.dp)) {
            Text("Explore", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = colors.primary)
            Spacer(Modifier.height(8.dp))
            Text("Top XRPL tokens by 24h volume, sourced live from XRPLMeta. Listings use provider-curated metadata and meaningful-liquidity filters; verify the issuer yourself before activating.", fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary)
            Spacer(Modifier.height(16.dp))
            when {
                ui.marketError != null && ui.featured.isEmpty() -> HomeNotice("Market data unavailable", ui.marketError!!, Icons.AutoMirrored.Filled.ShowChart, Warning)
                ui.featured.isEmpty() -> DesignedEmptyState("No matching tokens", "Market data unavailable or filters excluded every listing.", Icons.Default.History)
                else -> ui.featured.forEachIndexed { i, t ->
                    MarketTokenRow(t, i + 1) { model.openToken(t) }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun MarketTokenRow(token: MarketToken, rank: Int, onClick: () -> Unit) {
    val colors = LocalVaultColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.elevated)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.secondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        TokenMark(token.currency, token.iconURL, 40.dp)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(token.name, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary, maxLines = 1)
            Text(token.currency, fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTokenPrice(token.priceUSD), fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Text("Vol ${token.volume24hXRP.stripTrailingZeros().toPlainString()} XRP", fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTab(model: AppViewModel) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val addr = ui.wallets.active?.classicAddress
    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { model.refreshAll() }, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.horizontal, vertical = 16.dp)) {
            Text("Activity", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = colors.primary)
            Spacer(Modifier.height(16.dp))
            val items = ui.snapshot?.activity.orEmpty()
            if (items.isEmpty()) {
                DesignedEmptyState("No validated activity", "Validated transactions for the selected wallet appear here once the account has ledger history.", Icons.Default.History)
            } else {
                items.forEach { item ->
                    ActivityRow(item, addr)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, walletAddress: String?) {
    val colors = LocalVaultColors.current
    val out = item.account == walletAddress
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background((if (out) Warning else Success).copy(0.10f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.Icon(if (out) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = if (out) Warning else Success, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.type, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Text(if (item.validated) "Validated" else "Pending", fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
            }
            Text(
                when {
                    item.amount != null -> "${if (out) "−" else "+"}${item.amount.formatted()} XRP"
                    item.issuedValue != null && item.issuedCurrency != null -> "${if (out) "−" else "+"}${item.issuedValue.stripTrailingZeros().toPlainString()} ${item.issuedCurrency}"
                    else -> "—"
                },
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
    }
}
