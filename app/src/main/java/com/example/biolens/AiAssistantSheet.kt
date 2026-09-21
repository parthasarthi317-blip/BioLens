    package com.example.biolens

    import androidx.compose.foundation.background
    import androidx.compose.foundation.layout.navigationBars
    import androidx.compose.foundation.layout.WindowInsets
    import androidx.compose.foundation.layout.imePadding
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.fillMaxWidth
    import androidx.compose.foundation.border
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.KeyboardActions
    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.Close
    import androidx.compose.material.icons.filled.Send
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.input.ImeAction
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import com.example.biolens.ui.theme.BioLensGreen
    import kotlinx.coroutines.launch

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AiAssistantSheet(
        species: SpeciesKnowledge,
        onDismiss: () -> Unit,
        viewModel: AskBioLensViewModel = viewModel()
    ) {
        val scope = rememberCoroutineScope()
        val scrollState = rememberLazyListState()
        var inputText by remember { mutableStateOf("") }

        LaunchedEffect(species.commonName) {
            viewModel.initChat(species)
        }

        // Auto-scroll to bottom when new messages arrive
        LaunchedEffect(viewModel.messages.size) {
            if (viewModel.messages.isNotEmpty()) {
                scrollState.animateScrollToItem(viewModel.messages.size - 1)
            }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF121212),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
            modifier = Modifier.fillMaxHeight(0.85f),
            contentWindowInsets = { WindowInsets(0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "✦ ASK BIOLENS",
                            color = BioLensGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "About ${species.commonName}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Message Area
                Box(modifier = Modifier.weight(1f)) {
                    if (viewModel.messages.isEmpty() && viewModel.uiState is AskBioLensViewModel.ChatUiState.Idle) {
                        EmptyState(species.commonName) { viewModel.sendMessage(it) }
                    } else {
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(viewModel.messages) { message ->
                                ChatBubble(message)
                            }

                            if (viewModel.uiState is AskBioLensViewModel.ChatUiState.Loading) {
                                item { TypingIndicator() }
                            }
                        }
                    }
                }

                // Input Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Ask anything...", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = BioLensGreen,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) BioLensGreen else Color.White.copy(alpha = 0.1f))
                            .clickable(enabled = inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) Color.Black else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyState(speciesName: String, onChipClick: (String) -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Verified data loaded. Ask me about the $speciesName's habitat, diet, or ecological role.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            val chips = listOf(
                "What does it eat?",
                "Where is it found?",
                "Is it migratory?",
                "Tell me an interesting fact."
            )

            chips.forEach { chipText ->
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clickable { onChipClick(chipText) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = chipText, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }

    @Composable
    fun ChatBubble(message: ChatMessage) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 0.dp,
                            bottomEnd = if (message.isUser) 0.dp else 16.dp
                        )
                    )
                    .background(
                        if (message.isUser) BioLensGreen.copy(alpha = 0.2f)
                        else if (message.isError) Color.Red.copy(alpha = 0.1f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        1.dp,
                        if (message.isUser) BioLensGreen.copy(alpha = 0.5f)
                        else if (message.isError) Color.Red.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 0.dp,
                            bottomEnd = if (message.isUser) 0.dp else 16.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (message.isError) Color(0xFFFF8A80) else Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }

    @Composable
    fun TypingIndicator() {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✦ BioLens is thinking...",
                color = BioLensGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
