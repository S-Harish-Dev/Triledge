package com.triledge.dailyjournal.ui.tabs

import android.Manifest
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triledge.dailyjournal.data.AiApiClient
import com.triledge.dailyjournal.data.AiPreferencesRepository
import com.triledge.dailyjournal.data.ChatRepository
import com.triledge.dailyjournal.data.TodoRepository
import com.triledge.dailyjournal.data.db.TodoCategory
import com.triledge.dailyjournal.data.db.TodoItem
import com.triledge.dailyjournal.data.db.TodoItemWithCategory
import com.triledge.dailyjournal.ui.settings.getIconByName
import com.triledge.dailyjournal.ui.settings.parseHexColor
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import android.net.Uri
import android.content.Intent
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.triledge.dailyjournal.data.LinkPreviewHelper
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

enum class TodoSortOrder {
    CREATION_ORDER,
    DUE_DATE
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TodoTab(
    repository: TodoRepository,
    aiPreferencesRepository: AiPreferencesRepository,
    chatRepository: ChatRepository,
    addTrigger: Int = 0,
    onConsumeAddTrigger: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var sortOrder by remember { mutableStateOf(TodoSortOrder.CREATION_ORDER) }
    var showAddDialog by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(false) }
    
    // Search query & bucket filtering
    var searchQuery by remember { mutableStateOf("") }
    var selectedBucket by remember { mutableStateOf("All") }
    val buckets = listOf("All", "Urgent", "Today", "In Loop", "Weekly", "Whenever")

