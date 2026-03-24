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
    var results by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    val saveCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                out.write("Filename,Actor,Match_%")
                out.newLine()
                for (res in results) {
                    val parts = res.first.split(" | ")
                    val actor = if (parts.size > 1) parts[0] else "Unknown"
                    val filename = if (parts.size > 1) parts[1] else parts[0]
                    out.write(filename + "," + actor + "," + res.second.toString())
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

                            // 1. Process DG2
                            val wrappedDG2 = CylinderWrap.wrapFlatToCylinder(dg2Bitmap!!, 1.2f)
                            var dg2Cropped: Bitmap = wrappedDG2
                            val dg2Input = InputImage.fromBitmap(wrappedDG2, 0)
                            val dg2Faces = Tasks.await(faceDetector.process(dg2Input))

                            if (dg2Faces.isNotEmpty()) {
                                val face = dg2Faces.first()
                                val bounds = face.boundingBox
                                var alignedBmp = wrappedDG2

                                val leftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
                                val rightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)

                                if (leftEye != null && rightEye != null) {
                                    val dx = (rightEye.position.x - leftEye.position.x).toDouble()
                                    val dy = (rightEye.position.y - leftEye.position.y).toDouble()
                                    val angle = Math.toDegrees(Math.atan2(dy, dx)).toFloat()
                                    val matrix = android.graphics.Matrix()
                                    matrix.postRotate(angle, (leftEye.position.x + rightEye.position.x)/2f, (leftEye.position.y + rightEye.position.y)/2f)
                                    alignedBmp = Bitmap.createBitmap(alignedBmp, 0, 0, alignedBmp.width, alignedBmp.height, matrix, true)
                                }

                                val size = maxOf(bounds.width(), bounds.height())
                                val cx = bounds.left + bounds.width()/2
                                val cy = bounds.top + bounds.height()/2
                                val x = maxOf(0, cx - size/2)
                                val y = maxOf(0, cy - size/2)
                                val fSize = minOf(size, minOf(alignedBmp.width - x, alignedBmp.height - y))

                                dg2Cropped = Bitmap.createBitmap(alignedBmp, x, y, fSize, fSize)
                            }

                            val dg2Emb = engine.getEmbedding(dg2Cropped) ?: FloatArray(0)

                            // 2. Process Selfies Batch
                            val newResults = mutableListOf<Pair<String, Float>>()
                            for (uri in selfieUris) {
                                try {
                                    val stream = context.contentResolver.openInputStream(uri)
                                    val selfieBmp = BitmapFactory.decodeStream(stream)

                                    val selfieInput = InputImage.fromBitmap(selfieBmp, 0)
                                    val faces = Tasks.await(faceDetector.process(selfieInput))

                                    if (faces.isNotEmpty()) {
                                        var selfieCropped: Bitmap
                                        val face = faces.first()
                                        val bounds = face.boundingBox
                                        var alignedBmp = selfieBmp

                                        val leftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
                                        val rightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)

                                        if (leftEye != null && rightEye != null) {
                                            val dx = (rightEye.position.x - leftEye.position.x).toDouble()
                                            val dy = (rightEye.position.y - leftEye.position.y).toDouble()
                                            val angle = Math.toDegrees(Math.atan2(dy, dx)).toFloat()
                                            val matrix = android.graphics.Matrix()
                                            matrix.postRotate(angle, (leftEye.position.x + rightEye.position.x)/2f, (leftEye.position.y + rightEye.position.y)/2f)
                                            alignedBmp = Bitmap.createBitmap(alignedBmp, 0, 0, alignedBmp.width, alignedBmp.height, matrix, true)
                                        }

                                        val size = maxOf(bounds.width(), bounds.height())
                                        val cx = bounds.left + bounds.width()/2
                                        val cy = bounds.top + bounds.height()/2
                                        val x = maxOf(0, cx - size/2)
                                        val y = maxOf(0, cy - size/2)
                                        val fSize = minOf(size, minOf(alignedBmp.width - x, alignedBmp.height - y))

                                        selfieCropped = Bitmap.createBitmap(alignedBmp, x, y, fSize, fSize)

                                        val selfieEmb = engine.getEmbedding(selfieCropped)
                                        if (selfieEmb != null) {
                                            val score = engine.calculateCosineSimilarity(dg2Emb, selfieEmb)

                                            val actualName = uri.getFileName(context)

                                            val actor = csvData.entries.find { actualName.contains(it.key) }?.value ?: "Unknown"
                                            newResults.add(Pair("$actor | $actualName", score))
                                        } else {
                                            newResults.add(Pair(uri.getFileName(context), -1f))
                                        }
                                    } else {
                                        newResults.add(Pair(uri.getFileName(context), -2f)) // No face found
                                    }
                                } catch (e: Exception) {
                                    newResults.add(Pair(uri.getFileName(context), -3f)) // Error
                                }
                            }

                            results = newResults
                            isProcessing = false
                          } catch (e: Exception) {
                            android.util.Log.e("RNDTestScreen", "Crash running telemetry", e)
                            val newResults = mutableListOf<Pair<String, Float>>()
                            newResults.add(Pair("FATAL ERROR", -4f))
                            newResults.add(Pair(e.message ?: e.javaClass.simpleName, -4f))
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
                        val status = when {
                            res.second == -1f -> "No Embedding"
                            res.second == -2f -> "No Face Found"
                            res.second == -3f -> "Error Reading"
                            res.second == -4f -> "CRASH"
                            else -> "%.2f%%".format(res.second)
                        }
                        Text("${res.first} : $status")
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
