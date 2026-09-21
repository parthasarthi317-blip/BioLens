package com.example.biolens

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors

data class BirdObservation(
    val id: String,
    val speciesName: String,
    val confidence: Float,
    val timestamp: Long,
    val imagePath: String,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String? = null
)

object ObservationStorage {
    private val executor = Executors.newSingleThreadExecutor()
    private const val JSON_FILE = "observations.json"
    private const val IMAGES_DIR = "observation_images"

    fun saveObservation(
        context: Context,
        speciesName: String,
        confidence: Float,
        tempImagePath: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationName: String? = null,
        onComplete: () -> Unit
    ) {
        executor.execute {
            try {
                val imagesDir = File(context.filesDir, IMAGES_DIR)
                if (!imagesDir.exists()) imagesDir.mkdirs()

                val id = UUID.randomUUID().toString()
                val permanentFile = File(imagesDir, "obs_$id.jpg")
                
                val tempFile = File(tempImagePath)
                if (tempFile.exists()) {
                    tempFile.copyTo(permanentFile, overwrite = true)
                }

                val observation = BirdObservation(
                    id = id,
                    speciesName = speciesName,
                    confidence = confidence,
                    timestamp = System.currentTimeMillis(),
                    imagePath = permanentFile.absolutePath,
                    location = locationName,
                    latitude = latitude,
                    longitude = longitude
                )

                val observations = loadObservationsInternal(context).toMutableList()
                observations.add(0, observation)
                saveObservationsInternal(context, observations)

                Handler(Looper.getMainLooper()).post { onComplete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadObservations(context: Context, callback: (List<BirdObservation>) -> Unit) {
        executor.execute {
            val list = loadObservationsInternal(context)
            Handler(Looper.getMainLooper()).post { callback(list) }
        }
    }

    fun deleteObservation(context: Context, observation: BirdObservation, onComplete: () -> Unit) {
        executor.execute {
            try {
                val observations = loadObservationsInternal(context).toMutableList()
                observations.removeAll { it.id == observation.id }
                saveObservationsInternal(context, observations)
                
                val file = File(observation.imagePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Handler(Looper.getMainLooper()).post { onComplete() }
        }
    }

    private fun loadObservationsInternal(context: Context): List<BirdObservation> {
        val file = File(context.filesDir, JSON_FILE)
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val array = JSONArray(json)
            val list = mutableListOf<BirdObservation>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(BirdObservation(
                    id = obj.getString("id"),
                    speciesName = obj.getString("speciesName"),
                    confidence = obj.getDouble("confidence").toFloat(),
                    timestamp = obj.getLong("timestamp"),
                    imagePath = obj.getString("imagePath"),
                    location = if (obj.has("location") && !obj.isNull("location")) obj.getString("location") else null,
                    latitude = if (obj.has("latitude") && !obj.isNull("latitude")) obj.getDouble("latitude") else null,
                    longitude = if (obj.has("longitude") && !obj.isNull("longitude")) obj.getDouble("longitude") else null,
                    note = if (obj.has("note") && !obj.isNull("note")) obj.getString("note") else null
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveObservationsInternal(context: Context, observations: List<BirdObservation>) {
        val array = JSONArray()
        observations.forEach { obs ->
            val obj = JSONObject()
            obj.put("id", obs.id)
            obj.put("speciesName", obs.speciesName)
            obj.put("confidence", obs.confidence.toDouble())
            obj.put("timestamp", obs.timestamp)
            obj.put("imagePath", obs.imagePath)
            obj.put("location", obs.location)
            obj.put("latitude", obs.latitude)
            obj.put("longitude", obs.longitude)
            obj.put("note", obs.note)
            array.put(obj)
        }
        val file = File(context.filesDir, JSON_FILE)
        file.writeText(array.toString())
    }

    fun saveTempBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val tempFile = File(context.cacheDir, "temp_crop.jpg")
            val out = FileOutputStream(tempFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
