package com.vaultxrplwallet.app.ui.send

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.data.XrplSubmissionOutcome
import com.vaultxrplwallet.app.ui.components.PremiumCard
import com.vaultxrplwallet.app.ui.components.PremiumField
import com.vaultxrplwallet.app.ui.components.PrimaryGlassButton
import com.vaultxrplwallet.app.ui.components.SecondaryGlassButton
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import com.vaultxrplwallet.app.ui.theme.Warning
import kotlinx.coroutines.launch

@Composable
fun SendSheet(model: AppViewModel, activity: FragmentActivity) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) {
        Text(if (step == 0) "Send XRP" else if (step == 1) "Review" else "Transaction status", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
        Spacer(Modifier.height(16.dp))
        when {
            ui.sendOutcome != null -> {
                val o = ui.sendOutcome!!
                Text(
                    when (o) {
                        is XrplSubmissionOutcome.ValidatedSuccess -> "Payment validated"
                        is XrplSubmissionOutcome.ValidatedFailure -> "Payment failed"
                        is XrplSubmissionOutcome.Rejected -> "Payment rejected"
                        is XrplSubmissionOutcome.Expired -> "Payment expired"
                        is XrplSubmissionOutcome.Unknown -> "Status unknown — do not send again until this hash settles"
                    },
                    fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = colors.primary,
                )
                val hash = when (o) {
                    is XrplSubmissionOutcome.ValidatedSuccess -> o.hash
                    is XrplSubmissionOutcome.ValidatedFailure -> o.hash
                    is XrplSubmissionOutcome.Rejected -> o.hash
                    is XrplSubmissionOutcome.Expired -> o.hash
                    is XrplSubmissionOutcome.Unknown -> o.hash
                }
                Text(hash, fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                PrimaryGlassButton("Done") { model.closeSheet() }
            }
            step == 0 -> {
                PremiumCard {
                    Text("XRP", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text("Native XRP Ledger asset", fontFamily = Nunito, color = colors.secondary)
                    Text("${ui.snapshot?.spendableXRP?.stripTrailingZeros()?.toPlainString() ?: "0"} available", fontFamily = Nunito, color = colors.secondary)
                }
                Spacer(Modifier.height(12.dp))
                PremiumField("Amount", ui.sendAmount, { model.setSend(amount = it) }, "0.00")
                Spacer(Modifier.height(12.dp))
                PremiumField("Destination", ui.sendDestination, { model.setSend(dest = it) }, "Classic XRPL address beginning with r")
                Spacer(Modifier.height(12.dp))
                PremiumField("Destination tag", ui.sendTag?.toString().orEmpty(), {
                    val trimmed = it.trim()
                    if (trimmed.isEmpty()) model.setSend(tag = null)
                    else model.setSend(tag = trimmed.toLongOrNull())
                }, "Required by many exchanges · 0–4294967295")
                Spacer(Modifier.height(12.dp))
                PremiumField("Memo", ui.sendMemo, { model.setSend(memo = it) }, "Optional")
                error?.let { Text(it, color = com.vaultxrplwallet.app.ui.theme.Danger, modifier = Modifier.padding(top = 8.dp)) }
                if (ui.wallets.active?.isWatchOnly == true) {
                    Text("Watch-only wallets cannot send. Import the signing secret first.", fontFamily = Nunito, color = Warning, modifier = Modifier.padding(top = 8.dp))
                }
                Text("The fee, sequence, reserve, and expiry are fetched from the validated ledger before review. Your secret is never transmitted.", fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary, modifier = Modifier.padding(vertical = 12.dp))
                PrimaryGlassButton(if (ui.sendBusy) "Checking ledger…" else "Review payment", enabled = !ui.sendBusy && ui.wallets.active?.isWatchOnly != true) {
                    scope.launch {
                        error = model.reviewSend()
                        if (error == null) step = 1
                    }
                }
            }
            else -> {
                val prep = ui.sendPrep
                ReviewRow("Amount", "${ui.sendAmount} XRP")
                ReviewRow("From", ui.wallets.active?.classicAddress.orEmpty())
                ReviewRow("Destination", ui.sendDestination)
                ReviewRow("Destination tag", ui.sendTag?.toString() ?: "None")
                ReviewRow("Network fee", "${prep?.feeDrops ?: 0} drops")
                ReviewRow("Remaining spendable", prep?.let { ((it.spendableDrops - (ui.sendAmount.toBigDecimalOrNull()?.movePointRight(6)?.toLong() ?: 0)).toString()) } ?: "—")
                ReviewRow("Expires after ledger", prep?.lastLedgerSequence?.toString() ?: "—")
                Spacer(Modifier.height(16.dp))
                PrimaryGlassButton(if (ui.sendBusy) "Submitting…" else "Authenticate & send", enabled = !ui.sendBusy) {
                    scope.launch {
                        try {
                            model.secrets.authenticate(activity, "Authenticate to sign this XRP payment")
                            val secret = model.reveal(ui.wallets.active!!.id) ?: error("No secret")
                            model.submitSend(secret)
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                }
                error?.let { Text(it, color = com.vaultxrplwallet.app.ui.theme.Danger) }
            }
        }
    }
}

@Composable
private fun ReviewRow(k: String, v: String) {
    val colors = LocalVaultColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, fontFamily = Nunito, color = colors.secondary, modifier = Modifier.weight(1f))
        Text(v, fontFamily = Nunito, fontWeight = FontWeight.Medium, color = colors.primary, modifier = Modifier.weight(1.4f))
    }
}

@Composable
fun ReceiveSheet(model: AppViewModel) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val ctx = LocalContext.current
    val address = ui.wallets.active?.classicAddress
    var tag by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Receive", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        if (address == null) {
            Text("Create or import a wallet to display its address and QR code.", fontFamily = Nunito, color = colors.secondary)
        } else {
            val payload = if (tag.isNotBlank()) "xrpl:$address?dt=$tag" else address
            qrBitmap(payload)?.let {
                Image(it.asImageBitmap(), "QR code", Modifier.size(220.dp).clip(RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Color.White).padding(14.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(ui.wallets.active?.name.orEmpty(), fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Text(address, fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary)
            Spacer(Modifier.height(12.dp))
            PremiumField("Destination tag", tag, { tag = it }, "Optional")
            Spacer(Modifier.height(12.dp))
            Row {
                SecondaryGlassButton("Copy", Modifier.weight(1f)) {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("address", address))
                }
                Spacer(Modifier.size(8.dp))
                SecondaryGlassButton("Share", Modifier.weight(1f)) {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, payload)
                    }, "Share"))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Warning.copy(0.10f)).padding(12.dp)) {
                Icon(Icons.Default.Warning, null, tint = Warning)
                Spacer(Modifier.size(8.dp))
                Column {
                    Text("Verify before sending", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text(
                        if (tag.isBlank()) {
                            "Only send XRP and supported XRPL-issued assets to this address on Mainnet. Add a destination tag only when the recipient workflow requires one."
                        } else {
                            "This QR includes destination tag $tag. Confirm both the address and tag before requesting payment."
                        },
                        fontFamily = Nunito,
                        fontSize = 13.sp,
                        color = colors.secondary,
                    )
                }
            }
        }
    }
}

private fun qrBitmap(value: String): Bitmap? = try {
    val bits = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
    val bmp = Bitmap.createBitmap(bits.width, bits.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until bits.width) for (y in 0 until bits.height) {
        bmp.setPixel(x, y, if (bits[x, y]) AColor.BLACK else AColor.WHITE)
    }
    bmp
} catch (_: Exception) { null }
