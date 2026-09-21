package com.example.biolens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.launch
import android.util.Log

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false
)

class AskBioLensViewModel : ViewModel() {
    var uiState by mutableStateOf<ChatUiState>(ChatUiState.Idle)
        private set

    val messages = mutableStateListOf<ChatMessage>()
    
    private var chat: Chat? = null
    private var currentSpeciesId: String? = null

    sealed interface ChatUiState {
        object Idle : ChatUiState
        object Loading : ChatUiState
        data class Error(val message: String) : ChatUiState
    }

    fun initChat(species: SpeciesKnowledge) {
        if (currentSpeciesId == species.commonName) return
        currentSpeciesId = species.commonName
        if (currentSpeciesId == species.id) return
        
        currentSpeciesId = species.id
        messages.clear()
        
        // BOB-Engineered System Instruction for Grounded BioLens Ornithologist
        val systemInstruction = """
            You are the BioLens Ornithologist. Your primary goal is to provide accurate, grounded information about birds based ONLY on the verified data provided.
            
            VERIFIED KNOWLEDGE BASE ENTRY:
            Species: ${species.commonName} (${species.scientificName})
            Habitat: ${species.habitat ?: "Not specified"}
            Diet: ${species.diet ?: "Not specified"}
            Distribution: ${species.distribution ?: "Not specified"}
            Ecological Role: ${species.ecologicalRole ?: "Not specified"}
            Conservation Status: ${species.conservationStatus ?: "Not specified"}
            Native/Migration Status: ${species.nativeStatus ?: "Not specified"}
            Interesting Fact: ${species.interestingFact ?: "Not specified"}
            
            GROUNDING RULES:
            1. Use the "VERIFIED KNOWLEDGE BASE ENTRY" as your sole source of truth for factual claims.
            2. If a user asks a question that is NOT answered in the provided data, say: "My current verified records for the ${species.commonName} don't contain that specific detail. You might want to check the provided sources for more in-depth research."
            3. DO NOT invent or hallucinate bird facts (e.g., diet, nesting habits, or migration patterns) that are not in the provided entry.
            4. If the user asks something completely unrelated to birds or this species, politely decline to answer and redirect them to ask about the ${species.commonName}.
            5. Be professional, helpful, and concise.
            6. Distinguish between verified knowledge and conversational filler.
        """.trimIndent()

        try {
            // Using Gemini 1.5 Flash through Firebase AI Logic
            // The GenerativeBackend.googleAI() returns a backend instance
            val aiInstance = Firebase.ai(backend = GenerativeBackend.googleAI())
            val model = aiInstance.generativeModel(
                modelName = "gemini-3.5-flash-lite",
                systemInstruction = content { text(systemInstruction) }
            )
            
            chat = model.startChat()
            uiState = ChatUiState.Idle
        } catch (e: Exception) {
            Log.e("AskBioLens", "Error initializing chat", e)
            uiState = ChatUiState.Error("Failed to initialize AI assistant.")
        }
    }

    fun sendMessage(userText: String) {
        val currentChat = chat ?: return
        if (userText.isBlank()) return

        messages.add(ChatMessage(userText, isUser = true))
        uiState = ChatUiState.Loading

        viewModelScope.launch {
            try {
                val response = currentChat.sendMessage(userText)
                val responseText = response.text ?: "I couldn't process that request."
                messages.add(ChatMessage(responseText, isUser = false))
                uiState = ChatUiState.Idle
            } catch (e: Exception) {
                Log.e("AskBioLens", "Error sending message", e)
                messages.add(ChatMessage("Sorry, I encountered an error. Please try again.", isUser = false, isError = true))
                uiState = ChatUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
