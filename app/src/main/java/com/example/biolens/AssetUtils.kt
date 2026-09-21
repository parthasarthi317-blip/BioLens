package com.example.biolens

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetUtils {
    /**
     * Copies an asset file to the internal files directory if it doesn't exist or is outdated.
     * Returns the absolute path to the copied file.
     */
    fun copyAssetToFile(context: Context, fileName: String): String {
        val outFile = File(context.filesDir, fileName)
        
        // Simple check: if it exists, we assume it's valid. 
        // In a production app, you might check version or hash.
        if (!outFile.exists()) {
            context.assets.open(fileName).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return outFile.absolutePath
    }
}
