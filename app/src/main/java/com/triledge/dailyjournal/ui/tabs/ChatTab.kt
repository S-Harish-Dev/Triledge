package com.triledge.dailyjournal.ui.tabs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triledge.dailyjournal.data.AiApiClient
import com.triledge.dailyjournal.data.AiPreferencesRepository
import com.triledge.dailyjournal.data.AiPrefs
import com.triledge.dailyjournal.data.ChatRepository
import com.triledge.dailyjournal.data.db.ChatMessage
import kotlinx.coroutines.launch

@Composable
fun ChatTab(
    chatRepository: ChatRepository,
    aiPreferencesRepository: AiPreferencesRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiPrefs by aiPreferencesRepository.aiPrefsState.collectAsStateWithLifecycle(initialValue = AiPrefs.Default)
    val messages by chatRepository.allMessages.collectAsStateWithLifecycle(initialValue = emptyList())

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (aiPrefs.provider == "NONE" || aiPrefs.apiKey.isNullOrBlank()) {
        // Disabled placeholder view
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "AI Chat is Disabled",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "To keep your data completely private, AI features are off by default. Bring your own Gemini or OpenAI API Key in Settings to enable the local AI assistant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        text = "Navigate to the Settings tab -> General -> AI Chat Configuration to opt-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }
            }
        }
    } else {
        // Active chat screen
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Header showing provider name + clear history
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Chat (${aiPrefs.provider})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            chatRepository.clearHistory()
                            Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Message logs
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    val bubbleColor = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        if (msg.content.startsWith("[Error]")) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    }

                    val alignment = if (isUser) Alignment.End else Alignment.Start
                    val bubbleShape = if (isUser) {
                        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
                    } else {
                        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            horizontalAlignment = alignment,
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Text(
                                text = if (isUser) "You" else aiPrefs.provider,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .clip(bubbleShape)
                                    .background(bubbleColor)
                                    .padding(12.dp)
                              ) {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        if (msg.content.startsWith("[Error]")) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 72.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask anything...") },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium
                )

                IconButton(
                    onClick = {
                        val prompt = inputText.trim()
                        if (prompt.isNotBlank()) {
                            inputText = ""
                            isLoading = true
                            
                            coroutineScope.launch {
                                // Save user query to room
                                val userMsg = ChatMessage(
                                    role = "user",
                                    content = prompt,
                                    timestamp = System.currentTimeMillis()
                                )
                                chatRepository.insertMessage(userMsg)

                                try {
                                    // Fetch current history
                                    val currentHistory = messages
                                    val response = AiApiClient.getAiResponse(
                                        provider = aiPrefs.provider,
                                        apiKey = aiPrefs.apiKey ?: "",
                                        model = aiPrefs.model,
                                        customEndpoint = aiPrefs.customEndpoint,
                                        prompt = prompt,
                                        history = currentHistory
                                    )

                                    val aiMsg = ChatMessage(
                                        role = "model",
                                        content = response.text,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    chatRepository.insertMessage(aiMsg)

                                    // Log token usage metrics
                                    chatRepository.insertTokenUsage(
                                        provider = aiPrefs.provider,
                                        model = aiPrefs.model,
                                        input = response.promptTokens,
                                        output = response.completionTokens
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    val errorMsg = ChatMessage(
                                        role = "model",
                                        content = "[Error]: ${e.localizedMessage ?: "Failed to get AI response"}",
                                        timestamp = System.currentTimeMillis()
                                    )
                                    chatRepository.insertMessage(errorMsg)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
