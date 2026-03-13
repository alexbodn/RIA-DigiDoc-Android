package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import kotlin.math.sqrt

class FaceRecognizer(
    context: Context,
) {
    private var interpreter: Interpreter? = null

    // FaceNet typically uses 160x160 input size and outputs a 128 or 512 dimension embedding
    private val inputImageWidth = 160
    private val inputImageHeight = 160
    private val outputSize = 128 // Typically 128 for FaceNet, adjust if 512

    init {
        try {
            // Because the Python script saves the model directly into assets folder:
            val modelBuffer = FileUtil.loadMappedFile(context, "facenet.tflite")
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Compares two face bitmaps and returns true if they match (cosine similarity > threshold).
     * The bitmaps passed here MUST be cropped tightly around the face.
     */
    fun compareFaces(
        face1: Bitmap,
        face2: Bitmap,
        threshold: Float = 0.7f,
    ): Boolean {
        if (interpreter == null) return false

        val emb1 = getFaceEmbedding(face1)
        val emb2 = getFaceEmbedding(face2)

        if (emb1 == null || emb2 == null) return false

        val similarity = cosineSimilarity(emb1, emb2)
        Log.d("FaceRecognizer", "Faces compared. Cosine Similarity: $similarity (Threshold: $threshold)")
        return similarity >= threshold
    }

    private fun getFaceEmbedding(bitmap: Bitmap): FloatArray? {
        val tflite = interpreter ?: return null

        // 1. Resize and preprocess the image
        val imageProcessor =
            ImageProcessor
                .Builder()
                .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
                // FaceNet typically requires normalization to [-1, 1]
                .add(NormalizeOp(127.5f, 127.5f))
                .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Prepare output tensor
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, outputSize), DataType.FLOAT32)

        // 3. Run inference
        tflite.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        // 4. L2 Normalize the embedding vector
        val embeddings = outputBuffer.floatArray
        var sumSq = 0f
        for (v in embeddings) sumSq += v * v
        val norm = sqrt(sumSq.toDouble()).toFloat()
        for (i in embeddings.indices) embeddings[i] /= norm

        return embeddings
    }

    private fun cosineSimilarity(
        v1: FloatArray,
        v2: FloatArray,
    ): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }

        return if (normA == 0.0f || normB == 0.0f) 0.0f else (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
