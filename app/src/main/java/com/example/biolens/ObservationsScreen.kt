package com.example.biolens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.biolens.ui.theme.BioLensGreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ObservationsScreen(
    onObservationClick: (BirdObservation) -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var observations by remember { mutableStateOf<List<BirdObservation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        ObservationStorage.loadObservations(context) { list ->
            observations = list
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "YOUR OBSERVATIONS",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            
            Text(
                text = "Track your explicitly saved bird discoveries",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            if (observations.isNotEmpty()) {
                BiodiversitySummary(observations)
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BioLensGreen)
                }
            } else if (observations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No saved observations yet",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Identify birds via live camera and save them",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(observations, key = { it.id }) { obs ->
                        ObservationItemRow(
                            observation = obs,
                            onClick = { onObservationClick(obs) },
                            onDelete = {
                                ObservationStorage.deleteObservation(context, obs) {
                                    ObservationStorage.loadObservations(context) { updatedList ->
                                        observations = updatedList
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Bottom Action Area (Above Nav)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 110.dp) // Sufficient space for the 96dp bottom nav + padding
        ) {
            Button(
                onClick = onOpenMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BioLensGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "OBSERVATION MAP",
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun BiodiversitySummary(observations: List<BirdObservation>) {
    val uniqueSpecies = remember(observations) {
        observations.distinctBy { it.speciesName.lowercase() }.size
    }
    val totalObservations = observations.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem(label = "SPECIES OBSERVED", value = uniqueSpecies.toString())
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            SummaryItem(label = "TOTAL OBSERVATIONS", value = totalObservations.toString())
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = BioLensGreen,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ObservationItemRow(
    observation: BirdObservation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val knowledge = remember(observation.speciesName) {
        SpeciesModelProvider.getSpeciesKnowledge(observation.speciesName)
    }
    val scientificName = knowledge?.scientificName ?: "Aves family"
    
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()) }
    val dateString = remember(observation.timestamp) { dateFormat.format(Date(observation.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(observation.imagePath))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = observation.speciesName.uppercase().replace("-", " "),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = scientificName,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 2.dp)
            )
            
            Text(
                text = "${(observation.confidence * 100).toInt()}% Confidence",
                color = BioLensGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = dateString,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (!observation.location.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = BioLensGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = observation.location,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
