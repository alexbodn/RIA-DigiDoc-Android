package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch



fun transformEidNatural(eidBmp: android.graphics.Bitmap, selfieBmp: android.graphics.Bitmap, eidFace: com.google.mlkit.vision.face.Face, selfieFace: com.google.mlkit.vision.face.Face): android.graphics.Bitmap {
    val eLeft = eidFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
    val eRight = eidFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)
    val sLeft = selfieFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
    val sRight = selfieFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)

    if (eLeft == null || eRight == null || sLeft == null || sRight == null) return eidBmp

    val eCenter = android.graphics.PointF((eLeft.position.x + eRight.position.x)/2f, (eLeft.position.y + eRight.position.y)/2f)
    val sCenter = android.graphics.PointF((sLeft.position.x + sRight.position.x)/2f, (sLeft.position.y + sRight.position.y)/2f)

    val eDx = (eRight.position.x - eLeft.position.x).toDouble()
    val eDy = (eRight.position.y - eLeft.position.y).toDouble()
    val eDist = Math.sqrt(eDx*eDx + eDy*eDy).toFloat()
    val eAngle = Math.toDegrees(Math.atan2(eDy, eDx)).toFloat()

    val sDx = (sRight.position.x - sLeft.position.x).toDouble()
    val sDy = (sRight.position.y - sLeft.position.y).toDouble()
    val sDist = Math.sqrt(sDx*sDx + sDy*sDy).toFloat()
    val sAngle = Math.toDegrees(Math.atan2(sDy, sDx)).toFloat()

    val scale = sDist / eDist
    val angleDiff = sAngle - eAngle

    val matrix = android.graphics.Matrix()
    matrix.postRotate(angleDiff, eCenter.x, eCenter.y)
    matrix.postScale(scale, scale, eCenter.x, eCenter.y)
    matrix.postTranslate(sCenter.x - eCenter.x, sCenter.y - eCenter.y)

    // warpAffine creates an image matching the target dimensions
    val output = android.graphics.Bitmap.createBitmap(selfieBmp.width, selfieBmp.height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    canvas.drawBitmap(eidBmp, matrix, null)
    return output
}

