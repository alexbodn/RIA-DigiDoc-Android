package ee.ria.DigiDoc.ui.component.myeid.mydata

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.tan

object CylinderWrap {
    /**
     * Simulates the 3D 'bulge' of a smartphone lens by mapping a flat image onto a cylinder.
     * Equivalent to Python cv2.remap logic provided in R&D.
     */
    fun wrapFlatToCylinder(img: Bitmap, focalLengthFactor: Float = 1.2f): Bitmap {
        val w = img.width
        val h = img.height
        val focalLength = w * focalLengthFactor
        val xc = w / 2.0f
        val yc = h / 2.0f

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h)
        img.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val destPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val theta = (x - xc) / focalLength

                // The inverse mapping: where do we pull the pixel from?
                val xSrc = (focalLength * tan(theta) + xc).toInt()
                val ySrc = ((y - yc) / cos(theta) + yc).toInt()

                val destIdx = y * w + x
                if (xSrc in 0 until w && ySrc in 0 until h) {
                    val srcIdx = ySrc * w + xSrc
                    destPixels[destIdx] = srcPixels[srcIdx]
                } else {
                    destPixels[destIdx] = Color.BLACK
                }
            }
        }
        result.setPixels(destPixels, 0, w, 0, 0, w, h)
        return result
    }
}
