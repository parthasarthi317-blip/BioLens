package com.example.biolens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationDetailScreen(
    observation: BirdObservation,
    onBack: () -> Unit,
    onViewProfile: (String) -> Unit
) {
    var showFullscreenImage by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "OBSERVATION DETAIL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // Large Tappable Image
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { showFullscreenImage = true },
                    color = Color.DarkGray
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(observation.imagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Observation Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Species Header
                Text(
                    text = observation.speciesName.uppercase().replace("-", " "),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                
                val knowledge = remember(observation.speciesName) {
                    SpeciesModelProvider.getSpeciesKnowledge(observation.speciesName)
                }
                Text(
                    text = knowledge?.scientificName ?: "Aves family",
                    color = BioLensGreen,
                    fontSize = 18.sp,
                    fontStyle = FontStyle.Italic
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Metadata Sections
                val dateFormat = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()) }
                val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                val dateStr = dateFormat.format(Date(observation.timestamp))
                val timeStr = timeFormat.format(Date(observation.timestamp))

                InfoRow(
                    icon = Icons.Default.LocationOn,
                    label = "LOCATION",
                    value = observation.location ?: "Location not available"
                )
                
                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoRow(icon = Icons.Default.CalendarToday, label = "DATE", value = dateStr)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        InfoRow(icon = Icons.Default.Schedule, label = "TIME", value = timeStr)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                InfoRow(
                    icon = Icons.Default.Verified,
                    label = "IDENTIFICATION CONFIDENCE",
                    value = "${(observation.confidence * 100).toInt()}% Verified"
                )

                Spacer(modifier = Modifier.height(40.dp))

                // VIEW PROFILE Button
                Button(
                    onClick = { onViewProfile(observation.speciesName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BioLensGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "VIEW PROFILE",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Fullscreen Image Viewer Overlay
        if (showFullscreenImage) {
            FullscreenImageViewer(
                imagePath = observation.imagePath,
                onDismiss = { showFullscreenImage = false }
            )
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun FullscreenImageViewer(imagePath: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(imagePath))
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.coerceIn(1f, 5f),
                    scaleY = scale.coerceIn(1f, 5f),
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = state),
            contentScale = ContentScale.Fit
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
