package com.vaultxrplwallet.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vaultxrplwallet.app.AppViewModel
import com.vaultxrplwallet.app.ui.components.AnimatedVaultMark
import com.vaultxrplwallet.app.ui.components.PrimaryGlassButton
import com.vaultxrplwallet.app.ui.theme.Danger
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import kotlinx.coroutines.launch

@Composable
fun LockView(model: AppViewModel, activity: FragmentActivity) {
    val colors = LocalVaultColors.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVaultMark(86.dp)
            Spacer(Modifier.height(20.dp))
            Text("Welcome back", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = colors.primary)
            Text(if (error == null) "Authenticate to open VaultXRP" else "Authentication is required to continue", fontFamily = Nunito, color = colors.secondary)
            Spacer(Modifier.height(24.dp))
            PrimaryGlassButton(
                when {
                    busy -> "Authenticating…"
                    error != null -> "Try again"
                    else -> "Unlock wallet"
                },
            ) {
                busy = true
                error = null
                scope.launch {
                    try {
                        model.secrets.authenticate(activity, "Unlock VaultXRP")
                        model.unlock()
                    } catch (_: Exception) {
                        error = "Biometric unlock or your device PIN is required."
                    }
                    busy = false
                }
            }
            error?.let {
                Text(it, fontFamily = Nunito, color = Danger, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