    // Speech and AI states
    val voiceState = rememberVoiceRecognizerState(context)
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }
    var isAiParsing by remember { mutableStateOf(false) }

    // Selected review item
    var reviewItem by remember { mutableStateOf<TodoItemWithCategory?>(null) }

    val items by repository.allItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(addTrigger) {
        if (addTrigger > 0) {
            showAddDialog = true
            onConsumeAddTrigger()
        }
    }

    // Voice Capture Permission Launcher
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (!aiPreferencesRepository.isVoiceDisclosureShown()) {
                showDisclosureDialog = true
            } else {
                voiceState.startListening { text ->
                    if (text.isNotBlank()) {
                        showVoiceDialog = false
                        parseVoiceTask(text, categories, repository, aiPreferencesRepository, chatRepository, context, coroutineScope) { loading ->
                            isAiParsing = loading
                        }
                    }
                }
                showVoiceDialog = true
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice capture.", Toast.LENGTH_SHORT).show()
        }
    }

    // Filter logic
    val activeItems = remember(items, sortOrder, selectedBucket, searchQuery) {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfToday = startOfToday + 24L * 60 * 60 * 1000
        val endOfWeek = startOfToday + 7L * 24 * 60 * 60 * 1000

        val filtered = items.filter { !it.item.isCompleted }.filter { wrapper ->
            val matchesSearch = searchQuery.isBlank() || 
                wrapper.item.title.contains(searchQuery, ignoreCase = true) ||
                (wrapper.item.note?.contains(searchQuery, ignoreCase = true) ?: false)

            val matchesBucket = when (selectedBucket) {
                "Urgent" -> wrapper.item.isPriority
                "Today" -> wrapper.item.dueDate != null && wrapper.item.dueDate >= startOfToday && wrapper.item.dueDate < endOfToday
                "In Loop" -> wrapper.item.recurringInterval != null
                "Weekly" -> wrapper.item.dueDate != null && wrapper.item.dueDate >= startOfToday && wrapper.item.dueDate <= endOfWeek
                "Whenever" -> wrapper.item.dueDate == null
                else -> true
            }

            matchesSearch && matchesBucket
        }
        sortTodoItems(filtered, sortOrder)
    }

    val completedItems = remember(items, sortOrder, selectedBucket, searchQuery) {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfToday = startOfToday + 24L * 60 * 60 * 1000
        val endOfWeek = startOfToday + 7L * 24 * 60 * 60 * 1000

        val filtered = items.filter { it.item.isCompleted }.filter { wrapper ->
            val matchesSearch = searchQuery.isBlank() || 
                wrapper.item.title.contains(searchQuery, ignoreCase = true) ||
                (wrapper.item.note?.contains(searchQuery, ignoreCase = true) ?: false)

            val matchesBucket = when (selectedBucket) {
                "Urgent" -> wrapper.item.isPriority
                "Today" -> wrapper.item.dueDate != null && wrapper.item.dueDate >= startOfToday && wrapper.item.dueDate < endOfToday
                "In Loop" -> wrapper.item.recurringInterval != null
                "Weekly" -> wrapper.item.dueDate != null && wrapper.item.dueDate >= startOfToday && wrapper.item.dueDate <= endOfWeek
                "Whenever" -> wrapper.item.dueDate == null
                else -> true
            }

            matchesSearch && matchesBucket
        }
        sortTodoItems(filtered, sortOrder)
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search & Voice capture Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search or speak task...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = CircleShape,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    ) {
                        Icon(
                            imageVector = MicIcon,
                            contentDescription = "Mic Voice Capture",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                buckets.forEach { bucket ->
                    val isSelected = selectedBucket == bucket
                    
                    val activeBgColor = when (bucket) {
                        "Urgent" -> Color(0xFFFEE2E2)
                        "Today" -> Color(0xFFDBEAFE)
                        "In Loop" -> Color(0xFFF3E8FF)
                        "Weekly" -> Color(0xFFD1FAE5)
                        "Whenever" -> Color(0xFFF3F4F6)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val activeTextColor = when (bucket) {
                        "Urgent" -> Color(0xFFEF4444)
                        "Today" -> Color(0xFF3B82F6)
                        "In Loop" -> Color(0xFF8B5CF6)
                        "Weekly" -> Color(0xFF10B981)
                        "Whenever" -> Color(0xFF6B7280)
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }

                    val containerColor = if (isSelected) activeBgColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    val textColor = if (isSelected) activeTextColor else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    val icon = when (bucket) {
                        "Urgent" -> Icons.Default.Warning
                        "Today" -> Icons.Default.DateRange
                        "In Loop" -> Icons.Default.Refresh
                        "Weekly" -> Icons.Default.Star
                        "Whenever" -> Icons.Default.MoreVert
                        else -> Icons.Default.List
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(containerColor)
                            .clickable { selectedBucket = bucket }
                            .padding(vertical = 8.dp, horizontal = 14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = bucket,
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = bucket,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // Tasks Pane (full screen width)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Toolbar with sort order
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedBucket == "All") "All Tasks" else "$selectedBucket Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            sortOrder = if (sortOrder == TodoSortOrder.CREATION_ORDER) {
                                TodoSortOrder.DUE_DATE
                            } else {
                                TodoSortOrder.CREATION_ORDER
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (sortOrder == TodoSortOrder.CREATION_ORDER) "Created Date" else "Due Date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active Tasks Listing
                    if (activeItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No tasks found.\nTap the + button to add.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(activeItems, key = { it.item.id }) { item ->
                            TodoRow(
                                item = item,
                                onToggle = { isChecked ->
                                    coroutineScope.launch {
                                        repository.updateItem(item.item.copy(isCompleted = isChecked))
                                    }
                                },
                                onDelete = {
                                    coroutineScope.launch { repository.deleteItem(item.item) }
                                },
                                onReviewClick = {
                                    reviewItem = item
                                },
                                repository = repository
                            )
                        }
                    }

                    // Completed Tasks Section
                    if (completedItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { completedExpanded = !completedExpanded }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (completedExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Completed (${completedItems.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (completedExpanded) {
                            items(completedItems, key = { it.item.id }) { item ->
                                TodoRow(
                                    item = item,
                                    onToggle = { isChecked ->
                                        coroutineScope.launch {
                                            repository.updateItem(item.item.copy(isCompleted = isChecked))
                                        }
                                    },
                                    onDelete = {
                                        coroutineScope.launch { repository.deleteItem(item.item) }
                                    },
                                    onReviewClick = {
                                        reviewItem = item
                                    },
                                    repository = repository
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Task Dialog
    if (showAddDialog) {
        AddTaskDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, note, dueDate, categoryId, isPriority, recurringInterval, linkUrl, stickerPath ->
                coroutineScope.launch {
                    val id = repository.insertItem(
                        TodoItem(
                            title = title,
                            note = note,
                            dueDate = dueDate,
                            isCompleted = false,
                            categoryId = categoryId,
                            createdAt = System.currentTimeMillis(),
                            isPriority = isPriority,
                            recurringInterval = recurringInterval,
                            requiresReview = false,
                            linkUrl = linkUrl,
                            stickerPath = stickerPath
                        )
                    )
                    
                    if (linkUrl != null) {
                        // Background fetch the Open Graph thumbnail
                        val path = LinkPreviewHelper.fetchOgThumbnail(context, linkUrl)
                        if (path != null) {
                            repository.updateItem(
                                TodoItem(
                                    id = id,
                                    title = title,
                                    note = note,
                                    dueDate = dueDate,
                                    isCompleted = false,
                                    categoryId = categoryId,
                                    createdAt = System.currentTimeMillis(),
                                    isPriority = isPriority,
                                    recurringInterval = recurringInterval,
                                    requiresReview = false,
                                    linkUrl = linkUrl,
                                    linkThumbnailPath = path,
                                    stickerPath = stickerPath
                                )
                            )
                        }
                    }
                }
                showAddDialog = false
            }
        )
    }

    // Speech Recognition Dialog Overlay
    if (showVoiceDialog) {
        Dialog(onDismissRequest = {
            voiceState.stopListening()
            showVoiceDialog = false
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (voiceState.isListening) "Listening..." else "Processing Speech...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MicIcon,
                            contentDescription = "Mic",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = voiceState.transcript.ifBlank { "Speak task now (e.g. 'Buy groceries tomorrow urgent')" },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    voiceState.errorMsg?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                voiceState.stopListening()
                                showVoiceDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    // Voice Capture Disclosure Dialog (Disclosed plain language for first-time use)
    if (showDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showDisclosureDialog = false },
            title = { Text("Voice Capture Processing") },
            text = {
                Text("Tapping the microphone records your voice and transcribes it locally on-device. If an AI provider is configured in Settings, the parsed text is sent to the AI cloud API to structure the tasks (title, due date, category, priority). No raw audio ever leaves your device.")
            },
            confirmButton = {
                Button(onClick = {
                    aiPreferencesRepository.setVoiceDisclosureShown(true)
                    showDisclosureDialog = false
                    voiceState.startListening { text ->
                        if (text.isNotBlank()) {
                            showVoiceDialog = false
                            parseVoiceTask(text, categories, repository, aiPreferencesRepository, chatRepository, context, coroutineScope) { loading ->
                                isAiParsing = loading
                            }
                        }
                    }
                    showVoiceDialog = true
                }) {
                    Text("Got it")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosureDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // AI Parsing Loading Dialog
    if (isAiParsing) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text("AI structuring tasks...")
                }
            }
        }
    }

    // Review Task Dialog (for uncertain-creation flags)
    reviewItem?.let { item ->
        ReviewTaskDialog(
            item = item,
            categories = categories,
            onDismiss = { reviewItem = null },
            onConfirm = { updatedItem ->
                coroutineScope.launch {
                    repository.updateItem(updatedItem)
                }
                reviewItem = null
            },
            onDelete = {
                coroutineScope.launch {
                    repository.deleteItem(item.item)
                }
                reviewItem = null
            }
        )
    }
}

private fun sortTodoItems(items: List<TodoItemWithCategory>, sortOrder: TodoSortOrder): List<TodoItemWithCategory> {
    return when (sortOrder) {
        TodoSortOrder.CREATION_ORDER -> items.sortedByDescending { it.item.createdAt }
        TodoSortOrder.DUE_DATE -> {
            val withDue = items.filter { it.item.dueDate != null }.sortedBy { it.item.dueDate }
            val withoutDue = items.filter { it.item.dueDate == null }.sortedByDescending { it.item.createdAt }
            withDue + withoutDue
        }
    }
}

@Composable
fun LocalImage(
    filePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(filePath) {
        runCatching { android.graphics.BitmapFactory.decodeFile(filePath) }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TodoRow(
    item: TodoItemWithCategory,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onReviewClick: () -> Unit,
    repository: TodoRepository, // Need repository to save fetched link preview path
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.item.isCompleted,
                onCheckedChange = onToggle
            )

            Spacer(Modifier.width(8.dp))

            // ── Link Preview Thumbnail ──
            if (item.item.linkThumbnailPath != null) {
                LocalImage(
                    filePath = item.item.linkThumbnailPath,
                    contentDescription = "Link Preview",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(8.dp))
            } else if (item.item.linkUrl != null) {
                var isFetching by remember { mutableStateOf(false) }
                var showPreviewDisclosure by remember { mutableStateOf(false) }

                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                } else {
                    IconButton(
                        onClick = { showPreviewDisclosure = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Fetch link preview",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                if (showPreviewDisclosure) {
                    AlertDialog(
                        onDismissRequest = { showPreviewDisclosure = false },
                        title = { Text("Fetch Link Preview?") },
                        text = { Text("Triledge will connect to this link once to fetch its metadata and image preview. This is a disclosed network request.") },
                        confirmButton = {
                            Button(onClick = {
                                showPreviewDisclosure = false
                                isFetching = true
                                coroutineScope.launch {
                                    val path = LinkPreviewHelper.fetchOgThumbnail(context, item.item.linkUrl)
                                    if (path != null) {
                                        repository.updateItem(item.item.copy(linkThumbnailPath = path))
                                    }
                                    isFetching = false
                                }
                            }) {
                                Text("Fetch")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPreviewDisclosure = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            // ── Sticker Badge ──
            if (item.item.stickerPath != null) {
                LocalImage(
                    filePath = item.item.stickerPath,
                    contentDescription = "Sticker",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Title
                    Text(
                        text = item.item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (item.item.isCompleted) TextDecoration.LineThrough else null,
                        color = if (item.item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Urgent badge
                    if (item.item.isPriority && !item.item.isCompleted) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Urgent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Uncertain review flag ("!" badge)
                    if (item.item.requiresReview && !item.item.isCompleted) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .clickable { onReviewClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "!",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Optional Note with clickable Markdown Link parsing
                if (!item.item.note.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    val annotatedNote = remember(item.item.note) {
                        buildAnnotatedString {
                            val pattern = Regex("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)")
                            var lastIndex = 0
                            pattern.findAll(item.item.note).forEach { match ->
                                val startIndex = match.range.first
                                val endIndex = match.range.last + 1
                                if (startIndex > lastIndex) {
                                    append(item.item.note.substring(lastIndex, startIndex))
                                }
                                val text = match.groupValues[1]
                                val url = match.groupValues[2]
                                pushStringAnnotation(tag = "URL", annotation = url)
                                withStyle(
                                    style = SpanStyle(
                                        color = Color(0xFF3F51B5),
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(text)
                                }
                                pop()
                                lastIndex = endIndex
                            }
                            if (lastIndex < item.item.note.length) {
                                append(item.item.note.substring(lastIndex))
                            }
                        }
                    }

                    ClickableText(
                        text = annotatedNote,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = { offset ->
                            annotatedNote.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                        context.startActivity(intent)
                                    }
                                }
                        }
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Metadata details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category tag
                        if (item.category != null) {
                            val catColor = parseHexColor(item.category.colorHex)
                            val catIcon = getIconByName(item.category.iconName)

                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = catIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = catColor
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(item.category.name, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        // Recurring reminder badge
                        if (item.item.recurringInterval != null && !item.item.isCompleted) {
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Recurring",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = item.item.recurringInterval.replaceFirstChar { it.titlecase() },
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }

                    // Due Date alert
                    if (item.item.dueDate != null) {
                        val isOverdue = item.item.dueDate < todayStart && !item.item.isCompleted
                        val isToday = item.item.dueDate >= todayStart && item.item.dueDate < todayStart + 24 * 60 * 60 * 1000 && !item.item.isCompleted
                        
                        val dateColor = when {
                            isOverdue -> MaterialTheme.colorScheme.error
                            isToday -> Color(0xFFD97706) // Amber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Text(
                            text = dateFormat.format(Date(item.item.dueDate)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isOverdue || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = dateColor
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    categories: List<TodoCategory>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, note: String?, dueDate: Long?, categoryId: Long?, isPriority: Boolean, recurringInterval: String?, linkUrl: String?, stickerPath: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var isPriority by remember { mutableStateOf(false) }
    var recurringInterval by remember { mutableStateOf<String?>(null) } // null, "daily", "weekly"
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var showCustomDatePicker by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Link URL and Sticker states
    var linkUrl by remember { mutableStateOf("") }
    var stickerPath by remember { mutableStateOf<String?>(null) }
    var showInsertLinkDialog by remember { mutableStateOf(false) }

    val stickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val stickersDir = File(context.filesDir, "stickers").also { it.mkdirs() }
                    val file = File(stickersDir, "sticker_${UUID.randomUUID()}.png")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        stickerPath = file.absolutePath
                        Toast.makeText(context, "Sticker attached!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Prefill category
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            selectedCategoryId = categories.first().id
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Add Task",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Optional Note/Details") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { showInsertLinkDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Insert Link in Note")
                            }
                        }
                    }
                }

                // Attach direct URL
                item {
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("Attach Link URL (e.g. YouTube, Instagram)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Attach Sticker
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sticker",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(onClick = { stickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (stickerPath == null) "Attach Sticker" else "Change Sticker")
                            }
                            stickerPath?.let { path ->
                                Box(modifier = Modifier.size(40.dp)) {
                                    LocalImage(
                                        filePath = path,
                                        contentDescription = "Attached Sticker",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    IconButton(
                                        onClick = { stickerPath = null },
                                        modifier = Modifier
                                            .size(16.dp)
                                            .align(Alignment.TopEnd)
                                            .background(Color.Red, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Optional Due Date Picker Row
                item {
                    Column {
                        Text(
                            text = "Due Date (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showCustomDatePicker = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = dueDate?.let { dateFormat.format(Date(it)) } ?: "No Due Date"
                                    )
                                }
                            }

                            if (dueDate != null) {
                                TextButton(onClick = { dueDate = null }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }

                // Category Selection
                item {
                    Column {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
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
                }

                // Priority & Recurrence Switches
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mark as Urgent (Priority)")
                            Switch(
                                checked = isPriority,
                                onCheckedChange = { isPriority = it }
                            )
                        }

                        // Recurring reminder
                        Column {
                            Text("Repeat Interval", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = recurringInterval == null,
                                    onClick = { recurringInterval = null },
                                    label = { Text("None") }
                                )
                                FilterChip(
                                    selected = recurringInterval == "daily",
                                    onClick = { recurringInterval = "daily" },
                                    label = { Text("Daily") }
                                )
                                FilterChip(
                                    selected = recurringInterval == "weekly",
                                    onClick = { recurringInterval = "weekly" },
                                    label = { Text("Weekly") }
                                )
                            }
                        }
                    }
                }

                item {
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
                                    onConfirm(
                                        title,
                                        note.takeIf { it.isNotBlank() },
                                        dueDate,
                                        selectedCategoryId,
                                        isPriority,
                                        recurringInterval,
                                        linkUrl.takeIf { it.isNotBlank() },
                                        stickerPath
                                    )
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

    if (showInsertLinkDialog) {
        InsertLinkDialog(
            onDismiss = { showInsertLinkDialog = false },
            onConfirm = { text, url ->
                val formattedLink = "[$text]($url)"
                note = if (note.isBlank()) formattedLink else "$note $formattedLink"
                showInsertLinkDialog = false
            }
        )
    }

    if (showCustomDatePicker) {
        com.triledge.dailyjournal.ui.components.CustomDatePickerDialog(
            initialDateMillis = dueDate,
            onDismiss = { showCustomDatePicker = false },
            onConfirm = { time ->
                dueDate = time
                showCustomDatePicker = false
            }
        )
    }
}

@Composable
fun InsertLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (text: String, url: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Link Text (e.g. Watch this)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Link URL (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text, url) },
                enabled = text.isNotBlank() && url.isNotBlank() && url.startsWith("http")
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Review dialog for voice-captured items
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewTaskDialog(
    item: TodoItemWithCategory,
    categories: List<TodoCategory>,
    onDismiss: () -> Unit,
    onConfirm: (TodoItem) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var title by remember { mutableStateOf(item.item.title) }
    var note by remember { mutableStateOf(item.item.note ?: "") }
    var selectedCategoryId by remember { mutableStateOf(item.item.categoryId) }
    var isPriority by remember { mutableStateOf(item.item.isPriority) }
    var recurringInterval by remember { mutableStateOf(item.item.recurringInterval) }
    var dueDate by remember { mutableStateOf(item.item.dueDate) }
    var showCustomDatePicker by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var linkUrl by remember { mutableStateOf(item.item.linkUrl ?: "") }
    var stickerPath by remember { mutableStateOf(item.item.stickerPath) }
    var showInsertLinkReview by remember { mutableStateOf(false) }

    val stickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val stickersDir = File(context.filesDir, "stickers").also { it.mkdirs() }
                    val file = File(stickersDir, "sticker_${UUID.randomUUID()}.png")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        stickerPath = file.absolutePath
                        Toast.makeText(context, "Sticker attached!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Review Required",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "This one needs a quick check",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note/Details") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { showInsertLinkReview = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Insert Link in Note")
                            }

                            if (showInsertLinkReview) {
                                InsertLinkDialog(
                                    onDismiss = { showInsertLinkReview = false },
                                    onConfirm = { text, url ->
                                        val formatted = "[$text]($url)"
                                        note = if (note.isBlank()) formatted else "$note $formatted"
                                        showInsertLinkReview = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Attach direct URL
                item {
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("Attach Link URL (e.g. YouTube, Instagram)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sticker",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(onClick = { stickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (stickerPath == null) "Attach Sticker" else "Change Sticker")
                            }
                            stickerPath?.let { path ->
                                Box(modifier = Modifier.size(40.dp)) {
                                    LocalImage(
                                        filePath = path,
                                        contentDescription = "Attached Sticker",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    IconButton(
                                        onClick = { stickerPath = null },
                                        modifier = Modifier
                                            .size(16.dp)
                                            .align(Alignment.TopEnd)
                                            .background(Color.Red, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            text = "Due Date (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showCustomDatePicker = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = dueDate?.let { dateFormat.format(Date(it)) } ?: "No Due Date"
                                    )
                                }
                            }

                            if (dueDate != null) {
                                TextButton(onClick = { dueDate = null }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
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
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mark as Urgent (Priority)")
                            Switch(
                                checked = isPriority,
                                onCheckedChange = { isPriority = it }
                            )
                        }

                        Column {
                            Text("Repeat Interval", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = recurringInterval == null,
                                    onClick = { recurringInterval = null },
                                    label = { Text("None") }
                                )
                                FilterChip(
                                    selected = recurringInterval == "daily",
                                    onClick = { recurringInterval = "daily" },
                                    label = { Text("Daily") }
                                )
                                FilterChip(
                                    selected = recurringInterval == "weekly",
                                    onClick = { recurringInterval = "weekly" },
                                    label = { Text("Weekly") }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (title.isNotBlank()) {
                                        onConfirm(
                                            item.item.copy(
                                                title = title,
                                                note = note.takeIf { it.isNotBlank() },
                                                dueDate = dueDate,
                                                categoryId = selectedCategoryId,
                                                isPriority = isPriority,
                                                recurringInterval = recurringInterval,
                                                linkUrl = linkUrl.takeIf { it.isNotBlank() },
                                                stickerPath = stickerPath,
                                                requiresReview = false // Cleared after review!
                                            )
                                        )
                                    }
                                },
                                enabled = title.isNotBlank(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("Looks Good")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDatePicker) {
        com.triledge.dailyjournal.ui.components.CustomDatePickerDialog(
            initialDateMillis = dueDate,
            onDismiss = { showCustomDatePicker = false },
            onConfirm = { time ->
                dueDate = time
                showCustomDatePicker = false
            }
        )
    }
}

// AI parser network request
private fun parseVoiceTask(
    transcript: String,
    categories: List<TodoCategory>,
    repository: TodoRepository,
    aiPreferencesRepository: AiPreferencesRepository,
    chatRepository: ChatRepository,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onLoading: (Boolean) -> Unit
) {
    val aiPrefs = aiPreferencesRepository.aiPrefsState.value
    if (aiPrefs.provider == "NONE" || aiPrefs.apiKey.isNullOrBlank()) {
        // Fallback gracefully
        coroutineScope.launch {
            repository.insertItem(
                TodoItem(
                    title = transcript,
                    note = null,
                    dueDate = null,
                    isCompleted = false,
                    categoryId = null,
                    createdAt = System.currentTimeMillis(),
                    requiresReview = true
                )
            )
            Toast.makeText(context, "Added raw task title (AI not configured)", Toast.LENGTH_LONG).show()
        }
        return
    }

    onLoading(true)
    coroutineScope.launch {
        try {
            val categoriesList = categories.map { it.name }.joinToString(", ")
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val systemPrompt = """
                You are a structured voice command task parser.
                Convert the user's transcript into a valid JSON array of tasks.
                Available categories: $categoriesList.
                Current Date reference: $todayStr.
                
                Respond ONLY with a valid JSON array matching this exact schema:
                [
                  {
                    "title": "Task title",
                    "note": "Optional details/note or null",
                    "dueDate": "YYYY-MM-DD or null",
                    "isPriority": true/false (true if they said urgent, important, high priority, etc.),
                    "categoryName": "One of the available categories, or null",
                    "requiresReview": true/false (true if title is unclear or date is ambiguous)
                  }
                ]
                Do not wrap in markdown tags like ```json or explain anything. Just JSON.
            """.trimIndent()

            val response = AiApiClient.getAiResponse(
                provider = aiPrefs.provider,
                apiKey = aiPrefs.apiKey,
                model = aiPrefs.model,
                customEndpoint = aiPrefs.customEndpoint,
                prompt = "$systemPrompt\n\nUser Voice Transcript: \"$transcript\"",
                history = emptyList()
            )

            // Log token usage metrics
            chatRepository.insertTokenUsage(
                provider = aiPrefs.provider,
                model = aiPrefs.model,
                input = response.promptTokens,
                output = response.completionTokens
            )

            val jsonText = response.text.trim()
            val cleanText = if (jsonText.startsWith("```")) {
                val firstNL = jsonText.indexOf('\n')
                val lastFence = jsonText.lastIndexOf("```")
                if (firstNL != -1 && lastFence != -1 && lastFence > firstNL) {
                    jsonText.substring(firstNL + 1, lastFence).trim()
                } else {
                    jsonText.replace("```json", "").replace("```", "").trim()
                }
            } else {
                jsonText
            }

            val array = JSONArray(cleanText)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.optString("title", "").takeIf { it.isNotBlank() } ?: transcript
                val note = obj.optString("note", null)?.takeIf { it.isNotBlank() }
                val dueDateStr = obj.optString("dueDate", null)?.takeIf { it.isNotBlank() }
                val isPriority = obj.optBoolean("isPriority", false)
                val requiresReview = obj.optBoolean("requiresReview", false)
                val categoryName = obj.optString("categoryName", null)

                var catId: Long? = null
                if (categoryName != null) {
                    catId = categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }?.id
                }

                var dueDate: Long? = null
                if (dueDateStr != null) {
                    runCatching {
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        dueDate = fmt.parse(dueDateStr)?.time
                    }
                }

                repository.insertItem(
                    TodoItem(
                        title = title,
                        note = note,
                        dueDate = dueDate,
                        isCompleted = false,
                        categoryId = catId,
                        createdAt = System.currentTimeMillis(),
                        isPriority = isPriority,
                        requiresReview = requiresReview
                    )
                )
            }
            Toast.makeText(context, "Voice task structured successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback
            repository.insertItem(
                TodoItem(
                    title = transcript,
                    note = null,
                    dueDate = null,
                    isCompleted = false,
                    categoryId = null,
                    createdAt = System.currentTimeMillis(),
                    requiresReview = true
                )
            )
            Toast.makeText(context, "AI failed to parse, added raw task", Toast.LENGTH_LONG).show()
        } finally {
            onLoading(false)
        }
    }
}

private var _micIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
val MicIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() {
        if (_micIcon != null) return _micIcon!!
        _micIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Mic body (capsule)
            path(
                fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
            ) {
                moveTo(12f, 2f)
                curveTo(9.79f, 2f, 8f, 3.79f, 8f, 6f)
                lineTo(8f, 12f)
                curveTo(8f, 14.21f, 9.79f, 16f, 12f, 16f)
                curveTo(14.21f, 16f, 16f, 14.21f, 16f, 12f)
                lineTo(16f, 6f)
                curveTo(16f, 3.79f, 14.21f, 2f, 12f, 2f)
                close()
            }
            // Stand arc + pole
            path(
                fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
            ) {
                moveTo(19f, 11f)
                curveTo(19f, 14.87f, 15.83f, 18f, 12f, 18f)
                curveTo(8.17f, 18f, 5f, 14.87f, 5f, 11f)
                lineTo(3f, 11f)
                curveTo(3f, 15.43f, 6.72f, 19.09f, 11f, 19.68f)
                lineTo(11f, 22f)
                lineTo(13f, 22f)
                lineTo(13f, 19.68f)
                curveTo(17.28f, 19.09f, 21f, 15.43f, 21f, 11f)
                lineTo(19f, 11f)
                close()
            }
        }.build()
        return _micIcon!!
    }

