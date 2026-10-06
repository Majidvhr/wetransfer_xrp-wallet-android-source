package com.vaultxrplwallet.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vaultxrplwallet.app.ui.VaultRoot
import com.vaultxrplwallet.app.ui.theme.VaultTheme

class MainActivity : FragmentActivity() {
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        setContent {
            val ui by model.ui.collectAsState()
            val appearance = ui.appearance
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            model.setPrivacy(true)
                            model.onBackground()
                        }
                        Lifecycle.Event.ON_START -> {
                            model.setPrivacy(false)
                            model.onForeground()
                        }
                        else -> {}
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            VaultTheme(appearance) {
                VaultRoot(model, this@MainActivity)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "vaultxrp") {
            model.applyScan(data.toString())
        }
    }
}
