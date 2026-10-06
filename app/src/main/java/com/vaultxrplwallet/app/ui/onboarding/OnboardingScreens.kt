package com.vaultxrplwallet.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.R
import com.vaultxrplwallet.app.core.crypto.AddressCodec
import com.vaultxrplwallet.app.core.crypto.Bip39
import com.vaultxrplwallet.app.core.crypto.XrplSecretFormat
import com.vaultxrplwallet.app.core.vault.NameGenerator
import com.vaultxrplwallet.app.core.crypto.DerivedWalletCandidate
import com.vaultxrplwallet.app.core.crypto.WalletKeyAlgorithm
import com.vaultxrplwallet.app.ui.components.AcknowledgementRow
import com.vaultxrplwallet.app.ui.components.AnimatedVaultMark
import com.vaultxrplwallet.app.ui.components.OnboardingProgress
import com.vaultxrplwallet.app.ui.components.PageHeading
import com.vaultxrplwallet.app.ui.components.PremiumBackground
import com.vaultxrplwallet.app.ui.components.PremiumCard
import com.vaultxrplwallet.app.ui.components.PremiumField
import com.vaultxrplwallet.app.ui.components.PrimaryGlassButton
import com.vaultxrplwallet.app.ui.components.RecoveryWordGrid
import com.vaultxrplwallet.app.ui.components.SelectableTile
import com.vaultxrplwallet.app.ui.components.StatusPill
import com.vaultxrplwallet.app.ui.components.WalletNameField
import com.vaultxrplwallet.app.ui.theme.Accent
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import com.vaultxrplwallet.app.ui.theme.Success
import com.vaultxrplwallet.app.ui.theme.Warning
import kotlinx.coroutines.launch

enum class OnboardPage { WELCOME, SELF, SECURITY, FEATURES, READY, METHOD, CREATE, IMPORT }

@Composable
fun OnboardingFlow(model: AppViewModel, activity: FragmentActivity) {
    var page by remember { mutableStateOf(OnboardPage.WELCOME) }
    PremiumBackground {
        when (page) {
            OnboardPage.WELCOME -> WelcomePage { page = OnboardPage.SELF }
            OnboardPage.SELF -> InfoPage(
                title = "Self-Custody",
                hero = R.drawable.hero_key,
                heading = "Your keys.\nYour XRP.",
                rows = listOf(
                    Icons.Default.Key to ("On-device keys" to "Secrets are generated and stored only on this device."),
                    Icons.Default.People to ("No middleman" to "VaultXRP never holds your funds or recovery phrase."),
                    Icons.Default.CloudOff to ("No cloud custody" to "There is no account and no remote backup of your keys."),
                ),
                step = 1,
                onBack = { page = OnboardPage.WELCOME },
                onContinue = { page = OnboardPage.SECURITY },
            )
            OnboardPage.SECURITY -> SecurityPage(
                onBack = { page = OnboardPage.SELF },
                onContinue = { page = OnboardPage.FEATURES },
            )
            OnboardPage.FEATURES -> InfoPage(
                title = "Features",
                hero = R.drawable.hero_chart,
                heading = "Everything XRPL,\nin one place.",
                rows = listOf(
                    Icons.Default.SwapVert to ("Send & receive" to "Native XRP payments with destination tags and memos."),
                    Icons.AutoMirrored.Filled.ShowChart to ("Live market" to "XRP prices and issued-token discovery."),
                    Icons.Default.Verified to ("Trust lines" to "Activate or deactivate issued assets you choose."),
                ),
                step = 3,
                onBack = { page = OnboardPage.SECURITY },
                onContinue = { page = OnboardPage.READY },
            )
            OnboardPage.READY -> InfoPage(
                title = "Get Started",
                hero = R.drawable.hero_privacy,
                heading = "Private\nby design.",
                rows = listOf(
                    Icons.Default.VisibilityOff to ("No tracking" to "No analytics SDKs. No seed transmission."),
                    Icons.Default.Shield to ("Local-first" to "Signing stays on this device."),
                ),
                step = 4,
                cta = "Set up wallet",
                onBack = { page = OnboardPage.FEATURES },
                onContinue = { page = OnboardPage.METHOD },
            )
            OnboardPage.METHOD -> MethodPage(
                onCreate = { page = OnboardPage.CREATE },
                onImport = { page = OnboardPage.IMPORT },
                onBack = { page = OnboardPage.READY },
            )
            OnboardPage.CREATE -> CreateWalletPage(model, activity) { page = OnboardPage.METHOD }
            OnboardPage.IMPORT -> ImportWalletPage(model, activity) { page = OnboardPage.METHOD }
        }
    }
}

