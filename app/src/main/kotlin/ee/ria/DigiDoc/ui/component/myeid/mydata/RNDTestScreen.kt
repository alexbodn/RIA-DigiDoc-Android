package ee.ria.DigiDoc.ui.component.myeid.mydata


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


import kotlinx.coroutines.tasks.await

@Composable
fun RNDTestScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()


    var csvUri by remember { mutableStateOf<Uri?>(null) }
    var csvData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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

    val dg2Launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        dg2Uri = uri
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            dg2Bitmap = BitmapFactory.decodeStream(inputStream)
        }
    }

    val selfiesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
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
                        Button(onClick = { csvLauncher.launch("text/csv") }) {
                Text("Upload CSV")
            }
            Button(onClick = { dg2Launcher.launch("image/*") }) {
                Text("Upload DG2")
            }
            Button(onClick = { selfiesLauncher.launch("image/*") }) {
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
                    val engine = MobileFaceNetEngine(context)
                    val detectorOptions = FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build()
                    val faceDetector = FaceDetection.getClient(detectorOptions)

                    // 1. Process DG2
                    val wrappedDG2 = CylinderWrap.wrapFlatToCylinder(dg2Bitmap!!, 1.2f)
                    var dg2Cropped: Bitmap = wrappedDG2
                    try {
                        val dg2Input = InputImage.fromBitmap(wrappedDG2, 0)
                            val faces = Tasks.await(faceDetector.process(dg2Input))
                        if (faces.isNotEmpty()) {
                            val bounds = faces.first().boundingBox
                            dg2Cropped = Bitmap.createBitmap(
                                wrappedDG2,
                                maxOf(0, bounds.left), maxOf(0, bounds.top),
                                minOf(bounds.width(), wrappedDG2.width - maxOf(0, bounds.left)),
                                minOf(bounds.height(), wrappedDG2.height - maxOf(0, bounds.top))
                            )
                        }
                    } catch (e: Exception) { e.printStackTrace() }

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
                                val bounds = faces.first().boundingBox
                                val selfieCropped = Bitmap.createBitmap(
                                    selfieBmp,
                                    maxOf(0, bounds.left), maxOf(0, bounds.top),
                                    minOf(bounds.width(), selfieBmp.width - maxOf(0, bounds.left)),
                                    minOf(bounds.height(), selfieBmp.height - maxOf(0, bounds.top))
                                )
                                val selfieEmb = engine.getEmbedding(selfieCropped)
                                if (selfieEmb != null) {
                                    val score = engine.calculateCosineSimilarity(dg2Emb, selfieEmb)

                                    val filename = uri.lastPathSegment ?: "Unknown"
                                    val actualName = filename.split("/").last() // sometimes DocumentProvider includes folders

                                    val actor = csvData.entries.find { actualName.contains(it.key) }?.value ?: "Unknown"
                                    newResults.add(Pair("$actor | $actualName", score))
                                } else {
                                    newResults.add(Pair(uri.lastPathSegment ?: "Unknown", -1f))
                                }
                            } else {
                                newResults.add(Pair(uri.lastPathSegment ?: "Unknown", -2f)) // No face found
                            }
                        } catch (e: Exception) {
                            newResults.add(Pair(uri.lastPathSegment ?: "Unknown", -3f)) // Error
                        }
                    }

                    results = newResults
                    isProcessing = false
                }
            }
        ) {
            Text(if (isProcessing) "Processing..." else "Run R&D Comparison")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(results) { res ->
                val status = when {
                    res.second == -1f -> "No Embedding"
                    res.second == -2f -> "No Face Found"
                    res.second == -3f -> "Error Reading"
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
