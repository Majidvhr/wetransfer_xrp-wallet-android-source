package com.vaultxrplwallet.app.ui.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.Sheet
import com.vaultxrplwallet.app.data.MarketToken
import com.vaultxrplwallet.app.data.PriceChartRange
import com.vaultxrplwallet.app.data.RLUSD_ISSUER
import com.vaultxrplwallet.app.data.TrustLine
import com.vaultxrplwallet.app.data.XrplSubmissionOutcome
import com.vaultxrplwallet.app.data.truncatedAddress
import com.vaultxrplwallet.app.ui.components.AcknowledgementRow
import com.vaultxrplwallet.app.ui.components.HomeNotice
import com.vaultxrplwallet.app.ui.components.PortfolioMetric
import com.vaultxrplwallet.app.ui.components.PremiumCard
import com.vaultxrplwallet.app.ui.components.PremiumField
import com.vaultxrplwallet.app.ui.components.PriceChart
import com.vaultxrplwallet.app.ui.components.PrimaryGlassButton
import com.vaultxrplwallet.app.ui.components.RlusdLogo
import com.vaultxrplwallet.app.ui.components.TokenMark
import com.vaultxrplwallet.app.ui.components.XrpLogo
import com.vaultxrplwallet.app.ui.formatPct
import com.vaultxrplwallet.app.ui.formatTokenPrice
import com.vaultxrplwallet.app.ui.formatUsd
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.Danger
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import com.vaultxrplwallet.app.ui.theme.Success
import com.vaultxrplwallet.app.ui.theme.Warning
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date

