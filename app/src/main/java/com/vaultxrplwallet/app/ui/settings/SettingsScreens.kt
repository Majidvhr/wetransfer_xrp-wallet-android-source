package com.vaultxrplwallet.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Support
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.Sheet
import com.vaultxrplwallet.app.core.crypto.Bip39
import com.vaultxrplwallet.app.core.vault.NameGenerator
import com.vaultxrplwallet.app.data.AppAppearance
import com.vaultxrplwallet.app.data.AutoLockPreference
import com.vaultxrplwallet.app.ui.components.AcknowledgementRow
import com.vaultxrplwallet.app.ui.components.PremiumCard
import com.vaultxrplwallet.app.ui.components.PremiumField
import com.vaultxrplwallet.app.ui.components.PrimaryGlassButton
import com.vaultxrplwallet.app.ui.components.RecoveryWordGrid
import com.vaultxrplwallet.app.ui.components.SecondaryGlassButton
import com.vaultxrplwallet.app.ui.components.SettingsNavRow
import com.vaultxrplwallet.app.ui.components.WalletNameField
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.Danger
import com.vaultxrplwallet.app.ui.theme.Dimens
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import com.vaultxrplwallet.app.ui.theme.Warning
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(model: AppViewModel, activity: FragmentActivity) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("root") }
    var deleteText by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf<String?>(null) }
    var backupPass by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.isLocked) {
        if (ui.isLocked && !model.awaitingExternalDocument) {
            reveal = null
            backupPass = ""
            deleteText = ""
            status = null
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            model.cancelExternalDocument()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val bytes = model.takeExportBytes() ?: error("Backup expired. Authenticate and export again.")
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                status = "Backup exported"
            } catch (e: Exception) {
                model.cancelExternalDocument()
                status = e.message
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            model.cancelExternalDocument()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val password = model.consumeBackupPassword()
                val data = ctx.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                model.importBackup(data, password)
                status = "Backup imported"
            } catch (e: Exception) {
                model.cancelExternalDocument()
                status = e.message
            }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.horizontal, vertical = 16.dp).padding(bottom = 24.dp)) {
        Text("Settings", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = colors.primary)
        Spacer(Modifier.height(16.dp))
        when (page) {
            "root" -> {
                PremiumCard {
                    Text("Appearance", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        AppAppearance.entries.forEach { a ->
                            Text(
                                a.title,
                                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(if (ui.appearance == a) Accent else colors.elevatedStrong).clickable { model.setAppearance(a) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (ui.appearance == a) androidx.compose.ui.graphics.Color.White else colors.primary,
                                fontFamily = Nunito,
                            )
                        }
                    }
                    Text("${ui.appearance.title} appearance is active.", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    Text("Security", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    SettingsNavRow("Lock Now", Icons.Default.Lock) { model.lock() }
                    SettingsNavRow("Authentication & Auto-Lock", Icons.Default.Face) { page = "auth" }
                    SettingsNavRow("Recovery & Backups", Icons.Default.Storage) { page = "backup" }
                }
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    Text("Network", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(model.network.displayName, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text(if (ui.snapshot != null && ui.ledgerError == null) "Connected" else "Reconnecting…", fontFamily = Nunito, color = colors.secondary)
                    SettingsNavRow("Network details", Icons.Default.Wifi) { model.openSheet(Sheet.NETWORK) }
                }
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    Text("Privacy", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    SettingsNavRow(if (ui.hideBalances) "Show balances" else "Hide balances", Icons.Default.VisibilityOff) { model.toggleHide() }
                    Text("No analytics or tracking", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    Text("Support & Legal", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    SettingsNavRow("Support", Icons.Default.Support) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(com.vaultxrplwallet.app.R.string.support_url)))) }
                    SettingsNavRow("Privacy Policy", Icons.Default.Policy) {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(com.vaultxrplwallet.app.R.string.privacy_policy_url))))
                    }
                    SettingsNavRow("Privacy details", Icons.Default.Policy) { page = "privacy" }
                    SettingsNavRow("Terms of Use", Icons.Default.Description) {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(com.vaultxrplwallet.app.R.string.terms_url))))
                    }
                    SettingsNavRow("Terms details", Icons.Default.Description) { page = "terms" }
                    SettingsNavRow("Independence & network", Icons.Default.Wifi) { page = "independence" }
                }
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    SettingsNavRow("Delete All Wallets and App Data", Icons.Default.Delete, destructive = true) { page = "delete" }
                    Text("Removes app-owned Keystore items, wallet metadata, contacts, caches, and preferences. On-ledger transactions and exported files cannot be deleted.", fontFamily = Nunito, color = colors.secondary)
                }
                Spacer(Modifier.height(12.dp))
                Text("Version  1.2.0 (1)", fontFamily = Nunito, color = colors.tertiary)
            }
            "auth" -> {
                Text("← Settings", color = Accent, modifier = Modifier.clickable { page = "root" }, fontFamily = Nunito)
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    Text("Authentication", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text("Authentication cannot be disabled. It is required to unlock the app, sign transactions, reveal secrets, export backups, and delete wallet data.", fontFamily = Nunito, color = colors.secondary)
                    Spacer(Modifier.height(8.dp))
                    AutoLockPreference.entries.forEach { p ->
                        SettingsRow(p.title) { model.setAutoLock(p) }
                    }
                }
            }
            "backup" -> {
                Text("← Settings", color = Accent, modifier = Modifier.clickable { page = "root" }, fontFamily = Nunito)
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    Text("Recovery secret", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    PrimaryGlassButton("Authenticate & reveal") {
                        scope.launch {
                            try {
                                model.secrets.authenticate(activity, "Authenticate to reveal your recovery secret")
                                reveal = model.reveal(ui.wallets.active!!.id)
                            } catch (e: Exception) { status = e.message }
                        }
                    }
                    reveal?.let { secret ->
                        Text("Anyone with this recovery secret can move your funds. Never share it, screenshot it, or enter it into another app or website.", fontFamily = Nunito, color = Warning, modifier = Modifier.padding(vertical = 8.dp))
                        val phrase = if (secret.trim().startsWith("{")) {
                            Regex("\"phrase\"\\s*:\\s*\"([^\"]+)\"").find(secret)?.groupValues?.get(1) ?: secret
                        } else secret
                        val usedPassphrase = secret.trim().startsWith("{") &&
                            Regex("\"passphrase\"\\s*:\\s*\"([^\"]+)\"").find(secret)?.groupValues?.get(1)?.isNotEmpty() == true
                        if (usedPassphrase) {
                            Text("This wallet was imported with a BIP39 passphrase. The 24 words alone restore a different address. The passphrase is not shown again here.", fontFamily = Nunito, color = Warning, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        val words = Bip39.normalize(phrase).split(" ")
                        if (words.size >= 12) RecoveryWordGrid(words) else Text(secret, fontFamily = Nunito, color = colors.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    Text("Encrypted export", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text("Uses AES-256-GCM with PBKDF2-HMAC-SHA256 and a random salt. The password is never stored.", fontFamily = Nunito, color = colors.secondary)
                    PremiumField("Password", backupPass, { backupPass = it }, "At least 10 characters", secure = true)
                    PrimaryGlassButton("Export .vaultxrp", enabled = backupPass.length >= 10) {
                        scope.launch {
                            try {
                                model.secrets.authenticate(activity, "Authenticate to export an encrypted backup")
                                model.beginExternalDocument()
                                val bytes = model.exportBackup(backupPass)
                                model.holdExportBytes(bytes)
                                export.launch("vaultxrp-backup.vaultxrp")
                            } catch (e: Exception) { status = e.message }
                        }
                    }
                    SecondaryGlassButton("Import backup") {
                        scope.launch {
                            try {
                                if (backupPass.length < 10) {
                                    status = "Enter the backup password first (at least 10 characters)."
                                    return@launch
                                }
                                model.secrets.authenticate(activity, "Authenticate to import an encrypted backup")
                                model.beginExternalDocument(backupPass)
                                importer.launch(arrayOf("application/x-vaultxrp", "*/*"))
                            } catch (e: Exception) { status = e.message }
                        }
                    }
                    Text("An exported file contains encrypted wallet secrets. Keep both the file and its password private. Anyone with both can control the backed-up wallets.", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            "delete" -> {
                Text("← Settings", color = Accent, modifier = Modifier.clickable { page = "root" }, fontFamily = Nunito)
                Spacer(Modifier.height(12.dp))
                PremiumCard {
                    Text("Type DELETE", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    PremiumField("Confirm", deleteText, { deleteText = it }, "DELETE")
                    PrimaryGlassButton("Authenticate & delete everything", enabled = deleteText == "DELETE") {
                        scope.launch {
                            try {
                                model.secrets.authenticate(activity, "Authenticate to delete all wallet data")
                                model.deleteAll()
                            } catch (e: Exception) { status = e.message }
                        }
                    }
                }
            }
            "privacy" -> Legal(
                "Privacy Policy",
                listOf(
                    "Effective date" to "September 2026",
                    "Local-first wallet" to "VaultXRP Wallet does not create a developer account or custody funds. Recovery secrets are encrypted on device. The AES key lives in Android Keystore; ciphertext is stored in the app’s private files. Wallet names, public addresses, contacts, appearance, and cached public ledger data stay local.",
                    "Network requests" to "To show balances, tokens, market data, fees, and status, the app sends public addresses and signed blobs to public XRPL nodes (xrplcluster.com, s1/s2.ripple.com). Market and icons use CoinGecko, XRPLMeta, and Bithomp. Stake uses Flare RPC, Ankr, Upshift, and DefiLlama with your public XRPL address. Recovery phrases, passphrases, and private keys are never sent.",
                    "Camera and QR scanning" to "Camera frames are processed on-device with ML Kit to recognize QR codes. Frames are not stored or uploaded. Google Play services may process on-device diagnostics for that library.",
                    "Backups" to "You can export an encrypted .vaultxrp file that you control. The app never transmits that file. Anyone with the file and its password can control the wallets inside it.",
                    "Tracking and analytics" to "The app contains no advertising SDK or product analytics. It does not collect an advertising ID.",
                    "Your controls" to "Delete All wipes Keystore-backed secrets, wallet metadata, contacts, pending-transaction records, preferences, and the image cache. It cannot reverse XRP Ledger transactions or delete backup files you exported elsewhere.",
                ),
            ) { page = "root" }
            "terms" -> Legal(
                "Terms of Use",
                listOf(
                    "Self-custody" to "VaultXRP Wallet is non-custodial software. You alone control wallet credentials and authorize transactions. The developer cannot recover a lost phrase, stop a submitted transaction, freeze an address, or restore funds.",
                    "No financial service" to "The app is software for interacting with the XRP Ledger. It is not a bank, exchange, broker, custodian, money transmitter, investment adviser, or provider of financial, tax, or legal advice.",
                    "Optional third-party staking" to "The Stake tab lets you sign an ordinary XRPL Payment into Flare’s FAssets Core Vault for the Upshift • Clearstar earnXRP vault. That is not native XRPL staking. Yield is variable. VaultXRP never holds FLR or your keys. Unstaking waits one vault epoch.",
                    "Irreversible transactions" to "XRP Ledger transactions may be irreversible after validation. You are responsible for checking the network, recipient address, destination tag, amount, fee, issuer, and final review screen.",
                    "Issued assets" to "Issued currencies such as RLUSD are obligations of their issuers, not native XRP. Displaying an asset is not an endorsement.",
                    "No warranty" to "The software is provided on an as-available basis to the extent permitted by law.",
                ),
            ) { page = "root" }
            "independence" -> Legal(
                "Independence & Network",
                listOf(
                    "Independent product" to "VaultXRP Wallet is independently developed. It is not affiliated with, sponsored by, endorsed by, or operated by Ripple, the XRPL Foundation, or any token issuer.",
                    "The XRP Ledger" to "The XRP Ledger is a decentralized public network operated by independent participants. VaultXRP Wallet is a client that reads public ledger data, signs locally, and submits transactions to network endpoints.",
                    "Names and marks" to "XRP, XRP Ledger, XRPL, Ripple, RLUSD, and other names may be trademarks of their respective owners. Their use identifies compatible networks or assets and does not imply affiliation.",
                    "Truthful asset display" to "Native XRP and the documented Mainnet RLUSD issuer are permanently discoverable in the interface. A displayed issued asset does not imply that a wallet has a trust line or balance; the app labels that state explicitly.",
                ),
            ) { page = "root" }
        }
        status?.let { Text(it, fontFamily = Nunito, color = colors.primary, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
private fun SettingsRow(title: String, onClick: () -> Unit) {
    val colors = LocalVaultColors.current
    Text(title, fontFamily = Nunito, fontWeight = FontWeight.Medium, color = colors.primary, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp))
}

@Composable
private fun Legal(title: String, sections: List<Pair<String, String>>, back: () -> Unit) {
    val colors = LocalVaultColors.current
    Text("← Settings", color = Accent, modifier = Modifier.clickable(onClick = back), fontFamily = Nunito)
    Spacer(Modifier.height(12.dp))
    Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
    sections.forEach { (heading, body) ->
        Text(heading, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(top = 18.dp))
        Text(body, fontFamily = Nunito, color = colors.secondary, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
fun WalletSwitcherSheet(model: AppViewModel, activity: FragmentActivity) {
    val ui by model.ui.collectAsState()
    val colors = LocalVaultColors.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var quiz by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(model.names.suggest(NameGenerator.Role.CREATED, ui.wallets.wallets.map { it.name })) }
    LaunchedEffect(ui.isLocked, ui.privacyShield) {
        if (ui.isLocked || ui.privacyShield) {
            adding = false
            phrase = ""
            saved = false
            quiz = ""
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Wallets", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
        Spacer(Modifier.height(12.dp))
        ui.wallets.wallets.forEach { w ->
            PremiumCard {
                Row(Modifier.fillMaxWidth().clickable { model.activateWallet(w.id) }) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        Text(w.classicAddress, fontFamily = Nunito, fontSize = 12.sp, color = colors.secondary)
                    }
                    if (w.id == ui.wallets.activeWalletID) {
                        Text("ACTIVE", fontFamily = Nunito, fontWeight = FontWeight.Bold, color = Accent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!adding) {
            PrimaryGlassButton("Add wallet") { adding = true }
        } else {
            WalletNameField(name, { name = it }) { name = model.names.suggest(NameGenerator.Role.CREATED, ui.wallets.wallets.map { it.name }, name) }
            Spacer(Modifier.height(8.dp))
            if (phrase.isBlank()) {
                PrimaryGlassButton("Generate recovery phrase") {
                    if (!model.secrets.canAuthenticate()) return@PrimaryGlassButton
                    phrase = model.createBip39(name).secret
                }
                if (!model.secrets.canAuthenticate()) {
                    Text("Set a device screen lock before creating a wallet.", fontFamily = Nunito, color = Warning, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                val words = phrase.split(" ")
                Text("Write these 24 words down before the wallet is saved.", fontFamily = Nunito, color = Warning)
                Spacer(Modifier.height(8.dp))
                RecoveryWordGrid(words)
                Spacer(Modifier.height(8.dp))
                AcknowledgementRow("I wrote these 24 words down offline", saved) { saved = !saved }
                PremiumField("Enter word 3 to confirm", quiz, { quiz = it }, "Third word")
                Spacer(Modifier.height(8.dp))
                PrimaryGlassButton("Save wallet", enabled = saved && quiz.trim().equals(words.getOrNull(2).orEmpty(), ignoreCase = true)) {
                    scope.launch {
                        model.secrets.authenticate(activity, "Authenticate to store this recovery phrase")
                        val material = model.bip39Material(phrase, "", model.bip39Candidates(phrase).first(), name)
                        model.completeCreate(material)
                    }
                }
            }
        }
    }
}
