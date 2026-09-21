package com.example.biolens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.biolens.ui.theme.BioLensGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onSpeciesClick: (SpeciesKnowledge) -> Unit,
    modifier: Modifier = Modifier
) {
    val allSpecies = remember { SpeciesModelProvider.getAllSpecies() }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredSpecies = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allSpecies
        } else {
            allSpecies.filter {
                it.commonName.contains(searchQuery, ignoreCase = true) ||
                it.scientificName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "DISCOVER SPECIES",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        
        Text(
            text = "Explore the birds BioLens can identify",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Search Field
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            placeholder = { 
                Text(
                    "Search species...", 
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 15.sp
                ) 
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = BioLensGreen
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = BioLensGreen,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "${filteredSpecies.size} SPECIES",
            color = BioLensGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredSpecies) { species ->
                SpeciesGridCard(
                    species = species,
                    onClick = { onSpeciesClick(species) }
                )
            }
        }
    }
}

@Composable
fun SpeciesGridCard(
    species: SpeciesKnowledge,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imagePath = "file:///android_asset/species_images/${species.id}.jpg"
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imagePath)
                    .crossfade(true)
                    .build(),
                contentDescription = species.commonName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = species.commonName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = species.scientificName,
                color = BioLensGreen,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
