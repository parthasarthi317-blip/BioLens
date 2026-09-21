package com.example.biolens

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import java.nio.FloatBuffer

/**
 * Isolated YOLO Detector for bird identification.
 * Handles ONNX session management, centered letterbox preprocessing,
 * and parsing of the end-to-end detection output.
 */
class YoloDetector(private val context: Context) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    companion object {
        private const val MODEL_PATH = "yolo26n.onnx"
        private const val INPUT_SIZE = 640
        private const val BIRD_CLASS_ID = 14.0f
        private const val PADDING_COLOR = 114
    }

    /**
     * Data class containing detection results and mapping metadata.
     */
    data class DetectionResult(
        val boundingBox: RectF?,
        val confidence: Float,
        val isBirdDetected: Boolean,
        val scale: Float = 1f,
        val leftPadding: Int = 0,
        val topPadding: Int = 0
    )

    private data class PreprocessResult(
        val buffer: FloatBuffer,
        val scale: Float,
        val leftPadding: Int,
        val topPadding: Int
    )

    init {
        try {
            val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
            session = env.createSession(modelBytes)
            
            // Verify model metadata
            val inputInfo = session.inputInfo.values.first().info.toString()
            val outputInfo = session.outputInfo.values.first().info.toString()
            Log.d("YoloDetector", "Model loaded. Input: $inputInfo, Output: $outputInfo")
        } catch (e: Exception) {
            Log.e("YoloDetector", "Failed to initialize YOLO model", e)
            throw e
        }
    }

    /**
     * Runs bird detection on the provided bitmap.
     * Uses background-safe ONNX inference.
     */
    fun detect(bitmap: Bitmap): DetectionResult {
        val preprocessResult = preprocess(bitmap)
        val inputName = session.inputNames.iterator().next()
        
        val inputTensor = OnnxTensor.createTensor(
            env, 
            preprocessResult.buffer, 
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )

        return try {
            inputTensor.use { tensor ->
                val outputs = session.run(mapOf(inputName to tensor))
                outputs.use { result ->
                    val outputTensor = result[0] as OnnxTensor
                    val detection = parseOutput(outputTensor)
                    
                    detection.copy(
                        scale = preprocessResult.scale,
                        leftPadding = preprocessResult.leftPadding,
                        topPadding = preprocessResult.topPadding
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("YoloDetector", "Inference error", e)
            DetectionResult(null, 0f, false)
        }
    }

    /**
     * CENTERED LETTERBOX PREPROCESSING
     * 1. Resizes while maintaining aspect ratio.
     * 2. Centers image in 640x640 canvas.
     * 3. Pads with RGB 114.
     * 4. Normalizes to [0,1].
     */
    private fun preprocess(bitmap: Bitmap): PreprocessResult {
        val w = bitmap.width
        val h = bitmap.height
        
        val scale = Math.min(INPUT_SIZE.toFloat() / w, INPUT_SIZE.toFloat() / h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        
        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        
        val left = (INPUT_SIZE - newW) / 2
        val top = (INPUT_SIZE - newH) / 2
        
        val pixels = IntArray(newW * newH)
        resized.getPixels(pixels, 0, newW, 0, 0, newW, newH)
        
        val buffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        val padValue = PADDING_COLOR / 255.0f
        
        // CHW format: [1, 3, 640, 640]
        for (c in 0 until 3) {
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val imgX = x - left
                    val imgY = y - top
                    
                    if (imgX in 0 until newW && imgY in 0 until newH) {
                        val pixel = pixels[imgY * newW + imgX]
                        val v = when (c) {
                            0 -> Color.red(pixel)
                            1 -> Color.green(pixel)
                            else -> Color.blue(pixel)
                        }
                        buffer.put(v / 255.0f)
                    } else {
                        buffer.put(padValue)
                    }
                }
            }
        }
        
        buffer.rewind()
        return PreprocessResult(buffer, scale, left, top)
    }

    /**
     * Parses the [1, 300, 6] output tensor.
     * Returns the highest confidence bird (Class 14).
     */
    private fun parseOutput(outputTensor: OnnxTensor): DetectionResult {
        val buffer = outputTensor.floatBuffer
        buffer.rewind()
        
        var maxConf = -1f
        var bestBox: RectF? = null
        
        for (i in 0 until 300) {
            val x1 = buffer.get()
            val y1 = buffer.get()
            val x2 = buffer.get()
            val y2 = buffer.get()
            val conf = buffer.get()
            val cls = buffer.get()
            
            if (cls == BIRD_CLASS_ID && conf > maxConf) {
                maxConf = conf
                bestBox = RectF(x1, y1, x2, y2)
            }
        }
        
        return if (bestBox != null) {
            DetectionResult(bestBox, maxConf, true)
        } else {
            DetectionResult(null, 0f, false)
        }
    }

    override fun close() {
        try {
            session.close()
            env.close()
        } catch (e: Exception) {
            Log.e("YoloDetector", "Error during cleanup", e)
        }
    }
}
