package com.murmur.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import java.net.URLDecoder
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors


class QRScannerActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var foundCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        setContentView(previewView)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, { imageProxy ->
                        processImageProxy(imageProxy)
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val code = barcode.rawValue
                    if (!foundCode && code != null) {
                        foundCode = true
                        handleScannedCode(code)
                    }

                }
            }
            .addOnFailureListener {
                it.printStackTrace()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleScannedCode(inviteId: String) {
        val raw = inviteId

        fun extractQueryParam(name: String): String? {
            val match = Regex("""[?&]$name=([^#&]+)""").find(raw)
            return match?.groupValues?.getOrNull(1)?.let {
                URLDecoder.decode(it, "UTF-8")
            }
        }

        // 1) Intent URI path:
        // intent://join?sid=STREAM_ID&rk=RELAY_KEY#Intent;...
        val sidFromIntent = if (raw.startsWith("intent://", ignoreCase = true)) {
            extractQueryParam("sid")
        } else null

        val relayKeyFromIntent = if (raw.startsWith("intent://", ignoreCase = true)) {
            extractQueryParam("rk")
        } else null

        if (!sidFromIntent.isNullOrBlank()) {
            StreamRepository.tryJoinStream(this, sidFromIntent) { success, message ->
                runOnUiThread {
                    if (success) {
                        val resultIntent = Intent().apply {
                            putExtra("streamId", sidFromIntent)
                            if (!relayKeyFromIntent.isNullOrBlank()) {
                                putExtra("relayKey", relayKeyFromIntent)
                            }
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } else {
                        Toast.makeText(this, message ?: "Couldn’t join this stream. Try again.", Toast.LENGTH_SHORT).show()
                        foundCode = false
                    }
                }
            }
            return
        }

        // 2) Legacy path: scantojoin::<inviteId>
        if (!raw.startsWith("scantojoin::", ignoreCase = true)) {
            Log.w("QR_SCAN", "Scanned code not recognized: $raw")
            runOnUiThread {
                Toast.makeText(this, "Unrecognized QR code", Toast.LENGTH_SHORT).show()
                foundCode = false
            }
            return
        }

        val cleanedInviteId = raw.removePrefix("scantojoin::")
        StreamRepository.getStreamIdForInvite(cleanedInviteId) { streamId ->
            if (streamId != null) {
                StreamRepository.tryJoinStream(this, streamId) { success, message ->
                    runOnUiThread {
                        if (success) {
                            val resultIntent = Intent().apply {
                                putExtra("streamId", streamId)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        } else {
                            Toast.makeText(this, message ?: "Couldn’t join this stream. Try again.", Toast.LENGTH_SHORT).show()
                            foundCode = false
                        }
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Invalid invite code", Toast.LENGTH_SHORT).show()
                    foundCode = false
                }
            }
        }
    }


}
