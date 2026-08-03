package com.baptiste.dicta.beta.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.lang.reflect.Array
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.exp

/**
 * On-device PP-OCRv6 adapter. The detector finds horizontal text bands in the
 * captured sheet, then the recognizer is run once per band. Both models and
 * their character dictionary are bundled in the APK, so this path is offline.
 */
class OnnxOcrEngine(private val assets: android.content.res.AssetManager) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private var detector: OrtSession? = null
    private var recognizer: OrtSession? = null
    private var dictionary: List<String>? = null

    suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        runCatching {
            val detectedLines = detectLineCrops(bitmap)
            val crops = if (detectedLines.isEmpty()) listOf(bitmap) else detectedLines
            val recognized = crops.mapNotNull { crop ->
                try {
                    recognizeLine(crop).takeIf { it.text.isNotBlank() }
                } finally {
                    if (crop !== bitmap) crop.recycle()
                }
            }
            if (recognized.isEmpty()) {
                OcrResult("", 0.0)
            } else {
                OcrResult(
                    text = recognized.joinToString(" ") { it.text }.trim(),
                    confidence = recognized.map { it.confidence }.average().coerceIn(0.0, 1.0),
                )
            }
        }.getOrElse { OcrResult("", 0.0) }
    }

    private fun recognizeLine(source: Bitmap): OcrResult {
        val session = recognizerSession()
        val inputName = session.inputNames.first()
        val input = imageTensor(source, 320, 48, preserveAspect = true)
        input.use { tensor ->
            session.run(mapOf(inputName to tensor)).use { outputs ->
                return decodeRecognition(outputs[0].value)
            }
        }
    }

    /**
     * Runs PP-OCR's DB detector and turns its probability map into horizontal
     * bands. The beta capture is intentionally line-oriented: it is easier for
     * a child to frame and avoids uploading or storing a full sheet.
     */
    private fun detectLineCrops(source: Bitmap): List<Bitmap> {
        val (inputWidth, inputHeight) = detectionDimensions(source)
        val session = detectorSession()
        val inputName = session.inputNames.first()
        val input = imageTensor(source, inputWidth, inputHeight)
        input.use { tensor ->
            session.run(mapOf(inputName to tensor)).use { outputs ->
                val value = outputs[0].value
                val scores = flatten(value)
                val shape = actualShape(value)
                val mapWidth = shape?.lastOrNull()?.takeIf { it > 0 } ?: inputWidth
                val mapHeight = shape?.getOrNull(shape.size - 2)?.takeIf { it > 0 } ?: inputHeight
                if (mapWidth <= 0 || mapHeight <= 0 || scores.size < mapWidth * mapHeight) return emptyList()

                val threshold = 0.2f
                val minimumActiveCells = max(2, mapWidth / 256)
                val bands = mutableListOf<DetectionBand>()
                var startRow = -1
                var lastActiveRow = -1
                var minX = mapWidth
                var maxX = -1
                var y = 0
                while (y < mapHeight) {
                    val rowOffset = y * mapWidth
                    var rowActive = 0
                    var rowMinX = mapWidth
                    var rowMaxX = -1
                    for (x in 0 until mapWidth) {
                        if (scores[rowOffset + x] >= threshold) {
                            rowActive++
                            rowMinX = min(rowMinX, x)
                            rowMaxX = max(rowMaxX, x)
                        }
                    }
                    if (rowActive >= minimumActiveCells) {
                        if (startRow < 0 || y - lastActiveRow <= 2) {
                            if (startRow < 0) startRow = y
                            minX = min(minX, rowMinX)
                            maxX = max(maxX, rowMaxX)
                        } else {
                            bands += DetectionBand(startRow, lastActiveRow, minX, maxX)
                            startRow = y
                            minX = rowMinX
                            maxX = rowMaxX
                        }
                        lastActiveRow = y
                    }
                    y++
                }
                if (startRow >= 0) bands += DetectionBand(startRow, lastActiveRow, minX, maxX)

                return bands.mapNotNull { band ->
                    if (band.maxX <= band.minX || band.bottom <= band.top) return@mapNotNull null
                    val scaleX = source.width.toDouble() / mapWidth
                    val scaleY = source.height.toDouble() / mapHeight
                    val paddingX = max(8, ((band.maxX - band.minX) * scaleX * 0.03).roundToInt())
                    val paddingY = max(8, ((band.bottom - band.top) * scaleY * 0.35).roundToInt())
                    val left = (band.minX * scaleX).roundToInt().minus(paddingX).coerceIn(0, source.width - 1)
                    val top = (band.top * scaleY).roundToInt().minus(paddingY).coerceIn(0, source.height - 1)
                    val right = (band.maxX * scaleX).roundToInt().plus(paddingX).coerceIn(left + 1, source.width)
                    val bottom = (band.bottom * scaleY).roundToInt().plus(paddingY).coerceIn(top + 1, source.height)
                    val width = right - left
                    val height = bottom - top
                    if (width < 16 || height < 8 || width.toLong() * height > source.width.toLong() * source.height * 0.95) {
                        null
                    } else {
                        Bitmap.createBitmap(source, left, top, width, height)
                    }
                }
            }
        }
    }

    private fun detectionDimensions(source: Bitmap): Pair<Int, Int> {
        val longest = max(source.width, source.height).coerceAtLeast(1)
        val scale = min(1.0, 960.0 / longest)
        fun aligned(value: Int): Int = (((value * scale).roundToInt().coerceAtLeast(32) + 31) / 32) * 32
        return aligned(source.width) to aligned(source.height)
    }

    private fun detectorSession(): OrtSession {
        if (detector == null) detector = environment.createSession(
            assets.open("PP-OCRv6_small_det_onnx_infer/inference.onnx").use { it.readBytes() },
            OrtSession.SessionOptions(),
        )
        return detector!!
    }

    private fun recognizerSession(): OrtSession {
        if (recognizer == null) recognizer = environment.createSession(
            assets.open("PP-OCRv6_small_rec_onnx_infer/inference.onnx").use { it.readBytes() },
            OrtSession.SessionOptions(),
        )
        return recognizer!!
    }

    private fun imageTensor(source: Bitmap, width: Int, height: Int, preserveAspect: Boolean = false): OnnxTensor {
        val resizedWidth = if (preserveAspect) {
            min(width, max(1, (source.width.toDouble() * height / source.height.coerceAtLeast(1)).roundToInt()))
        } else width
        val resized = Bitmap.createScaledBitmap(source, resizedWidth, height, true)
        return try {
            val pixels = IntArray(resizedWidth * height)
            resized.getPixels(pixels, 0, resizedWidth, 0, 0, resizedWidth, height)
            val values = FloatArray(1 * 3 * height * width)
            var offset = 0
            val means = floatArrayOf(0.485f, 0.456f, 0.406f)
            val stds = floatArrayOf(0.229f, 0.224f, 0.225f)
            for (channel in 0..2) {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pixel = if (x < resizedWidth) pixels[y * resizedWidth + x] else Color.WHITE
                        // PaddleOCR's mobile models decode the image as BGR.
                        val component = when (channel) {
                            0 -> Color.blue(pixel)
                            1 -> Color.green(pixel)
                            else -> Color.red(pixel)
                        }
                        values[offset++] = (component / 255f - means[channel]) / stds[channel]
                    }
                }
            }
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(values),
                longArrayOf(1, 3, height.toLong(), width.toLong()),
            )
        } finally {
            if (resized !== source) resized.recycle()
        }
    }

    private fun decodeRecognition(value: Any?): OcrResult {
        val values = flatten(value)
        val characters = loadDictionary()
        val vocabulary = maxOf(2, characters.size + 1)
        val steps = if (values.size >= vocabulary) values.size / vocabulary else 0
        if (steps == 0) return OcrResult("", 0.0)
        val output = StringBuilder()
        var previous = -1
        var confidenceTotal = 0.0
        var emitted = 0
        repeat(steps) { step ->
            val start = step * vocabulary
            var bestIndex = 0
            var bestValue = Float.NEGATIVE_INFINITY
            for (index in 0 until vocabulary) {
                val candidate = values[start + index]
                if (candidate > bestValue) {
                    bestValue = candidate
                    bestIndex = index
                }
            }
            val confidence = if (bestValue in 0f..1f) bestValue.toDouble() else (1.0 / (1.0 + exp(-bestValue.toDouble())))
            if (bestIndex != 0 && bestIndex != previous && bestIndex - 1 < characters.size) {
                output.append(characters[bestIndex - 1])
                confidenceTotal += confidence
                emitted++
            }
            previous = bestIndex
        }
        return OcrResult(output.toString().trim(), if (emitted == 0) 0.0 else confidenceTotal / emitted)
    }

    private fun flatten(value: Any?): FloatArray {
        if (value == null) return FloatArray(0)
        if (value is FloatArray) return value
        val result = ArrayList<Float>()
        fun visit(item: Any?) {
            when (item) {
                is Number -> result += item.toFloat()
                else -> if (item != null && item.javaClass.isArray) {
                    val size = Array.getLength(item)
                    repeat(size) { visit(Array.get(item, it)) }
                }
            }
        }
        visit(value)
        return result.toFloatArray()
    }

    private fun actualShape(value: Any?): IntArray? {
        if (value == null || !value.javaClass.isArray) return null
        val dimensions = mutableListOf<Int>()
        var current: Any? = value
        while (current != null && current.javaClass.isArray) {
            val size = Array.getLength(current)
            dimensions += size
            current = if (size == 0) null else Array.get(current, 0)
        }
        return dimensions.toIntArray().takeIf { it.size >= 2 }
    }

    private fun loadDictionary(): List<String> {
        dictionary?.let { return it }
        val parsed = runCatching {
            val lines = assets.open("PP-OCRv6_small_rec_onnx_infer/inference.yml").bufferedReader(Charsets.UTF_8).readLines()
            val start = lines.indexOfFirst { it.trim() == "character_dict:" }
            if (start < 0) emptyList() else lines.drop(start + 1)
                .takeWhile { it.startsWith("  -") }
                .map { line -> line.trim().removePrefix("-").trim().let(::unquote) }
        }.getOrDefault(emptyList())
        // PaddleOCR appends the space character when `use_space_char` is not
        // explicitly present in the YAML. The exported model has 18,710
        // classes: 18,709 characters plus the CTC blank at index zero.
        val base = if (parsed.isEmpty()) (('a'..'z') + ('A'..'Z') + ('0'..'9')).map { it.toString() } else parsed
        dictionary = if (base.lastOrNull() == " ") base else base + " "
        return dictionary!!
    }

    private fun unquote(value: String): String = when {
        value.length >= 2 && value.first() == '\'' && value.last() == '\'' -> value.substring(1, value.length - 1).replace("''", "'")
        value.length >= 2 && value.first() == '"' && value.last() == '"' -> value.substring(1, value.length - 1)
        else -> value
    }

    override fun close() {
        detector?.close()
        recognizer?.close()
        detector = null
        recognizer = null
    }

    private data class DetectionBand(val top: Int, val bottom: Int, val minX: Int, val maxX: Int)
}
