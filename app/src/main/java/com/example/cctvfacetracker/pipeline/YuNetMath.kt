package com.example.cctvfacetracker.pipeline

/**
 * Matches OpenCV's FaceDetectorYN::postProcess score combination: the raw cls/obj outputs are
 * already bounded (no extra sigmoid -- applying one squashes everything toward 0.5 and destroys
 * the face/background contrast), clamped and combined via geometric mean.
 */
fun yunetScore(clsRaw: Float, objRaw: Float): Float {
    val cls = clsRaw.coerceIn(0f, 1f)
    val obj = objRaw.coerceIn(0f, 1f)
    return Math.sqrt((cls * obj).toDouble()).toFloat()
}

data class DecodedBox(val cx: Float, val cy: Float, val w: Float, val h: Float)

/** Decodes a stride-relative box prediction (dx, dy offsets + log-space dw, dh) to pixel-space. */
fun yunetDecodeBox(col: Int, row: Int, stride: Int, dx: Float, dy: Float, dw: Float, dh: Float): DecodedBox {
    val cx = (col + dx) * stride
    val cy = (row + dy) * stride
    val w = Math.exp(dw.toDouble()).toFloat() * stride
    val h = Math.exp(dh.toDouble()).toFloat() * stride
    return DecodedBox(cx, cy, w, h)
}

/** Decodes a single stride-relative keypoint offset to pixel-space. */
fun yunetDecodePoint(col: Int, row: Int, stride: Int, dx: Float, dy: Float): Pair<Float, Float> =
    (col + dx) * stride to (row + dy) * stride
