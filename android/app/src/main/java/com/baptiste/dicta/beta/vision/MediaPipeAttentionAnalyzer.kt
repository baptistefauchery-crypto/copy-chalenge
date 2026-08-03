package com.baptiste.dicta.beta.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class MediaPipeAttentionAnalyzer(
    context: Context,
    private val onReading: (AttentionReading) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val running = AtomicBoolean(true)
    private val stabilizer = AttentionStabilizer()
    private val recent = ArrayDeque<AttentionFeatures>()
    private val samples = mutableListOf<AttentionFeatures>()
    private var calibration: AttentionCalibration? = null
    private var collecting = false
    private val landmarker: FaceLandmarker

    init {
        val baseOptions = BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { onReading(AttentionReading(AttentionState.NOTEBOOK, 1.0, false, SystemClock.uptimeMillis())) }
            .build()
        landmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun beginCalibration() {
        samples.clear()
        recent.clear()
        calibration = null
        collecting = true
        stabilizer.reset()
    }

    fun finishCalibration(): Boolean {
        collecting = false
        if (samples.size < 5) return false
        calibration = AttentionCalibration(summarizeSamples(samples))
        return true
    }

    fun calibrationSnapshot(): AttentionCalibration? = calibration

    fun restoreCalibration(snapshot: AttentionCalibration?) {
        calibration = snapshot
        collecting = false
        samples.clear()
        recent.clear()
        stabilizer.reset()
    }

    override fun analyze(image: ImageProxy) {
        if (!running.get()) { image.close(); return }
        try {
            val bitmap = image.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
            // LIVE_STREAM consumes the image asynchronously. Do not recycle the
            // Bitmap here; MediaPipe may still be reading it on its worker thread.
        } catch (_: Throwable) {
            onReading(AttentionReading(stabilizer.loseFace(), 1.0, false, SystemClock.uptimeMillis()))
        } finally {
            image.close()
        }
    }

    private fun onResult(result: com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult) {
        val points = result.faceLandmarks().firstOrNull()?.map { Landmark(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) }
        val features = points?.let(::extractAttentionFeatures)
        val timestamp = SystemClock.uptimeMillis()
        if (features == null) {
            onReading(AttentionReading(stabilizer.loseFace(), 1.0, false, timestamp))
            return
        }
        recent.addLast(features)
        if (recent.size > 20) recent.removeFirst()
        if (collecting) samples += features
        val currentCalibration = calibration
        if (currentCalibration == null) {
            onReading(AttentionReading(AttentionState.UNKNOWN, 0.0, true, timestamp, features))
            return
        }
        val (raw, confidence) = classifyFeatures(features, currentCalibration)
        val state = stabilizer.update(raw, confidence, timestamp)
        onReading(AttentionReading(state, confidence, true, timestamp, features))
    }

    override fun close() {
        running.set(false)
        landmarker.close()
    }
}

internal fun ImageProxy.toBitmap(): Bitmap {
    val image = image ?: error("Camera image unavailable")
    if (format == ImageFormat.JPEG) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("JPEG camera frame conversion failed")
    }
    if (format == ImageFormat.YUV_420_888) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        var offset = ySize
        for (row in 0 until chromaHeight) {
            for (column in 0 until chromaWidth) {
                val uIndex = row * uPlane.rowStride + column * uPlane.pixelStride
                val vIndex = row * vPlane.rowStride + column * vPlane.pixelStride
                if (uIndex < uBuffer.limit() && vIndex < vBuffer.limit() && offset + 1 < nv21.size) {
                    nv21[offset++] = vBuffer.get(vIndex)
                    nv21[offset++] = uBuffer.get(uIndex)
                }
            }
        }
        val jpeg = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null).compressToJpeg(Rect(0, 0, width, height), 80, jpeg)
        return BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size())
            ?: error("Camera frame conversion failed")
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
}
