package com.vaultxrplwallet.app.ui.send

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vaultxrplwallet.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vaultxrplwallet.app.ui.components.PremiumField
import com.vaultxrplwallet.app.ui.components.PrimaryGlassButton
import com.vaultxrplwallet.app.ui.theme.LocalVaultColors
import com.vaultxrplwallet.app.ui.theme.Nunito
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanSheet(onScanned: (String) -> Unit) {
    val colors = LocalVaultColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var disclosed by remember { mutableStateOf(granted) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var paste by remember { mutableStateOf("") }
    val handled = remember { AtomicBoolean(false) }
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider.value?.unbindAll()
            executor.shutdown()
        }
    }
    Column(Modifier.padding(24.dp)) {
        Text("Scan", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.primary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.camera_disclosure), fontFamily = Nunito, color = colors.secondary)
        Spacer(Modifier.height(16.dp))
        if (!disclosed && !granted) {
            PrimaryGlassButton("Continue") {
                disclosed = true
                launcher.launch(Manifest.permission.CAMERA)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        cameraProvider.value = provider
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        val scanner = BarcodeScanning.getClient()
                        val main = ContextCompat.getMainExecutor(ctx)
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val media = imageProxy.image
                            if (media == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { codes ->
                                    val value = codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                                        ?: codes.firstOrNull()?.rawValue
                                    if (!value.isNullOrBlank() && handled.compareAndSet(false, true)) {
                                        main.execute { onScanned(value) }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)),
            )
        } else if (disclosed) {
            Text("Camera access is needed to scan a QR code. You can still paste an address below.", fontFamily = Nunito, color = colors.secondary)
        }
        Spacer(Modifier.height(16.dp))
        PremiumField("Or paste an address", paste, { paste = it }, "Classic address or xrpl: URI")
        Spacer(Modifier.height(12.dp))
        PrimaryGlassButton("Use address", enabled = paste.isNotBlank()) { onScanned(paste) }
    }
}
