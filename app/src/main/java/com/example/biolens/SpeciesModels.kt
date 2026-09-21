package com.example.biolens

import android.content.Context
import org.json.JSONArray
import android.util.Log

/**
 * Data architecture for BioLens Species Knowledge Base.
 * This model represents verified information for bird species.
 */
data class SpeciesSource(
    val sourceName: String,
    val sourceUrl: String,
    val accessedDate: String,
    val note: String? = null
)

data class SpeciesKnowledge(
    val id: String,
    val commonName: String,
    val scientificName: String,
    val habitat: String? = null,
    val diet: String? = null,
    val distribution: String? = null,
    val ecologicalRole: String? = null,
    val conservationStatus: String? = null,
    val nativeStatus: String? = null,
    val interestingFact: String? = null,
    val sources: List<SpeciesSource> = emptyList()
)

/**
 * Legacy compatibility model for the UI layer.
 * Will be phased out as SpeciesDetailScreen is updated to consume SpeciesKnowledge directly.
 */
data class SpeciesMockInfo(
    val commonName: String,
    val scientificName: String,
    val lightweightInfo: String,
    val description: String,
    val habitat: String,
    val diet: String,
    val distribution: String,
    val ecologicalRole: String,
    val conservationStatus: String,
    val interestingFact: String
)

object SpeciesModelProvider {
    private var knowledgeBase: List<SpeciesKnowledge> = emptyList()

    /**
     * Loads the species knowledge base from the assets/species_knowledge.json file.
     */
    fun loadKnowledgeBase(context: Context) {
        if (knowledgeBase.isNotEmpty()) return
        try {
            val jsonString = context.assets.open("species_knowledge.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<SpeciesKnowledge>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val sourcesList = mutableListOf<SpeciesSource>()
                val sourcesArray = obj.optJSONArray("sources")
                if (sourcesArray != null) {
                    for (j in 0 until sourcesArray.length()) {
                        val sObj = sourcesArray.getJSONObject(j)
                        sourcesList.add(SpeciesSource(
                            sourceName = sObj.getString("sourceName"),
                            sourceUrl = sObj.getString("sourceUrl"),
                            accessedDate = sObj.getString("accessedDate"),
                            note = if (sObj.isNull("note")) null else sObj.getString("note")
                        ))
                    }
                }

                list.add(SpeciesKnowledge(
                    id = obj.getString("id"),
                    commonName = obj.getString("commonName"),
                    scientificName = obj.getString("scientificName"),
                    habitat = if (obj.isNull("habitat")) null else obj.getString("habitat"),
                    diet = if (obj.isNull("diet")) null else obj.getString("diet"),
                    distribution = if (obj.isNull("distribution")) null else obj.getString("distribution"),
                    ecologicalRole = if (obj.isNull("ecologicalRole")) null else obj.getString("ecologicalRole"),
                    conservationStatus = if (obj.isNull("conservationStatus")) null else obj.getString("conservationStatus"),
                    nativeStatus = if (obj.isNull("nativeStatus")) null else obj.getString("nativeStatus"),
                    interestingFact = if (obj.isNull("interestingFact")) null else obj.getString("interestingFact"),
                    sources = sourcesList
                ))
            }
            knowledgeBase = list
            Log.d("BioLens", "Knowledge base loaded with ${knowledgeBase.size} species.")
        } catch (e: Exception) {
            Log.e("BioLens", "Failed to load species knowledge base", e)
        }
    }

    /**
     * Retrieves all species in the knowledge base.
     */
    fun getAllSpecies(): List<SpeciesKnowledge> = knowledgeBase

    /**
     * Retrieves the structured knowledge for a given species name.
     */
    fun getSpeciesKnowledge(speciesName: String): SpeciesKnowledge? {
        val normalizedId = speciesName.lowercase().trim().replace(Regex("[\\s-]+"), "_")
        return knowledgeBase.find { it.id == normalizedId }
    }

    /**
     * Compatibility bridge for existing UI components.
     * Maps the new SpeciesKnowledge model back to the expected SpeciesMockInfo.
     */
    fun getSpeciesMockData(speciesName: String): SpeciesMockInfo {
        val knowledge = getSpeciesKnowledge(speciesName)
        
        return SpeciesMockInfo(
            commonName = knowledge?.commonName ?: speciesName.replace(Regex("[\\s-]+"), " ").uppercase(),
            scientificName = knowledge?.scientificName ?: "Aves family",
            lightweightInfo = knowledge?.nativeStatus ?: "Resident • India",
            description = (knowledge?.interestingFact ?: "Information pending verification from authoritative sources.").take(120) + "...",
            habitat = knowledge?.habitat ?: "Data not yet available.",
            diet = knowledge?.diet ?: "Data not yet available.",
            distribution = knowledge?.distribution ?: "Data not yet available.",
            ecologicalRole = knowledge?.ecologicalRole ?: "Data not yet available.",
            conservationStatus = knowledge?.conservationStatus ?: "Data not yet available.",
            interestingFact = knowledge?.interestingFact ?: "Data not yet available."
        )
    }
}
