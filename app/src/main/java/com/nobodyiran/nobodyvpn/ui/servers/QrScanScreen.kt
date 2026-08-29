package com.nobodyiran.nobodyvpn.ui.servers

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.nobodyiran.nobodyvpn.R
import com.nobodyiran.nobodyvpn.data.Repo
import com.nobodyiran.nobodyvpn.ui.UiBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@Composable
fun QrScanScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = remember { Repo.get(context) }
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var handled by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {

        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        try {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val reader = MultiFormatReader().apply {
                                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                if (!handled) {
                                    val text = decodeYuv(proxy, reader)
                                    if (text != null && !handled) {
                                        handled = true
                                        scope.launch {
                                            val imported = withContext(Dispatchers.IO) {
                                                importScanned(repo, text)
                                            }
                                            UiBus.show(
                                                if (imported > 0)
                                                    ctx.getString(R.string.import_result, imported)
                                                else ctx.getString(R.string.invalid_link)
                                            )
                                            onDone()
                                        }
                                    }
                                }
                                proxy.close()
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.camera_permission_needed),
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        // scanning frame hint
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                )
        )

        IconButton(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White)
        }

        Text(
            text = stringResource(R.string.import_qr_camera),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        )
    }
}

/** Decodes a camera frame (YUV) with zxing. */
private fun decodeYuv(proxy: ImageProxy, reader: MultiFormatReader): String? {
    return try {
        if (proxy.format != android.graphics.ImageFormat.YUV_420_888) return null
        val yPlane = proxy.planes[0]
        val buffer = yPlane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val stride = yPlane.rowStride.coerceAtLeast(proxy.width)
        val height = proxy.height
        val source = PlanarYUVLuminanceSource(
            data, stride, height, 0, 0, proxy.width, proxy.height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = try {
            reader.decodeWithState(bitmap)
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
        result?.text
    } catch (_: Exception) {
        null
    }
}

private suspend fun importScanned(repo: Repo, text: String): Int {
    return withContext(Dispatchers.IO) {
        val t = text.trim()
        when {
            t.startsWith("http://", true) || t.startsWith("https://", true) -> {
                val res = com.nobodyiran.nobodyvpn.net.SubUpdater.fetch(t, com.nobodyiran.nobodyvpn.util.C.UA_DEFAULT)
                if (res.ok && res.content != null) {
                    val profiles = com.nobodyiran.nobodyvpn.net.SubUpdater.toProfiles(res.content, null)
                    if (profiles.isNotEmpty()) {
                        repo.addProfiles(profiles)
                        profiles.size
                    } else 0
                } else 0
            }
            t.startsWith("{") && t.contains("outbounds") -> {
                val p = com.nobodyiran.nobodyvpn.parser.JsonConfig.parse(t)
                if (p != null) {
                    repo.addProfiles(listOf(p))
                    1
                } else 0
            }
            else -> {
                val parsed = t.lines().mapNotNull { com.nobodyiran.nobodyvpn.parser.ShareLink.parse(it.trim()) }
                if (parsed.isNotEmpty()) {
                    repo.addProfiles(parsed)
                    parsed.size
                } else 0
            }
        }
    }
}
