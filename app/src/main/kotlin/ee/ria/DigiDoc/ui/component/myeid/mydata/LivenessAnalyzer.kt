package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector

class LivenessAnalyzer(
    private val faceDetector: FaceDetector,
    private val onInstruction: (String) -> Unit,
    private val onStepSuccess: (String) -> Unit,
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
            // Because ML Kit bounding boxes relate to the unrotated image,
            // but we eventually display a rotated/mirrored image to the user,
            // we must map the bounding box.
            // Let's create the final rotated/mirrored bitmap FIRST, then detect on that.

            val bitmap = imageProxy.toBitmap()
            val matrix = android.graphics.Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // Pass the already rotated/mirrored bitmap to ML Kit (rotation is now 0)
            val image = InputImage.fromBitmap(finalBitmap, 0)

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
                    Log.i("LivenessAnalyzer", "Face rotY: $rotY, state: $currentInstructionState")

                    // Allow turning either direction first to be forgiving
                    when (currentInstructionState) {
                        LivenessState.LOOK_STRAIGHT -> {
                            onInstruction("Look straight into the camera")
                            if (rotY in -12f..12f) {
                                currentInstructionState = LivenessState.TURN_LEFT
                            }
                        }
                        LivenessState.TURN_LEFT -> {
                            onInstruction("Turn your head to one side")
                            if (rotY > 15f) {
                                headTurnedLeft = true
                            } else if (rotY < -15f) {
                                headTurnedRight = true
                            }

                            if (headTurnedLeft || headTurnedRight) {
                                currentInstructionState = LivenessState.TURN_RIGHT
                            }
                        }
                        LivenessState.TURN_RIGHT -> {
                            onInstruction("Turn your head to the other side")
                            if (!headTurnedLeft && rotY > 15f) {
                                headTurnedLeft = true
                            } else if (!headTurnedRight && rotY < -15f) {
                                headTurnedRight = true
                            }

                            if (headTurnedLeft && headTurnedRight) {
                                currentInstructionState = LivenessState.VERIFIED
                            }
                        }
                        LivenessState.VERIFIED -> {
                            onInstruction("Look straight into the camera to capture photo")
                            if (headTurnedLeft && headTurnedRight && !isVerified && rotY in -12f..12f) {
                                isVerified = true
                                val croppedFace = cropFace(finalBitmap, face.boundingBox)
                                onLivenessVerified(croppedFace)
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
