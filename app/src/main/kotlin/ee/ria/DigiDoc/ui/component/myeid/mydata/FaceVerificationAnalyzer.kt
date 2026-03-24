package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector

class FaceVerificationAnalyzer(
    private val isFrontCamera: Boolean,
    private val faceDetector: FaceDetector,
    private val onStateChanged: (LivenessState) -> Unit,
    private val onPerfectFrameFound: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    private var isVerifying = false
    private var currentInstructionState = LivenessState.LOOK_FRONT
    private var headTurnedLeft = false
    private var headTurnedRight = false
    private var isLivenessVerified = false

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isVerifying) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        onStateChanged(LivenessState.NO_FACE)
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    val face = faces.first()

                    val rotY = face.headEulerAngleY

                    // LIVENESS CHECK PHASE (Head turning)
                    if (!isLivenessVerified) {
                        when (currentInstructionState) {
                            LivenessState.LOOK_FRONT -> {
                                onStateChanged(LivenessState.LOOK_FRONT)
                                if (rotY in -FaceVerificationConfig.MAX_YAW_ANGLE..FaceVerificationConfig.MAX_YAW_ANGLE) {
                                    currentInstructionState = LivenessState.TURN_LEFT
                                }
                            }
                            LivenessState.TURN_LEFT -> {
                                onStateChanged(LivenessState.TURN_LEFT)
                                if (rotY > 15f) headTurnedLeft = true
                                else if (rotY < -15f) headTurnedRight = true

                                if (headTurnedLeft || headTurnedRight) {
                                    currentInstructionState = LivenessState.TURN_RIGHT
                                }
                            }
                            LivenessState.TURN_RIGHT -> {
                                onStateChanged(LivenessState.TURN_RIGHT)
                                if (!headTurnedLeft && rotY > 15f) headTurnedLeft = true
                                else if (!headTurnedRight && rotY < -15f) headTurnedRight = true

                                if (headTurnedLeft && headTurnedRight) {
                                    isLivenessVerified = true
                                    currentInstructionState = LivenessState.LOOK_FRONT
                                }
                            }
                            else -> { }
                        }

                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    // 1. FAST GEOMETRY CHECK (Euler Angles from ML Kit) - Post Liveness
                    if (Math.abs(rotY) > FaceVerificationConfig.MAX_YAW_ANGLE ||
                        Math.abs(face.headEulerAngleZ) > FaceVerificationConfig.MAX_TILT_ANGLE) {
                        onStateChanged(LivenessState.LOOK_FRONT)
                        imageProxy.close()
                        return@addOnSuccessListener
                    }


                    // 1b. FACE SIZE CHECK (Must not be a tiny fridge magnet)
                    if (face.boundingBox.width() < FaceVerificationConfig.MIN_FACE_WIDTH) {
                        onStateChanged(LivenessState.TOO_FAR)
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    // 2. CONVERT TO BITMAP FOR PIXEL MATH
                    // Transform for mirroring and rotation before crop so the face boundaries align properly visually
                    val bitmap = imageProxy.toBitmap()
                    val matrix = Matrix()
                    matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    if (isFrontCamera) {
                        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                    }
                    val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                    val faceCrop = cropFaceFromBitmap(finalBitmap, face.boundingBox, face)

                    // 3. LIGHTING & BLUR CHECKS
                    if (calculateBrightness(faceCrop) < FaceVerificationConfig.BRIGHTNESS_TOO_DARK) {
                        onStateChanged(LivenessState.TOO_DARK)
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    if (calculateLaplacianVariance(faceCrop) < FaceVerificationConfig.BLUR_THRESHOLD) {
                        onStateChanged(LivenessState.TOO_BLURRY)
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    // 4. PASSED GATEKEEPER - LOCK AND VERIFY
                    isVerifying = true
                    onStateChanged(LivenessState.PROCESSING)

                    val readyForNet = Bitmap.createScaledBitmap(
                        faceCrop,
                        FaceVerificationConfig.TARGET_FACE_SIZE,
                        FaceVerificationConfig.TARGET_FACE_SIZE,
                        true
                    )
                    onPerfectFrameFound(readyForNet)

                    imageProxy.close()
                }
                .addOnFailureListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    fun resume() {
        isVerifying = false
    }

    // Add alignment logic using landmarks directly if available
    private fun cropFaceFromBitmap(bitmap: Bitmap, bounds: Rect, face: com.google.mlkit.vision.face.Face? = null): Bitmap {
        var processingBitmap = bitmap

        // 🟢 Python R&D Port: Affine Eye Alignment
        if (face != null) {
            val leftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)

            if (leftEye != null && rightEye != null) {
                val dx = (rightEye.position.x - leftEye.position.x).toDouble()
                val dy = (rightEye.position.y - leftEye.position.y).toDouble()
                val angle = Math.toDegrees(Math.atan2(dy, dx)).toFloat()

                // Only rotate if angle is significant
                if (Math.abs(angle) > 2.0f) {
                    val center = android.graphics.PointF(
                        (leftEye.position.x + rightEye.position.x) / 2f,
                        (leftEye.position.y + rightEye.position.y) / 2f
                    )
                    val matrix = Matrix()
                    matrix.postRotate(angle, center.x, center.y)
                    processingBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }
            }
        }

        // 🟢 Python R&D Port: Centered square bounding crop
        var x = bounds.left
        var y = bounds.top
        var w = bounds.width()
        var h = bounds.height()

        // Make it a perfect square based on max dimension
        val size = maxOf(w, h)
        val cx = x + w / 2
        val cy = y + h / 2

        x = cx - size / 2
        y = cy - size / 2

        x = maxOf(0, x)
        y = maxOf(0, y)
        val finalSize = minOf(size, minOf(processingBitmap.width - x, processingBitmap.height - y))

        if (finalSize <= 0) return processingBitmap

        return Bitmap.createBitmap(processingBitmap, x, y, finalSize, finalSize)
    }

    private fun calculateBrightness(bitmap: Bitmap): Float {
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (color in pixels) {
            sumR += Color.red(color)
            sumG += Color.green(color)
            sumB += Color.blue(color)
        }

        val totalPixels = width * height
        if (totalPixels == 0) return 0f

        val avgR = sumR / totalPixels.toFloat()
        val avgG = sumG / totalPixels.toFloat()
        val avgB = sumB / totalPixels.toFloat()

        // Standard luminance formula
        return 0.299f * avgR + 0.587f * avgG + 0.114f * avgB
    }

    private fun calculateLaplacianVariance(bitmap: Bitmap): Float {
        // A simple approximation of blur using a 3x3 Laplacian kernel
        // Since we cannot load OpenCV easily, we'll manually apply a basic Laplace filter and get variance.
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to grayscale
        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        val laplacian = DoubleArray(width * height)
        var sum = 0.0

        // 3x3 Laplacian: [0, 1, 0; 1, -4, 1; 0, 1, 0]
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val center = gray[idx]
                val top = gray[idx - width]
                val bottom = gray[idx + width]
                val left = gray[idx - 1]
                val right = gray[idx + 1]

                val value = top + bottom + left + right - 4 * center
                laplacian[idx] = value.toDouble()
                sum += value
            }
        }

        val count = (width - 2) * (height - 2)
        if (count <= 0) return 0f

        val mean = sum / count
        var varianceSum = 0.0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val diff = laplacian[idx] - mean
                varianceSum += diff * diff
            }
        }

        return (varianceSum / count).toFloat()
    }
}