@Composable
private fun WelcomePage(onContinue: () -> Unit) {
    val colors = LocalVaultColors.current
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 88.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVaultMark(54.dp)
                Spacer(Modifier.weight(1f))
                StatusPill("MAINNET", Success)
            }
            Spacer(Modifier.height(20.dp))
            Text("VaultXRP", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.secondary)
            Spacer(Modifier.height(10.dp))
            Text("Hold XRP.\nOwn the keys.", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = (-1.2).sp, color = colors.primary, lineHeight = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("A private, local-first wallet for the decentralized XRP Ledger. No account. No cloud custody.", fontFamily = Nunito, fontSize = 16.sp, color = colors.secondary)
            Spacer(Modifier.height(16.dp))
            Image(painterResource(R.drawable.xrp_logo), null, Modifier.height(96.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf(Icons.Default.Key to "Your keys", Icons.Default.Lock to "On-device", Icons.Default.VisibilityOff to "No tracking").forEach { (icon, title) ->
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Icon(icon, null, tint = Accent)
                        Spacer(Modifier.height(8.dp))
                        Text(title, fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.secondary)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            OnboardingProgress(0, 5)
            Spacer(Modifier.height(12.dp))
            Text("Your keys and seed phrases stay on this device. VaultXRP never transmits them. You can export an encrypted backup you control.", fontFamily = Nunito, fontSize = 13.sp, color = Warning, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Self-custody means only you can recover this wallet.", fontFamily = Nunito, fontSize = 13.sp, color = colors.tertiary, modifier = Modifier.padding(top = 8.dp))
            Text("Independent software. Not affiliated with Ripple or the XRPL Foundation.", fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        }
        PrimaryGlassButton("Continue", modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp), onClick = onContinue)
    }
}

@Composable
private fun InfoPage(
    title: String,
    hero: Int,
    heading: String,
    rows: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, Pair<String, String>>>,
    step: Int,
    cta: String = "Continue",
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalVaultColors.current
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 88.dp)) {
            Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.secondary, modifier = Modifier.clickable(onClick = onBack))
            Image(painterResource(hero), null, Modifier.height(160.dp).fillMaxWidth().padding(vertical = 12.dp), contentScale = ContentScale.Fit)
            Text(heading, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-1).sp, color = colors.primary, lineHeight = 34.sp)
            Spacer(Modifier.height(14.dp))
            rows.forEach { (icon, copy) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(icon, null, tint = Accent)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(copy.first, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.primary)
                        Text(copy.second, fontFamily = Nunito, fontSize = 14.sp, color = colors.secondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OnboardingProgress(step, 5)
        }
        PrimaryGlassButton(cta, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp), onClick = onContinue)
    }
}

