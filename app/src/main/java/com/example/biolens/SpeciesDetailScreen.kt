package com.example.biolens

import com.example.biolens.ui.theme.BioLensGreen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun SpeciesDetailScreen(
    speciesName: String,
    confidence: Float,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val knowledge = SpeciesModelProvider.getSpeciesKnowledge(speciesName)
    val commonName = knowledge?.commonName ?: speciesName.replace("-", " ").replace("_", " ").uppercase()
    val scientificName = knowledge?.scientificName ?: "Aves family"
    
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val speciesId = knowledge?.id ?: speciesName.lowercase().trim().replace(Regex("[\\s-]+"), "_")
    val imagePath = "file:///android_asset/species_images/$speciesId.jpg"

    var showAiAssistant by remember { mutableStateOf(false) }

    if (showAiAssistant && knowledge != null) {
        AiAssistantSheet(
            species = knowledge,
            onDismiss = { showAiAssistant = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Species Image HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color.DarkGray.copy(alpha = 0.15f))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imagePath)
                        .crossfade(true)
                        .build(),
                    contentDescription = "$commonName illustration",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    error = null // Handled by placeholder below if needed
                )
                
                // HUD Overlay elements
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = "V-ASSET-ID: ${speciesId.uppercase()}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                // Scanlines / Texture overlay could go here
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header Info
                Text(
                    text = commonName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = scientificName,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (confidence > 0f) {
                    Text(
                        text = "${(confidence * 100).toInt()}% IDENTIFICATION CONFIDENCE",
                        color = BioLensGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                } else {
                    Text(
                        text = "VERIFIED KNOWLEDGE BASE SPECIES",
                        color = BioLensGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(24.dp))

                // Structured Knowledge Cards
                SpeciesKnowledgeCard(title = "HABITAT", content = knowledge?.habitat)
                SpeciesKnowledgeCard(title = "DIET", content = knowledge?.diet)
                SpeciesKnowledgeCard(title = "DISTRIBUTION", content = knowledge?.distribution)
                SpeciesKnowledgeCard(title = "ECOLOGICAL ROLE", content = knowledge?.ecologicalRole)
                
                // Emphasized Conservation Status
                SpeciesKnowledgeCard(
                    title = "CONSERVATION STATUS", 
                    content = knowledge?.conservationStatus,
                    isEmphasized = true
                )
                
                SpeciesKnowledgeCard(title = "NATIVE / MIGRATION STATUS", content = knowledge?.nativeStatus)
                SpeciesKnowledgeCard(title = "INTERESTING FACT", content = knowledge?.interestingFact)

                // Compact Sources Section
                val sources = knowledge?.sources ?: emptyList()
                if (sources.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .background(Color(0x0DFFFFFF), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "SOURCES",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val uriHandler = LocalUriHandler.current
                        sources.forEachIndexed { index, source ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (index == sources.size - 1) 0.dp else 12.dp)
                            ) {
                                Text(
                                    text = source.sourceName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!source.note.isNullOrBlank()) {
                                    Text(
                                        text = source.note,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = source.sourceUrl,
                                    color = Color(0xFF29B6F6),
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable {
                                            try {
                                                uriHandler.openUri(source.sourceUrl)
                                            } catch (_: Exception) {
                                                // Handle gracefully
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AI Visual Button Only
                Button(
                    onClick = { if (knowledge != null) showAiAssistant = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "✦ ASK BIOLENS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun SpeciesKnowledgeCard(
    title: String,
    content: String?,
    isEmphasized: Boolean = false
) {
    val displayContent = if (content.isNullOrBlank()) "Information unavailable" else content

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(
                color = if (isEmphasized) BioLensGreen.copy(alpha = 0.08f) else Color(0x0DFFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = if (isEmphasized) BioLensGreen else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = if (isEmphasized) BioLensGreen else Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = displayContent,
            color = if (content.isNullOrBlank()) Color.White.copy(alpha = 0.4f) else Color.White,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}
