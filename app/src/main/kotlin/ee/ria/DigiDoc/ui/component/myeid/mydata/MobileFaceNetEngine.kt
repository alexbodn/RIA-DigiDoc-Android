package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MobileFaceNetEngine(
    context: Context,
) {
    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, "facenet.tflite")
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getEmbedding(bitmap: Bitmap): FloatArray? {
        val tflite = interpreter ?: return null

        val inputTensor = tflite.getInputTensor(0)
        val shape = inputTensor.shape() // Expected: [batchSize, targetH, targetW, 3]
        val batchSize = shape[0]
        val targetH = shape[1]
        val targetW = shape[2]

        // Allocate buffer: batchSize * height * width * channels * 4 bytes per float
        val byteBuffer = ByteBuffer.allocateDirect(batchSize * targetH * targetW * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        // 1. Normalize Bitmap pixels: (pixel - 127.5) / 128.0
        val floatValues = FloatArray(targetH * targetW * 3)
        var idx = 0

        val scaledBitmap = if (bitmap.width != targetW || bitmap.height != targetH) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val pixels = IntArray(targetW * targetH)
        scaledBitmap.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            // Typical FaceNet Normalization: (val - 127.5) / 128.0
            floatValues[idx++] = (r - 127.5f) / 128.0f
            floatValues[idx++] = (g - 127.5f) / 128.0f
            floatValues[idx++] = (b - 127.5f) / 128.0f
        }

        // 2. THE SIAMESE BYPASS: Fill the buffer based on required batch size
        for (i in 0 until batchSize) {
            for (value in floatValues) {
                byteBuffer.putFloat(value)
            }
        }

        // 3. Invoke TFLite
        val outputTensorShape = tflite.getOutputTensor(0).shape() // e.g., [batchSize, 192]
        val embeddingSize = outputTensorShape[1]
        val outputBuffer = Array(batchSize) { FloatArray(embeddingSize) }

        tflite.run(byteBuffer, outputBuffer)

        // Return just the embedding for the 0th image
        return outputBuffer[0]
    }

    fun calculateCosineSimilarity(emb1: FloatArray, emb2: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in emb1.indices) {
            dotProduct += emb1[i] * emb2[i]
            normA += emb1[i] * emb1[i]
            normB += emb2[i] * emb2[i]
        }
        val distance = 1.0f - (dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble()))).toFloat()
        return (1.0f - distance) * 100.0f
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
