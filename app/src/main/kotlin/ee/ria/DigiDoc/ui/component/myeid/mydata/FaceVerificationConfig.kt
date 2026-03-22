package ee.ria.DigiDoc.ui.component.myeid.mydata

object FaceVerificationConfig {
    // Core ML Settings
    const val TARGET_FACE_SIZE = 112 // MobileFaceNet requires exactly 112x112
    const val MATCH_THRESHOLD_PERCENT = 76.0f
    const val MIN_FACE_WIDTH = 150 // Minimum bounding box pixel width required to run comparison // Strict Cosine Distance match lock

    // Geometry & Liveness (Google ML Kit hardware thresholds)
    const val MAX_YAW_ANGLE = 12.0f   // Left/Right turn
    const val MAX_TILT_ANGLE = 12.0f  // Ear to shoulder tilt
    const val INTRINSIC_RATIO_TOLERANCE = 0.08f // Eye-distance to face-width ratio variance

    // Lighting & Image Quality (Pixel math thresholds)
    const val BRIGHTNESS_TOO_DARK = 60.0f
    const val BLUR_THRESHOLD = 45.0f // Laplacian variance threshold
    const val SHADOW_RATIO_THRESHOLD = 0.40f // Eye-brightness vs Cheek-brightness
}

enum class LivenessState(val prompt: String) {
    NO_FACE("Position your face in the frame"),
    TURN_LEFT("Turn your head slightly to one side"),
    TURN_RIGHT("Turn your head to the other side"),
    LOOK_FRONT("Please look directly at the camera"),
    HOLD_STILL("Hold still..."),
    TOO_DARK("Move into better lighting"),
    TOO_BLURRY("Wipe your lens or hold still"),
    TOO_FAR("Move your face closer to the camera"),
    PROCESSING("Verifying... Please wait"),
    MATCHED("Verification Complete! ✅"),
    FAILED("Verification Failed ❌")
}