@Composable
fun AddTokensSheet(model: AppViewModel, activity: FragmentActivity) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf<MarketToken?>(null) }
    val filtered = remember(ui.featured, query) {
        val tokens = ui.featured
        if (query.isBlank()) tokens else tokens.filter {
            it.name.contains(query, true) || it.currency.contains(query, true) || it.issuer.contains(query, true)
        }
    }
    if (focused != null) {
        TokenDetailSheet(model, activity, xrp = false, token = focused, onBack = { focused = null })
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Add tokens", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Top XRPL issued tokens by 24h volume. Tap a token to review the issuer and activate a trust line on your wallet.",
            fontFamily = Nunito,
            fontSize = 13.sp,
            color = colors.secondary,
        )
        Spacer(Modifier.height(12.dp))
        PremiumField("Search", query, { query = it }, "Search token or issuer")
        Spacer(Modifier.height(16.dp))
        when {
            ui.marketError != null && filtered.isEmpty() -> Text(ui.marketError!!, fontFamily = Nunito, color = colors.secondary)
            filtered.isEmpty() && query.isBlank() -> {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(8.dp))
                    Text("Loading top tokens…", fontFamily = Nunito, color = colors.secondary)
                }
            }
            filtered.isEmpty() -> Text("No matching tokens", fontFamily = Nunito, color = colors.secondary)
            else -> {
                Text("Top ${filtered.size}", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Spacer(Modifier.height(8.dp))
                filtered.forEachIndexed { index, token ->
                    val enabled = ui.snapshot?.tokens?.any { it.currency == token.currency && it.issuer == token.issuer } == true
                    PremiumCard(modifier = Modifier.clickable { focused = token }.padding(bottom = 8.dp), radius = 18.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.secondary, modifier = Modifier.size(20.dp))
                            TokenMark(token.currency, token.iconURL, 36.dp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(token.name, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary, maxLines = 1)
                                Text(token.currency, fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatTokenPrice(token.priceUSD), fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                                Text(if (enabled) "Enabled" else "Add", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (enabled) colors.secondary else Accent)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun TokenDetailSheet(
    model: AppViewModel,
    activity: FragmentActivity,
    xrp: Boolean,
    token: MarketToken? = null,
    onBack: (() -> Unit)? = null,
) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val resolved = token ?: ui.selectedToken
    val line: TrustLine? = if (xrp) null else ui.snapshot?.tokens?.firstOrNull {
        it.currency == resolved?.currency && it.issuer == resolved?.issuer
    }
    val isRlusd = resolved?.issuer == RLUSD_ISSUER && resolved.currency == "RLUSD"
    val symbol = if (xrp) "XRP" else resolved?.currency ?: "Token"
    val name = if (xrp) "XRP" else resolved?.name ?: symbol
    val price = if (xrp) ui.xrpMarket?.priceUSD ?: ui.ecosystem?.priceUSD else resolved?.priceUSD
    val change = if (xrp) ui.ecosystem?.change24hPercent ?: ui.xrpMarket?.change24hPercent else null
    var range by remember { mutableStateOf(PriceChartRange.WEEK) }
    var showTrust by remember { mutableStateOf(false) }
    var issuedSendNotice by remember { mutableStateOf(false) }
    LaunchedEffect(range, xrp) {
        if (xrp) model.loadChart(range.days)
    }
    if (showTrust && !xrp && resolved != null) {
        TrustLineSheet(model, activity, resolved, line) { showTrust = false }
        return
    }
    val holding = if (xrp) ui.snapshot?.totalXRP else line?.balance
    val holdingFiat = if (holding != null && price != null) holding.multiply(price) else null
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        if (onBack != null) {
            Text("← Back", fontFamily = Nunito, color = Accent, modifier = Modifier.clickable(onClick = onBack))
            Spacer(Modifier.height(12.dp))
        }
        Text(symbol, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
        Spacer(Modifier.height(16.dp))
        PremiumCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    xrp -> XrpLogo(52.dp)
                    isRlusd -> RlusdLogo(52.dp, resolved?.iconURL)
                    else -> TokenMark(symbol, resolved?.iconURL, 52.dp)
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.primary)
                        if (xrp || isRlusd) {
                            Spacer(Modifier.size(6.dp))
                            androidx.compose.material3.Icon(Icons.Default.CheckCircle, null, tint = Accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(symbol, fontFamily = Nunito, color = colors.secondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatTokenPrice(price), fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.primary)
                    change?.let {
                        Text(formatPct(it), fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (it >= BigDecimal.ZERO) Success else Danger)
                    }
                }
            }
        }
        if (xrp) {
            Spacer(Modifier.height(16.dp))
            PremiumCard(padding = 18.dp) {
                Text("Price chart", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceChartRange.entries.forEach { option ->
                        Text(
                            option.title,
                            fontFamily = Nunito,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (range == option) Accent else colors.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (range == option) Accent.copy(0.18f) else colors.elevatedStrong)
                                .clickable { range = option }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (ui.chart.size < 2) {
                    Column(Modifier.fillMaxWidth().height(200.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
                    }
                } else {
                    val up = (ui.chart.last().priceUSD - ui.chart.first().priceUSD) >= 0
                    PriceChart(ui.chart, if (up) Success else Danger)
                    Row(Modifier.fillMaxWidth()) {
                        Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ui.chart.first().dateMs)), fontFamily = Nunito, fontSize = 11.sp, color = colors.tertiary)
                        Spacer(Modifier.weight(1f))
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ui.chart.last().dateMs)), fontFamily = Nunito, fontSize = 11.sp, color = colors.tertiary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PremiumCard(padding = 18.dp) {
            Text("Your holding", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(if (ui.hideBalances) "••••" else holding?.stripTrailingZeros()?.toPlainString() ?: (if (xrp) "0" else "—"), fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = colors.primary)
                    Text(symbol, fontFamily = Nunito, color = colors.secondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (ui.hideBalances) "••••" else formatUsd(holdingFiat), fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = colors.primary)
                    Text("USD value", fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
                }
            }
            if (xrp) {
                Spacer(Modifier.height(12.dp))
                Row {
                    PortfolioMetric("Spendable", if (ui.hideBalances) "••••" else (ui.snapshot?.spendableXRP ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString(), Modifier.weight(1f))
                    PortfolioMetric("Reserved", if (ui.hideBalances) "••••" else (ui.snapshot?.reserveXRP ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString(), Modifier.weight(1f))
                    PortfolioMetric("Total", if (ui.hideBalances) "••••" else (ui.snapshot?.totalXRP ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString(), Modifier.weight(1f))
                }
                if (ui.snapshot?.account?.isFunded == false) {
                    Spacer(Modifier.height(10.dp))
                    HomeNotice("Account is unfunded. Receive XRP to activate, then you can send.", "Receive the current account reserve in XRP to activate this address on Mainnet.", Icons.Default.Info, Accent)
                }
            } else if (line == null) {
                Spacer(Modifier.height(10.dp))
                HomeNotice("No trust line yet", "Activate to hold this issued asset. Balance is not implied.", Icons.Default.Info, Accent)
            } else {
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(if (line.isFrozen) "Frozen" else "Trust line active", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (line.isFrozen) Warning else Success)
                    Spacer(Modifier.weight(1f))
                    Text("Limit ${line.limit.stripTrailingZeros().toPlainString()}", fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
                }
            }
            if (!xrp) {
                Spacer(Modifier.height(12.dp))
                PrimaryGlassButton(if (line == null) "Activate $symbol" else "Deactivate $symbol") { showTrust = true }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryGlassButton("Send", Modifier.weight(1f)) {
                if (xrp) model.openSheet(Sheet.SEND) else issuedSendNotice = true
            }
            PrimaryGlassButton("Receive", Modifier.weight(1f)) { model.openSheet(Sheet.RECEIVE) }
        }
        if (issuedSendNotice) {
            Spacer(Modifier.height(10.dp))
            HomeNotice("Issued-token send", "VaultXRP can send native XRP today. Issued tokens such as $symbol are view-only for transfers.", Icons.Default.Info, Warning)
        }
        if (xrp) {
            Spacer(Modifier.height(16.dp))
            PremiumCard {
                Text("Market", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                DetailRow("Price", formatTokenPrice(price))
                DetailRow("24h change", formatPct(change))
                DetailRow("Market cap", formatUsd(ui.ecosystem?.marketCapUSD))
            }
        } else if (resolved != null) {
            Spacer(Modifier.height(16.dp))
            PremiumCard {
                Text("Issuer", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                DetailRow("Currency", resolved.currency)
                DetailRow("Issuer", resolved.issuer)
                DetailRow("Holders", resolved.holders.toString())
                DetailRow("24h volume", "${resolved.volume24hXRP.stripTrailingZeros().toPlainString()} XRP")
            }
        }
        val activity = ui.snapshot?.activity.orEmpty().filter {
            if (xrp) it.amount != null else it.issuedCurrency == symbol
        }.take(8)
        Spacer(Modifier.height(16.dp))
        PremiumCard {
            Text("Activity", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            if (activity.isEmpty()) {
                Text("No matching activity yet.", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.padding(top = 8.dp))
            } else {
                activity.forEach { item ->
                    val out = item.account == ui.wallets.active?.classicAddress
                    Row(Modifier.padding(vertical = 8.dp)) {
                        Text(item.type, fontFamily = Nunito, modifier = Modifier.weight(1f), color = colors.primary)
                        Text((if (out) "−" else "+") + (item.amount?.formatted() ?: item.issuedValue?.stripTrailingZeros()?.toPlainString() ?: "—"), fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun TrustLineSheet(
    model: AppViewModel,
    activity: FragmentActivity,
    token: MarketToken,
    line: TrustLine?,
    onBack: () -> Unit,
) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val scope = rememberCoroutineScope()
    val deactivate = line != null
    var acknowledged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<XrplSubmissionOutcome?>(null) }
    val isRlusd = token.issuer == RLUSD_ISSUER && token.currency == "RLUSD"
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("← ${token.currency}", fontFamily = Nunito, color = Accent, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(12.dp))
        Text(if (deactivate) "Deactivate Asset" else "Activate Asset", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isRlusd) RlusdLogo(52.dp, token.iconURL) else TokenMark(token.currency, token.iconURL, 52.dp)
            Spacer(Modifier.size(14.dp))
            Column {
                Text(token.name, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.primary)
                Text(token.currency, fontFamily = Nunito, color = colors.secondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (outcome != null) {
            PremiumCard {
                Text(
                    when (outcome) {
                        is XrplSubmissionOutcome.ValidatedSuccess -> "Trust line updated"
                        is XrplSubmissionOutcome.ValidatedFailure -> "TrustSet failed"
                        is XrplSubmissionOutcome.Rejected -> "TrustSet rejected"
                        is XrplSubmissionOutcome.Expired -> "TrustSet expired"
                        else -> "Status unknown"
                    },
                    fontFamily = Nunito,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryGlassButton("Done") { onBack() }
        } else {
            HomeNotice(
                if (deactivate) "Release the trust-line reserve" else "Create an XRPL trust line",
                if (deactivate) {
                    "This submits a TrustSet with a zero limit. The ledger removes the line and releases its owner reserve only when the balance is zero and no non-default trust-line state remains."
                } else {
                    "An issued asset is not native XRP. Activating it submits a TrustSet transaction, adds an owner object to this wallet, and locks the current owner reserve until the line can be removed."
                },
                Icons.Default.Link,
                if (deactivate) Warning else Accent,
            )
            Spacer(Modifier.height(16.dp))
            PremiumCard {
                DetailRow("Wallet", ui.wallets.active?.classicAddress?.truncatedAddress() ?: "Unavailable")
                HorizontalDivider(color = colors.line)
                DetailRow("Issuer", token.issuer)
                HorizontalDivider(color = colors.line)
                DetailRow("Trust limit", if (deactivate) "0 ${token.currency}" else "1,000,000,000 ${token.currency}")
                HorizontalDivider(color = colors.line)
                DetailRow("Network fee", "${ui.snapshot?.feeDrops ?: 0} drops")
                if (!deactivate) {
                    HorizontalDivider(color = colors.line)
                    DetailRow("Additional reserve", "${ui.snapshot?.reserveIncXRP?.stripTrailingZeros()?.toPlainString() ?: "—"} XRP")
                }
            }
            if (!deactivate) {
                Spacer(Modifier.height(12.dp))
                AcknowledgementRow("I verified the full issuer address", acknowledged) { acknowledged = !acknowledged }
            }
            if (ui.wallets.active?.isWatchOnly == true) {
                Spacer(Modifier.height(12.dp))
                HomeNotice("Watch-only", "Import this wallet’s signing secret to activate or deactivate issued assets.", Icons.Default.Info, Warning)
            }
            error?.let { Text(it, fontFamily = Nunito, color = Danger, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(16.dp))
            PrimaryGlassButton(
                if (busy) "Submitting…" else if (deactivate) "Authenticate & deactivate" else "Authenticate & activate",
                enabled = !busy && ui.wallets.active?.isWatchOnly != true && (deactivate || acknowledged),
            ) {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        model.secrets.authenticate(activity, "Authenticate to submit this TrustSet")
                        val secret = model.reveal(ui.wallets.active!!.id) ?: error("No secret")
                        outcome = model.submitTrust(token.currency, token.issuer, !deactivate, secret, line?.rawCurrency)
                    } catch (e: Exception) {
                        error = e.message
                    }
                    busy = false
                }
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, value: String) {
    val colors = LocalVaultColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, fontFamily = Nunito, color = colors.secondary, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.Medium, color = colors.primary, modifier = Modifier.weight(1.4f))
    }
}
