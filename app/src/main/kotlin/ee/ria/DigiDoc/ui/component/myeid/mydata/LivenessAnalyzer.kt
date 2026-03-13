package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import java.io.ByteArrayOutputStream

class LivenessAnalyzer(
    private val faceDetector: FaceDetector,
    private val onInstruction: (String) -> Unit,
    private val onLivenessVerified: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {
    private var currentInstructionState = LivenessState.LOOK_STRAIGHT
    private var headTurnedRight = false
    private var headTurnedLeft = false
    private var isVerified = false

    enum class LivenessState {
        LOOK_STRAIGHT,
        TURN_LEFT,
        TURN_RIGHT,
        VERIFIED,
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (isVerified) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector
                .process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        onInstruction("Face not found")
                        imageProxy.close()
                        return@addOnSuccessListener
                    }
                    if (faces.size > 1) {
                        onInstruction("Multiple faces detected")
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    val face = faces.first()
                    val rotY = face.headEulerAngleY // Head is turned to the right/left
                    val rotZ = face.headEulerAngleZ // Head is tilted

                    // Simple state machine for liveness
                    when (currentInstructionState) {
                        LivenessState.LOOK_STRAIGHT -> {
                            onInstruction("Look straight into the camera")
                            if (rotY in -10f..10f) {
                                currentInstructionState = LivenessState.TURN_LEFT
                            }
                        }
                        LivenessState.TURN_LEFT -> {
                            onInstruction("Turn your head slowly to the left")
                            if (rotY > 25f) { // User's left (mirror image)
                                headTurnedLeft = true
                                currentInstructionState = LivenessState.TURN_RIGHT
                            }
                        }
                        LivenessState.TURN_RIGHT -> {
                            onInstruction("Turn your head slowly to the right")
                            if (rotY < -25f) { // User's right
                                headTurnedRight = true
                                currentInstructionState = LivenessState.VERIFIED
                            }
                        }
                        LivenessState.VERIFIED -> {
                            onInstruction("Look straight into the camera to capture photo")
                            if (headTurnedLeft && headTurnedRight && !isVerified && rotY in -10f..10f) {
                                isVerified = true
                                val bitmap = imageProxyToBitmap(imageProxy)
                                if (bitmap != null) {
                                    val croppedFace = cropFace(bitmap, face.boundingBox)
                                    onLivenessVerified(croppedFace)
                                }
                            }
                        }
                    }
                    imageProxy.close()
                }.addOnFailureListener {
                    onInstruction("Face detection failed")
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val yBuffer = image.planes[0].buffer // Y
        val vuBuffer = image.planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val matrix = android.graphics.Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropFace(
        bitmap: Bitmap,
        boundingBox: Rect,
    ): Bitmap {
        var x = boundingBox.left
        var y = boundingBox.top
        var width = boundingBox.width()
        var height = boundingBox.height()

        // Ensure bounds are within the bitmap
        x = maxOf(0, x)
        y = maxOf(0, y)
        width = minOf(width, bitmap.width - x)
        height = minOf(height, bitmap.height - y)

        if (width <= 0 || height <= 0) return bitmap // Fallback

        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }
}
