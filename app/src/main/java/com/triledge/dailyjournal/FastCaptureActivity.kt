package com.triledge.dailyjournal

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triledge.dailyjournal.data.TodoRepository
import com.triledge.dailyjournal.data.UserPreferencesRepository
import com.triledge.dailyjournal.data.UserPrefs
import com.triledge.dailyjournal.data.db.TodoItem
import com.triledge.dailyjournal.data.db.TriledgeDatabase
import com.triledge.dailyjournal.ui.theme.TriledgeTheme
import kotlinx.coroutines.launch

class FastCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = TriledgeDatabase.getDatabase(applicationContext)
        val todoRepository = TodoRepository(database, applicationContext)
        val userPrefsRepo = UserPreferencesRepository(applicationContext)

        // Handle ACTION_SEND (share sheet) — extract shared text/URL
        var sharedText: String? = null
        var sharedUrl: String? = null
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val raw = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            // Try to extract a URL from the shared text
            val urlPattern = Regex("https?://\\S+")
            val match = urlPattern.find(raw)
            if (match != null) {
                sharedUrl = match.value
                // Everything that is NOT the URL becomes the note
                sharedText = raw.replace(match.value, "").trim()
                if (sharedText.isNullOrBlank()) {
                    sharedText = "Shared link"
                }
            } else {
                sharedText = raw
            }
        }

        setContent {
            val prefs by userPrefsRepo.userPrefs.collectAsStateWithLifecycle(initialValue = UserPrefs.Default)

            TriledgeTheme(
                appearanceMode = prefs.appearanceMode,
                seedColor = prefs.seedColor,
                shapeStyle = prefs.shapeStyle
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    FastCaptureScreen(
                        repository = todoRepository,
                        initialTitle = sharedText ?: "",
                        initialLinkUrl = sharedUrl,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FastCaptureScreen(
    repository: TodoRepository,
    initialTitle: String = "",
    initialLinkUrl: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    val categories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            selectedCategoryId = categories.first().id
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialLinkUrl != null) "Link To-Do" else "Quick Task Entry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (initialLinkUrl != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "🔗 Link attached",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                coroutineScope.launch {
                                    repository.insertItem(
                                        TodoItem(
                                            title = title,
                                            note = note.takeIf { it.isNotBlank() },
                                            dueDate = null,
                                            isCompleted = false,
                                            categoryId = selectedCategoryId,
                                            createdAt = System.currentTimeMillis(),
                                            linkUrl = initialLinkUrl
                                        )
                                    )
                                    Toast.makeText(context, "Task added!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        },
                        enabled = title.isNotBlank(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