@Composable
private fun SecurityPage(onBack: () -> Unit, onContinue: () -> Unit) {
    val colors = LocalVaultColors.current
    var rec by remember { mutableStateOf(false) }
    var tx by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 200.dp)) {
        Text("Security", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onBack), color = colors.secondary)
        Image(painterResource(R.drawable.hero_shield), null, Modifier.height(110.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
        Text("Know what\nyou control.", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-1).sp, color = colors.primary)
        Text("Self-custody removes the middleman — and makes you responsible for recovery and every transaction.", fontFamily = Nunito, fontSize = 15.sp, color = colors.secondary, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp))
        listOf(
            "01" to ("Recovery is yours" to "If your recovery secret is lost, nobody—including support—can restore the wallet."),
            "02" to ("Keep the secret offline" to "Never share it, photograph it, or type it into an untrusted app."),
            "03" to ("Transactions are final" to "A validated XRP Ledger transaction cannot be reversed."),
        ).forEach { (n, copy) ->
            Row(Modifier.padding(bottom = 14.dp)) {
                Text(n, fontFamily = Nunito, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.padding(end = 12.dp))
                Column {
                    Text(copy.first, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text(copy.second, fontFamily = Nunito, fontSize = 14.sp, color = colors.secondary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AcknowledgementRow("I understand recovery is my responsibility", rec) { rec = !rec }
        Spacer(Modifier.height(10.dp))
        AcknowledgementRow("I understand transactions are irreversible", tx) { tx = !tx }
        Spacer(Modifier.height(14.dp))
        OnboardingProgress(2, 5)
    }
        PrimaryGlassButton("Continue", enabled = rec && tx, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp), onClick = onContinue)
    }
}

@Composable
private fun MethodPage(onCreate: () -> Unit, onImport: () -> Unit, onBack: () -> Unit) {
    val colors = LocalVaultColors.current
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Wallet Setup", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(16.dp))
        Text("Add your\nfirst wallet.", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = colors.primary)
        Text("Create new keys on this device, or restore a wallet you already control.", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))
        MethodRow(Icons.Default.AutoAwesome, "Create a New Wallet", "24-word BIP39 phrase · XRPL BIP44 path", "RECOMMENDED", onCreate)
        Spacer(Modifier.height(12.dp))
        MethodRow(Icons.Default.Download, "Import an Existing Wallet", "Recovery phrase, family seed, secret numbers, RFC-1751, hex, passphrase, or watch-only", null, onImport)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Default.Lock, null, tint = Accent)
            Spacer(Modifier.size(8.dp))
            Text("Secrets are encrypted on this device and never transmitted by the app. You can export an encrypted backup you control.", fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary)
        }
    }
}

@Composable
private fun MethodRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, badge: String?, onClick: () -> Unit) {
    val colors = LocalVaultColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.elevated).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, null, tint = Accent)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
                if (badge != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(badge, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Accent, modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Text(subtitle, fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary)
        }
    }
}

