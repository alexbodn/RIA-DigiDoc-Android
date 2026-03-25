package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
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
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors

@Composable
fun BiometricVerificationScreen(
    dg2Image: ByteArray,
    useFrontCamera: Boolean = true,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var livenessInstruction by remember { mutableStateOf("Position your face in the camera") }
    var livenessVerified by remember { mutableStateOf(false) }
    var bestFaceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var comparisonResult by remember { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var liveScore by remember { mutableStateOf<Float?>(null) }
    var showBestMatchDialog by remember { mutableStateOf(false) }

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



    val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .build()

    val faceDetector = remember { FaceDetection.getClient(faceDetectorOptions) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val faceEngine = remember { MobileFaceNetEngine(context) }

    var eIDWrappedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var eIDFace by remember { mutableStateOf<com.google.mlkit.vision.face.Face?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val rawEID = BitmapFactory.decodeByteArray(dg2Image, 0, dg2Image.size)
                val wrappedDG2 = CylinderWrap.wrapFlatToCylinder(rawEID, 1.2f)
                eIDWrappedBitmap = wrappedDG2

                val dg2Input = com.google.mlkit.vision.common.InputImage.fromBitmap(wrappedDG2, 0)
                val dg2Faces = com.google.android.gms.tasks.Tasks.await(faceDetector.process(dg2Input))
                if (dg2Faces.isNotEmpty()) {
                    eIDFace = dg2Faces.first()
                }
            } catch (e: Exception) {
                Log.e("BiometricVerification", "Failed to process eID", e)
            }
        }
    }


    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
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
                        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("eID Image")
                            val eIDBitmap = BitmapFactory.decodeByteArray(dg2Image, 0, dg2Image.size)
                            Image(
                                bitmap = eIDBitmap.asImageBitmap(),
                                contentDescription = "eID Face",
                                modifier = Modifier.size(120.dp),
                            )
                        }
                        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                }
                            },
                            update = { previewView ->
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)

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
                                                val analyzer = FaceVerificationAnalyzer(
                                                    isFrontCamera = useFrontCamera,
                                                    faceDetector = faceDetector,
                                                    onStateChanged = { state ->
                                                        livenessInstruction = state.prompt
                                                    },
                                                    onPerfectFrameFound = { selfieFrame, selfieFace ->
                                                        if (eIDWrappedBitmap != null && eIDFace != null) {
                                                            // 🟢 EXACT R&D PYTHON AFFINE ALIGNMENT
                                                            val alignedEid = transformEidNatural(eIDWrappedBitmap!!, selfieFrame, eIDFace!!, selfieFace)

                                                            val hs = selfieFrame.height
                                                            val ws = selfieFrame.width
                                                            val cx = ws / 2
                                                            val cy = hs / 2
                                                            val size = minOf(hs, ws) / 2

                                                            val y1 = maxOf(0, cy - size)
                                                            val y2 = minOf(hs, cy + size)
                                                            val x1 = maxOf(0, cx - size)
                                                            val x2 = minOf(ws, cx + size)

                                                            val eidCrop = Bitmap.createBitmap(alignedEid, x1, y1, x2 - x1, y2 - y1)
                                                            val selfieCrop = Bitmap.createBitmap(selfieFrame, x1, y1, x2 - x1, y2 - y1)

                                                            bestFaceBitmap = selfieCrop

                                                            val dg2Emb = faceEngine.getEmbedding(eidCrop)
                                                            val selfieEmb = faceEngine.getEmbedding(selfieCrop)

                                                            if (dg2Emb != null && selfieEmb != null) {
                                                                val score = faceEngine.calculateCosineSimilarity(dg2Emb, selfieEmb)
                                                                liveScore = score

                                                                if (score >= FaceVerificationConfig.MATCH_THRESHOLD_PERCENT) {
                                                                    livenessInstruction = LivenessState.MATCHED.prompt
                                                                    livenessVerified = true
                                                                    comparisonResult = "Verified (Score: %.2f%%)".format(score)
                                                                    capturedBitmap = selfieCrop
                                                                } else {
                                                                    livenessInstruction = LivenessState.FAILED.prompt
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                                analysis.setAnalyzer(executor, analyzer)
                                            }

                                    val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

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
                                }, ContextCompat.getMainExecutor(previewView.context))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )



                                                // Live Score Indicator
                        androidx.compose.animation.AnimatedVisibility(
                            visible = liveScore != null,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 100.dp)
                        ) {
                            Text(
                                text = "Match Score: %.2f".format(liveScore ?: 0f),
                                color = if ((liveScore ?: 0f) >= 0.70f) Color.Green else Color.Yellow,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color(0x88000000), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        // Overlay instruction
                        Text(
                            text = livenessInstruction,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 32.dp)
                                    .background(Color(0x88000000), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .padding(16.dp),
                        )
                    }

                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        if (bestFaceBitmap != null) {
                            Button(onClick = { showBestMatchDialog = true }) {
                                Text("Show Best Match")
                            }
                        }
                    }
                }
            }
        }
    }
    if (showBestMatchDialog && bestFaceBitmap != null) {
        Dialog(onDismissRequest = { showBestMatchDialog = false }) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Best Match So Far", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
                    androidx.compose.foundation.Image(
                        bitmap = bestFaceBitmap!!.asImageBitmap(),
                        contentDescription = "Best Match",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showBestMatchDialog = false }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}