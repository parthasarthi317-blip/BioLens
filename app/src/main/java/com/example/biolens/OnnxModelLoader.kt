package com.example.biolens

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log

class OnnxModelLoader(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    fun verifyModels(): String {
        val results = StringBuilder()
        
        results.append(verifyModel("yolo26n.onnx", "YOLO"))
        results.append("\n\n")
        results.append(verifyModel("bird_classifier.onnx", "EfficientNet"))
        
        return results.toString()
    }

    private fun verifyModel(fileName: String, modelName: String): String {
        return try {
            val modelPath = if (fileName.endsWith(".onnx")) {
                // For models that might have external data, use the file path loading method
                AssetUtils.copyAssetToFile(context, fileName).also {
                    // Try to copy .data file if it exists (e.g. for bird_classifier)
                    try {
                        AssetUtils.copyAssetToFile(context, "$fileName.data")
                    } catch (e: Exception) {
                        // Ignore if .data doesn't exist
                    }
                }
            } else {
                null
            }

            val session = if (modelPath != null) {
                env.createSession(modelPath)
            } else {
                val modelBytes = context.assets.open(fileName).readBytes()
                env.createSession(modelBytes)
            }
            
            val info = StringBuilder("$modelName ($fileName) loaded successfully.\n")
            
            info.append("Inputs:\n")
            session.inputNames.forEach { name ->
                val inputInfo = session.inputInfo[name]
                info.append("  - $name: ${inputInfo?.info}\n")
            }
            
            info.append("Outputs:\n")
            session.outputNames.forEach { name ->
                val outputInfo = session.outputInfo[name]
                info.append("  - $name: ${outputInfo?.info}\n")
            }
            
            val result = info.toString()
            Log.d("OnnxModelLoader", result)
            session.close()
            result
        } catch (e: Exception) {
            val error = "Failed to load $modelName ($fileName): ${e.message}"
            Log.e("OnnxModelLoader", error)
            error
        }
    }
}