@Composable
private fun CreateWalletPage(model: AppViewModel, activity: FragmentActivity, onBack: () -> Unit) {
    val colors = LocalVaultColors.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf(model.names.suggest(NameGenerator.Role.CREATED, model.ui.value.wallets.wallets.map { it.name })) }
    var phrase by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("m/44'/144'/0'/0/0") }
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Create Wallet", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(16.dp))
        if (step == 1) {
            StatusPill("New keys are generated on this device", Accent)
            Spacer(Modifier.height(16.dp))
            PageHeading("Create a wallet", "Step 1 of 2")
            Spacer(Modifier.height(16.dp))
            WalletNameField(name, { name = it }) { name = model.names.suggest(NameGenerator.Role.CREATED, model.ui.value.wallets.wallets.map { it.name }, name) }
            Spacer(Modifier.height(16.dp))
            PremiumCard {
                Text("Recovery  24-word BIP39", fontFamily = Nunito, color = colors.primary)
                Text("Derivation  XRPL BIP44", fontFamily = Nunito, color = colors.primary)
                Text("Storage  Device Keystore", fontFamily = Nunito, color = colors.primary)
            }
            Text("Have pen and paper ready", fontFamily = Nunito, color = Warning, modifier = Modifier.padding(vertical = 16.dp))
            PrimaryGlassButton("Generate Wallet") {
                if (!model.secrets.canAuthenticate()) {
                    return@PrimaryGlassButton
                }
                val material = model.createBip39(name.ifBlank { "Main Wallet" })
                phrase = material.secret
                path = material.record.derivationPath ?: path
                address = material.record.classicAddress
                step = 2
            }
            if (!model.secrets.canAuthenticate()) {
                Text("Set a device screen lock before creating a wallet. Unlock, signing, and recovery all require it.", fontFamily = Nunito, color = Warning, modifier = Modifier.padding(top = 12.dp))
            }
        } else {
            var saved by remember { mutableStateOf(false) }
            var quiz by remember { mutableStateOf("") }
            val words = phrase.split(" ")
            PageHeading("Save your recovery phrase", "Step 2 of 2")
            Text("Anyone with these words owns the wallet. Write them down offline. Do not screenshot this screen on a device you do not control.", fontFamily = Nunito, color = Warning, modifier = Modifier.padding(vertical = 12.dp))
            RecoveryWordGrid(words)
            Spacer(Modifier.height(12.dp))
            Text(address, fontFamily = Nunito, fontSize = 13.sp, color = colors.secondary)
            Text(path, fontFamily = Nunito, fontSize = 13.sp, color = colors.tertiary)
            Spacer(Modifier.height(12.dp))
            AcknowledgementRow("I wrote these 24 words down offline", saved) { saved = !saved }
            Spacer(Modifier.height(8.dp))
            PremiumField("Enter word 3 to confirm", quiz, { quiz = it }, words.getOrNull(2)?.let { "Third word" } ?: "")
            Spacer(Modifier.height(18.dp))
            PrimaryGlassButton(if (busy) "Securing Wallet…" else "I Saved My Phrase", enabled = !busy && saved && quiz.trim().equals(words.getOrNull(2).orEmpty(), ignoreCase = true)) {
                busy = true
                scope.launch {
                    try {
                        model.secrets.authenticate(activity, "Authenticate to store this recovery phrase")
                        val material = model.bip39Material(phrase, "", model.bip39Candidates(phrase).first(), name)
                        model.completeCreate(material)
                    } catch (e: Exception) {
                        busy = false
                        return@launch
                    }
                    busy = false
                }
            }
        }
    }
}

