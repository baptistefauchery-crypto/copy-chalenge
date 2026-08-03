package com.baptiste.dicta.beta.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

class CameraCoordinator(
    context: Context,
    private val executor: Executor,
    private val onReading: (AttentionReading) -> Unit,
) : AutoCloseable {
    private val controller = LifecycleCameraController(context)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var analyzer: MediaPipeAttentionAnalyzer? = null
    private var calibration: AttentionCalibration? = null

    fun attachPreview(previewView: PreviewView, owner: LifecycleOwner, mode: CameraMode) {
        controller.cameraSelector = if (mode == CameraMode.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.IMAGE_ANALYSIS)
        if (mode == CameraMode.FRONT) {
            if (analyzer == null) {
                analyzer = MediaPipeAttentionAnalyzer(previewView.context, onReading).also { it.restoreCalibration(calibration) }
            }
            controller.setImageAnalysisAnalyzer(executor, analyzer!!)
        } else {
            analyzer?.close()
            analyzer = null
            controller.clearImageAnalysisAnalyzer()
        }
        previewView.controller = controller
        controller.bindToLifecycle(owner)
    }

    fun beginCalibration() {
        calibration = null
        analyzer?.beginCalibration()
    }

    fun finishCalibration(): Boolean {
        val success = analyzer?.finishCalibration() == true
        calibration = if (success) analyzer?.calibrationSnapshot() else null
        return success
    }

    fun capture(onCaptured: (Bitmap) -> Unit, onError: (Throwable) -> Unit) {
        controller.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    var bitmap = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    if (rotation != 0) {
                        val rotated = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            Matrix().apply { postRotate(rotation.toFloat()) },
                            true,
                        )
                        if (rotated !== bitmap) bitmap.recycle()
                        bitmap = rotated
                    }
                    mainExecutor.execute { onCaptured(bitmap) }
                } catch (error: Throwable) {
                    mainExecutor.execute { onError(error) }
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                mainExecutor.execute { onError(exception) }
            }
        })
    }

    fun detach() {
        controller.unbind()
    }

    override fun close() {
        analyzer?.close()
        analyzer = null
        detach()
    }
}

enum class CameraMode { FRONT, BACK }
