package com.baptiste.dicta.beta.vision

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

class CameraCoordinator(
    context: Context,
    private val executor: Executor,
    private val onReading: (AttentionReading) -> Unit,
) : AutoCloseable {
    private val controller = LifecycleCameraController(context)
    private var analyzer: MediaPipeAttentionAnalyzer? = null
    private var calibration: AttentionCalibration? = null

    fun attachPreview(previewView: PreviewView, owner: LifecycleOwner) {
        controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        controller.setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
        if (analyzer == null) {
            analyzer = MediaPipeAttentionAnalyzer(previewView.context, onReading).also { it.restoreCalibration(calibration) }
        }
        controller.setImageAnalysisAnalyzer(executor, analyzer!!)
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

    fun detach() {
        controller.unbind()
    }

    override fun close() {
        analyzer?.close()
        analyzer = null
        detach()
    }

}
