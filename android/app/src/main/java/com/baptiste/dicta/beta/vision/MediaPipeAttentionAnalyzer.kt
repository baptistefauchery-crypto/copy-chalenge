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
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MediaPipeAttentionAnalyzer(
    context: Context,
    private val onReading: (AttentionReading) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val running = AtomicBoolean(true)
    private val inferenceInFlight = AtomicBoolean(false)
    private val pendingImage = AtomicReference<MPImage?>(null)
    private val stabilizer = AttentionStabilizer()
    private val recent = ArrayDeque<AttentionFeatures>()
    private val samples = mutableListOf<AttentionFeatures>()
    private var calibration: AttentionCalibration? = null
    private var collecting = false
    private var lastAnalysisAt: Long? = null
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
            .setResultListener { result, inputImage ->
                try {
                    onResult(result)
                } finally {
                    if (pendingImage.compareAndSet(inputImage, null)) inputImage.close()
                    inferenceInFlight.set(false)
                }
            }
            .setErrorListener {
                pendingImage.getAndSet(null)?.close()
                inferenceInFlight.set(false)
                onReading(AttentionReading(AttentionState.NOTEBOOK, 1.0, false, SystemClock.uptimeMillis()))
            }
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
        val timestamp = SystemClock.uptimeMillis()
        if (!shouldAnalyzeFrame(lastAnalysisAt, timestamp) || !inferenceInFlight.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastAnalysisAt = timestamp
        var bitmap: Bitmap? = null
        var mpImage: MPImage? = null
        try {
            bitmap = image.toBitmap()
            mpImage = BitmapImageBuilder(bitmap).build()
            bitmap = null // MPImage owns and recycles the bitmap from this point.
            pendingImage.set(mpImage)
            val processingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(image.imageInfo.rotationDegrees)
                .build()
            landmarker.detectAsync(mpImage, processingOptions, timestamp)
            // The result callback closes the MPImage after MediaPipe is done;
            // closing it also recycles the BitmapImageBuilder bitmap.
        } catch (_: Throwable) {
            if (mpImage != null && pendingImage.compareAndSet(mpImage, null)) mpImage.close()
            else bitmap?.recycle()
            inferenceInFlight.set(false)
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
        pendingImage.getAndSet(null)?.close()
        inferenceInFlight.set(false)
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
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
        val nv21 = yuv420ToNv21(
            width = width,
            height = height,
            yPlane = yPlane.toYuvPlane(),
            uPlane = uPlane.toYuvPlane(),
            vPlane = vPlane.toYuvPlane(),
        )
        val jpeg = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null).compressToJpeg(Rect(0, 0, width, height), 80, jpeg)
        return BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size())
            ?: error("Camera frame conversion failed")
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
}

internal data class YuvPlane(
    val bytes: ByteArray,
    val rowStride: Int,
    val pixelStride: Int,
)

private fun android.media.Image.Plane.toYuvPlane(): YuvPlane {
    val copy = buffer.duplicate()
    val bytes = ByteArray(copy.remaining())
    copy.get(bytes)
    return YuvPlane(bytes, rowStride, pixelStride)
}

/** Converts strided YUV_420_888 planes to tightly packed NV21. */
internal fun yuv420ToNv21(
    width: Int,
    height: Int,
    yPlane: YuvPlane,
    uPlane: YuvPlane,
    vPlane: YuvPlane,
): ByteArray {
    require(width > 0 && height > 0) { "YUV dimensions must be positive" }
    val chromaWidth = (width + 1) / 2
    val chromaHeight = (height + 1) / 2
    val nv21 = ByteArray(width * height + chromaWidth * chromaHeight * 2)

    fun read(plane: YuvPlane, row: Int, column: Int): Byte {
        val index = row * plane.rowStride + column * plane.pixelStride
        require(index in plane.bytes.indices) { "YUV plane does not contain the requested pixel" }
        return plane.bytes[index]
    }

    var offset = 0
    repeat(height) { row ->
        repeat(width) { column ->
            nv21[offset++] = read(yPlane, row, column)
        }
    }
    repeat(chromaHeight) { row ->
        repeat(chromaWidth) { column ->
            nv21[offset++] = read(vPlane, row, column)
            nv21[offset++] = read(uPlane, row, column)
        }
    }
    return nv21
}
