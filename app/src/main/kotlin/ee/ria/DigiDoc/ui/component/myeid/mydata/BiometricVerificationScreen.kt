package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

@Composable
fun BiometricVerificationScreen(
    dg2Image: ByteArray,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var livenessInstruction by remember { mutableStateOf("Position your face in the camera") }
    var livenessVerified by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var comparisonResult by remember { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheckResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(2000)
            toastMessage = null
        }
    }
    val faceDetectorOptions =
        FaceDetectorOptions
            .Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

    val faceDetector = remember { FaceDetection.getClient(faceDetectorOptions) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val faceRecognizer = remember { FaceRecognizer(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Biometric Verification",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )

                if (!hasCameraPermission) {
                    Text("Camera permission is required for verification.", modifier = Modifier.padding(16.dp))
                    Button(onClick = onDismiss) { Text("Close") }
                    return@Column
                }

                if (comparisonResult != null) {
                    // Show final result
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("eID Image")
                            val eIDBitmap = BitmapFactory.decodeByteArray(dg2Image, 0, dg2Image.size)
                            Image(
                                bitmap = eIDBitmap.asImageBitmap(),
                                contentDescription = "eID Face",
                                modifier = Modifier.size(120.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Selfie")
                            capturedBitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Selfie",
                                    modifier = Modifier.size(120.dp),
                                )
                            }
                        }
                    }

                    val resultColor = if (comparisonResult == "Verified") Color(0xFF4CAF50) else Color(0xFFF44336)
                    Text(
                        text = comparisonResult ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = resultColor,
                        modifier = Modifier.padding(16.dp),
                    )

                    Button(onClick = onDismiss, modifier = Modifier.padding(16.dp)) {
                        Text("Close")
                    }
                } else {
                    // Show camera preview
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView =
                                    PreviewView(ctx).apply {
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()

                                    val preview =
                                        Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                    val imageAnalyzer =
                                        ImageAnalysis
                                            .Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                analysis.setAnalyzer(
                                                    executor,
                                                    LivenessAnalyzer(
                                                        faceDetector = faceDetector,
                                                        onInstruction = { livenessInstruction = it },
                                                        onStepSuccess = { msg ->
                                                            toastMessage = msg
                                                        },
                                                        onLivenessVerified = { bmp ->
                                                            livenessVerified = true
                                                            capturedBitmap = bmp

                                                            // Perform face comparison
                                                            val eIDBitmap =
                                                                BitmapFactory.decodeByteArray(
                                                                    dg2Image,
                                                                    0,
                                                                    dg2Image.size,
                                                                )

                                                            // Pass copies to FaceRecognizer as they are manipulated/drawn on via MLKit underneath sometimes
                                                            val isMatch =
                                                                faceRecognizer.compareFaces(
                                                                    eIDBitmap.copy(Bitmap.Config.ARGB_8888, true),
                                                                    bmp.copy(Bitmap.Config.ARGB_8888, true),
                                                                )
                                                            comparisonResult =
                                                                if (isMatch) "Verified" else "Not Verified"
                                                        },
                                                    ),
                                                )
                                            }

                                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            imageAnalyzer,
                                        )
                                    } catch (exc: Exception) {
                                        Log.e("BiometricVerification", "Use case binding failed", exc)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Overlay instruction
                        Text(
                            text = livenessInstruction,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp)
                                    .background(Color(0x88000000))
                                    .padding(16.dp),
                        )
                    }

                    Button(onClick = onDismiss, modifier = Modifier.padding(16.dp)) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
