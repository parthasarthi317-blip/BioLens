package com.example.biolens

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * EfficientNet-B0 Species Classifier for BioLens.
 * Loads the 25-class bird dataset model and performs inference on bird crops.
 */
class BirdClassifier(private val context: Context) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    companion object {
        private const val MODEL_PATH = "bird_classifier.onnx"
        private const val INPUT_SIZE = 224

        // Exact 25-class dataset order
        private val CLASS_LABELS = arrayOf(
            "Asian-Green-Bee-Eater",
            "Brown-Headed-Barbet",
            "Cattle-Egret",
            "Common-Kingfisher",
            "Common-Myna",
            "Common-Rosefinch",
            "Common-Tailorbird",
            "Coppersmith-Barbet",
            "Forest-Wagtail",
            "Gray-Wagtail",
            "Hoopoe",
            "House-Crow",
            "Indian-Grey-Hornbill",
            "Indian-Peacock",
            "Indian-Pitta",
            "Indian-Roller",
            "Jungle-Babbler",
            "Northern-Lapwing",
            "Red-Wattled-Lapwing",
            "Ruddy-Shelduck",
            "Rufous-Treepie",
            "Sarus-Crane",
            "White-Breasted-Kingfisher",
            "White-Breasted-Waterhen",
            "White-Wagtail"
        )

        // ImageNet normalization constants
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    data class ClassificationResult(
        val classIndex: Int,
        val speciesName: String,
        val confidence: Float
    )

    init {
        try {
            // Copy both .onnx and .onnx.data files to filesDir
            // ONNX Runtime needs them in the same directory to resolve external data
            val modelPath = AssetUtils.copyAssetToFile(context, MODEL_PATH)
            AssetUtils.copyAssetToFile(context, "$MODEL_PATH.data")
            
            session = env.createSession(modelPath)
            Log.d("BirdClassifier", "EfficientNet model loaded successfully from $modelPath")
        } catch (e: Exception) {
            Log.e("BirdClassifier", "Failed to initialize EfficientNet model", e)
            throw e
        }
    }

    /**
     * Preprocesses the cropped bitmap, runs inference, and returns the highest scoring class.
     */
    fun classify(bitmap: Bitmap): ClassificationResult {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Allocate float buffer for layout [1, 3, 224, 224] (CHW RGB)
        val buffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)

        for (c in 0 until 3) {
            for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
                val pixel = pixels[i]
                val v = when (c) {
                    0 -> Color.red(pixel)
                    1 -> Color.green(pixel)
                    else -> Color.blue(pixel)
                }
                // Normalize to [0,1] and apply ImageNet mean/std
                val normalized = ((v / 255.0f) - MEAN[c]) / STD[c]
                buffer.put(normalized)
            }
        }
        buffer.rewind()

        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(
            env,
            buffer,
            longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )

        return try {
            inputTensor.use { tensor ->
                val outputs = session.run(mapOf(inputName to tensor))
                outputs.use { result ->
                    val outputTensor = result[0] as OnnxTensor
                    val logits = outputTensor.floatBuffer.array() ?: FloatArray(25).apply {
                        outputTensor.floatBuffer.rewind()
                        outputTensor.floatBuffer.get(this)
                    }

                    val probabilities = softmax(logits)
                    var maxIdx = 0
                    var maxProb = -1f

                    for (j in probabilities.indices) {
                        if (probabilities[j] > maxProb) {
                            maxProb = probabilities[j]
                            maxIdx = j
                        }
                    }

                    ClassificationResult(
                        classIndex = maxIdx,
                        speciesName = CLASS_LABELS[maxIdx],
                        confidence = maxProb
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("BirdClassifier", "EfficientNet inference error", e)
            ClassificationResult(0, CLASS_LABELS[0], 0f)
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        var maxLogit = Float.NEGATIVE_INFINITY
        for (l in logits) {
            if (l > maxLogit) maxLogit = l
        }
        val exps = FloatArray(logits.size)
        var sumExps = 0f
        for (i in logits.indices) {
            exps[i] = exp(logits[i] - maxLogit)
            sumExps += exps[i]
        }
        for (i in exps.indices) {
            exps[i] /= sumExps
        }
        return exps
    }

    override fun close() {
        try {
            session.close()
            env.close()
        } catch (e: Exception) {
            Log.e("BirdClassifier", "Error during cleanup", e)
        }
    }
}