fun Uri.getFileName(context: android.content.Context): String {
    var result: String? = null
    if (scheme == "content") {
        val cursor = context.contentResolver.query(this, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Unknown"
}

@Composable
fun RNDTestScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var csvUri by remember { mutableStateOf<Uri?>(null) }
    var csvData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        csvUri = uri
        uri?.let {
            val map = mutableMapOf<String, String>()
            context.contentResolver.openInputStream(it)?.bufferedReader()?.useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        map[parts[0]] = parts[1]
                    }
                }
            }
            csvData = map
        }
    }

    var dg2Uri by remember { mutableStateOf<Uri?>(null) }
    var dg2Bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selfieUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    val saveCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                out.write("Filename,Actor,Match_%,Intrinsic_Diff,Blur,Shadow")
                out.newLine()
                for (res in results) {
                    val (nameActor, data) = res
                    out.write("$nameActor,$data")
                    out.newLine()
                }
            }
        }
    }

    val dg2Launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        dg2Uri = uri
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            dg2Bitmap = BitmapFactory.decodeStream(inputStream)
        }
    }

    val selfiesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selfieUris = uris
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Data Science Verification Harness", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { csvLauncher.launch(arrayOf("text/csv", "text/plain")) }) {
                        Text("Upload CSV")
                    }
                    Button(onClick = { dg2Launcher.launch(arrayOf("image/*")) }) {
                        Text("Upload DG2")
                    }
                    Button(onClick = { selfiesLauncher.launch(arrayOf("image/*")) }) {
                        Text("Upload Selfies (${selfieUris.size})")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (dg2Bitmap != null) {
                    Image(bitmap = dg2Bitmap!!.asImageBitmap(), contentDescription = "DG2", modifier = Modifier.height(100.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    enabled = dg2Bitmap != null && selfieUris.isNotEmpty() && !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        isProcessing = true
                        coroutineScope.launch(Dispatchers.IO) {
                          try {
                            val engine = MobileFaceNetEngine(context)
                            val detectorOptions = FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                .build()
                            val faceDetector = FaceDetection.getClient(detectorOptions)

                            // 1. Process DG2 (Just grab the face, crop and embedding happens per selfie)
                            val wrappedDG2 = CylinderWrap.wrapFlatToCylinder(dg2Bitmap!!, 1.2f)
                            val dg2Input = InputImage.fromBitmap(wrappedDG2, 0)
                            val dg2Faces = Tasks.await(faceDetector.process(dg2Input))
                            val dg2Face = if (dg2Faces.isNotEmpty()) dg2Faces.first() else null

                            // 2. Process Selfies Batch
                            val newResults = mutableListOf<Pair<String, String>>()
                            for (uri in selfieUris) {
                                try {
                                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                                    val decodedBmp = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                        decoder.isMutableRequired = true
                                        val maxDim = maxOf(info.size.width, info.size.height)
                                        if (maxDim > 1600) {
                                            val scale = 1600f / maxDim.toFloat()
                                            decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                                        }
                                    }
                                    val selfieBmp = decodedBmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true)

                                    val selfieInput = InputImage.fromBitmap(selfieBmp, 0)
                                    val faces = Tasks.await(faceDetector.process(selfieInput))

                                    if (faces.isNotEmpty() && dg2Face != null) {
                                        val selfieFace = faces.first()

                                        // 🟢 EXACT PYTHON ALIGNMENT
                                        val alignedEid = transformEidNatural(wrappedDG2, selfieBmp, dg2Face, selfieFace)

                                        val hs = selfieBmp.height
                                        val ws = selfieBmp.width
                                        val cx = ws / 2
                                        val cy = hs / 2
                                        val size = minOf(hs, ws) / 2

                                        val y1 = maxOf(0, cy - size)
                                        val y2 = minOf(hs, cy + size)
                                        val x1 = maxOf(0, cx - size)
                                        val x2 = minOf(ws, cx + size)

                                        val eidCrop = Bitmap.createBitmap(alignedEid, x1, y1, x2 - x1, y2 - y1)
                                        val selfieCrop = Bitmap.createBitmap(selfieBmp, x1, y1, x2 - x1, y2 - y1)

                                        val dg2Emb = engine.getEmbedding(eidCrop)
                                        val selfieEmb = engine.getEmbedding(selfieCrop)

                                        if (dg2Emb != null && selfieEmb != null) {
                                            val score = engine.calculateCosineSimilarity(dg2Emb, selfieEmb)
                                            val actualName = uri.getFileName(context)
                                            val actor = csvData.entries.find { actualName.contains(it.key) }?.value ?: "Unknown"

                                            // 🟢 Gatekeeper telemetry
                                            val blurScore = FaceVerificationAnalyzer.calculateLaplacianVariance(selfieCrop)
                                            val shadowScore = FaceVerificationAnalyzer.calculateShadowRatio(selfieBmp, selfieFace)

                                            val sL = selfieFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
                                            val sR = selfieFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
                                            val sLC = selfieFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_CHEEK)?.position
                                            val sRC = selfieFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_CHEEK)?.position

                                            val eL = dg2Face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
                                            val eR = dg2Face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
                                            val eLC = dg2Face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_CHEEK)?.position
                                            val eRC = dg2Face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_CHEEK)?.position

                                            var intrinsicDiff = -1f
                                            if (sL != null && sR != null && sLC != null && sRC != null && eL != null && eR != null && eLC != null && eRC != null) {
                                                val sEyeDist = Math.sqrt(((sR.x - sL.x)*(sR.x - sL.x) + (sR.y - sL.y)*(sR.y - sL.y)).toDouble()).toFloat()
                                                val sWidth = Math.sqrt(((sRC.x - sLC.x)*(sRC.x - sLC.x) + (sRC.y - sLC.y)*(sRC.y - sLC.y)).toDouble()).toFloat()

                                                val eEyeDist = Math.sqrt(((eR.x - eL.x)*(eR.x - eL.x) + (eR.y - eL.y)*(eR.y - eL.y)).toDouble()).toFloat()
                                                val eWidth = Math.sqrt(((eRC.x - eLC.x)*(eRC.x - eLC.x) + (eRC.y - eLC.y)*(eRC.y - eLC.y)).toDouble()).toFloat()

                                                val sRatio = sEyeDist / (sWidth + 1e-6f)
                                                val eRatio = eEyeDist / (eWidth + 1e-6f)
                                                intrinsicDiff = Math.abs(sRatio - eRatio)
                                            }

                                            // Pack data into string for CSV parsing later
                                            val telemetryData = "%.2f,%.3f,%.1f,%.2f".format(score, intrinsicDiff, blurScore, shadowScore)
                                            newResults.add(Pair("$actualName,$actor", telemetryData))
                                        } else {
                                            newResults.add(Pair(uri.getFileName(context), "NO_EMBEDDING"))
                                        }
                                    } else {
                                        newResults.add(Pair(uri.getFileName(context), "NO_FACE"))
                                    }
                                } catch (e: Exception) {
                                    newResults.add(Pair(uri.getFileName(context), "ERROR")) // Error
                                }
                            }

                            results = newResults
                            isProcessing = false
                          } catch (e: Exception) {
                            android.util.Log.e("RNDTestScreen", "Crash running telemetry", e)
                            val newResults = mutableListOf<Pair<String, String>>()
                            newResults.add(Pair("FATAL ERROR", "CRASH"))
                            newResults.add(Pair(e.message ?: e.javaClass.simpleName, "CRASH"))
                            results = newResults
                            isProcessing = false
                          }
                        }
                    }
                ) {
                    Text(if (isProcessing) "Processing..." else "Run R&D Comparison")
                }

                Button(
                    enabled = results.isNotEmpty() && !isProcessing,
                    onClick = { saveCsvLauncher.launch("final_telemetry_analyzed.csv") }
                ) {
                    Text("Download CSV")
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    items(results) { res ->
                        val (nameActor, data) = res
                        val name = nameActor.split(",").firstOrNull() ?: nameActor
                        val scoreStr = data.split(",").firstOrNull() ?: data
                        Text("$name : $scoreStr")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close Harness")
                }
            }
        }
    }
}