@Composable
private fun ImportWalletPage(model: AppViewModel, activity: FragmentActivity, onBack: () -> Unit) {
    val colors = LocalVaultColors.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(model.names.suggest(NameGenerator.Role.IMPORTED, model.ui.value.wallets.wallets.map { it.name })) }
    var format by remember { mutableStateOf(XrplSecretFormat.FAMILY_SEED) }
    var secret by remember { mutableStateOf("") }
    var algo by remember { mutableStateOf(WalletKeyAlgorithm.SECP256K1) }
    var phrasePass by remember { mutableStateOf("") }
    var isPhrase by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf(listOf<DerivedWalletCandidate>()) }
    var selected by remember { mutableStateOf<DerivedWalletCandidate?>(null) }
    var watch by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = 48.dp)) {
        Text("Import Wallet", fontFamily = Nunito, color = colors.secondary, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(12.dp))
        StatusPill("Decoded and encrypted on this device", Accent)
        Spacer(Modifier.height(12.dp))
        PageHeading("Import a wallet")
        Spacer(Modifier.height(12.dp))
        WalletNameField(name, { name = it }) { name = model.names.suggest(NameGenerator.Role.IMPORTED, emptyList(), name) }
        Spacer(Modifier.height(16.dp))
        Text("Import format", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
        val tiles = listOf(
            Triple("Recovery Phrase", "12–24 words", XrplSecretFormat.FAMILY_SEED to true),
            Triple("Family Seed", "sEd… or s…", XrplSecretFormat.FAMILY_SEED to false),
            Triple("Secret Numbers", "8 × 6 digits", XrplSecretFormat.SECRET_NUMBERS to false),
            Triple("RFC-1751", "rippled words", XrplSecretFormat.RFC1751 to false),
            Triple("Seed / Key Hex", "32 or 64 chars", XrplSecretFormat.SEED_HEX to false),
            Triple("Passphrase", "rippled hash", XrplSecretFormat.PASSPHRASE to false),
        )
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (title, hint, pair) ->
                    Box(Modifier.weight(1f)) {
                        SelectableTile(title, hint, !watch && ((isPhrase && title.startsWith("Recovery")) || (!isPhrase && format == pair.first && !title.startsWith("Recovery"))), Icons.Default.Key) {
                            watch = false
                            isPhrase = title.startsWith("Recovery")
                            format = pair.first
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        SelectableTile("Watch-Only", "Track any address, no secret", watch, Icons.Default.Visibility) {
            watch = true
            isPhrase = false
        }
        Spacer(Modifier.height(16.dp))
        PremiumField(if (watch) "Classic address" else "Secret", secret, { secret = it }, if (watch) "Classic XRPL address beginning with r" else "Paste here")
        if (isPhrase) {
            Spacer(Modifier.height(10.dp))
            PremiumField("BIP39 passphrase", phrasePass, { phrasePass = it }, "Optional · case-sensitive")
        }
        if (!watch && !isPhrase && format != XrplSecretFormat.FAMILY_SEED) {
            Spacer(Modifier.height(10.dp))
            Text("Key algorithm", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(WalletKeyAlgorithm.SECP256K1 to "secp256k1", WalletKeyAlgorithm.ED25519 to "Ed25519").forEach { (a, label) ->
                    Text(
                        label,
                        color = if (algo == a) androidx.compose.ui.graphics.Color.White else colors.primary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (algo == a) Accent else colors.elevatedStrong).clickable { algo = a }.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontFamily = Nunito,
                    )
                }
            }
        }
        error?.let { Text(it, color = com.vaultxrplwallet.app.ui.theme.Danger, modifier = Modifier.padding(top = 10.dp)) }
        if (candidates.isNotEmpty()) {
            Text("Choose the matching address", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp), color = colors.primary)
            Text("Accounts 1–5 from the documented XRPL BIP44 path.", fontFamily = Nunito, color = colors.secondary, fontSize = 13.sp)
            candidates.forEach { c ->
                Text(
                    "Account ${c.accountIndex + 1}  ${c.classicAddress}",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected == c) Accent.copy(0.1f) else colors.elevated).clickable { selected = c }.padding(12.dp),
                    fontFamily = Nunito,
                    color = colors.primary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryGlassButton(
            when {
                busy -> "Importing…"
                watch -> "Add watch-only wallet"
                candidates.isNotEmpty() -> "Import selected wallet"
                isPhrase -> "Verify phrase"
                else -> "Import"
            },
            enabled = !busy,
        ) {
            error = null
            busy = true
            scope.launch {
                try {
                    when {
                        watch -> {
                            require(AddressCodec.isValidClassicAddress(secret.trim())) { "Enter a valid classic address" }
                            model.completeWatchOnly(model.watch(secret.trim(), name))
                        }
                        isPhrase && candidates.isEmpty() -> {
                            require(Bip39.validate(secret)) { "Invalid recovery phrase" }
                            candidates = model.bip39Candidates(secret, phrasePass)
                            selected = candidates.first()
                        }
                        isPhrase -> {
                            val c = selected ?: error("Choose the matching address")
                            model.secrets.authenticate(activity, "Authenticate to import this recovery phrase")
                            model.completeCreate(model.bip39Material(secret, phrasePass, c, name))
                        }
                        else -> {
                            val actual = if (secret.trim().split(Regex("\\s+")).size in 12..24) {
                                isPhrase = true
                                candidates = model.bip39Candidates(secret, phrasePass)
                                selected = candidates.first()
                                null
                            } else {
                                model.importFormat(secret, format, algo, name)
                            }
                            if (actual != null) {
                                model.secrets.authenticate(activity, "Authenticate to import this wallet")
                                model.completeCreate(actual)
                            }
                        }
                    }
                } catch (e: Exception) {
                    error = e.message
                }
                busy = false
            }
        }
    }
}
