package com.triledge.dailyjournal.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triledge.dailyjournal.data.AiPreferencesRepository
import com.triledge.dailyjournal.data.AiPrefs
import com.triledge.dailyjournal.data.ChatRepository
import com.triledge.dailyjournal.data.QuoteSlot
import com.triledge.dailyjournal.data.QuotesRepository
import com.triledge.dailyjournal.data.SpendingRepository
import com.triledge.dailyjournal.data.TodoRepository
import com.triledge.dailyjournal.data.TradingRepository
import com.triledge.dailyjournal.data.UserPreferencesRepository
import java.util.Calendar
import com.triledge.dailyjournal.data.db.*
import com.triledge.dailyjournal.ui.theme.AppearanceMode
import com.triledge.dailyjournal.ui.theme.BrandPalette
import com.triledge.dailyjournal.ui.theme.ShapeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun getIconByName(name: String): ImageVector {
    return when (name) {
        "shopping_cart" -> Icons.Default.ShoppingCart
        "home" -> Icons.Default.Home
        "favorite" -> Icons.Default.Favorite
        "list" -> Icons.AutoMirrored.Filled.List
        "person" -> Icons.Default.Person
        "star" -> Icons.Default.Star
        else -> Icons.Default.Star
    }
}

val AvailableIcons = listOf(
    "shopping_cart" to Icons.Default.ShoppingCart,
    "home" to Icons.Default.Home,
    "favorite" to Icons.Default.Favorite,
    "list" to Icons.AutoMirrored.Filled.List,
    "person" to Icons.Default.Person,
    "star" to Icons.Default.Star
)

fun parseHexColor(hex: String): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
}

fun backupDatabase(context: Context, destinationUri: android.net.Uri): Boolean {
    return try {
        val dbFile = context.getDatabasePath("triledge_database")
        
        // Cleanly close database instance to flush WAL data into main .db file
        val database = TriledgeDatabase.getDatabase(context.applicationContext)
        database.close()
        
        context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
            dbFile.inputStream().use { inStream ->
                inStream.copyTo(outStream)
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun restoreDatabase(context: Context, sourceUri: android.net.Uri): Boolean {
    return try {
        val dbFile = context.getDatabasePath("triledge_database")
        
        // Cleanly close database before replacing the file
        val database = TriledgeDatabase.getDatabase(context.applicationContext)
        database.close()
        
        context.contentResolver.openInputStream(sourceUri)?.use { inStream ->
            dbFile.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        
        // Delete journal files so that they don't conflict with restored DB
        val walFile = java.io.File(dbFile.path + "-wal")
        if (walFile.exists()) walFile.delete()
        val shmFile = java.io.File(dbFile.path + "-shm")
        if (shmFile.exists()) shmFile.delete()
        
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    spendingRepository: SpendingRepository,
    todoRepository: TodoRepository,
    tradingRepository: TradingRepository,
    chatRepository: ChatRepository,
    userPreferencesRepository: UserPreferencesRepository,
    aiPreferencesRepository: AiPreferencesRepository,
    quotesRepository: QuotesRepository = QuotesRepository(LocalContext.current.applicationContext),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    
    val tokenLogs by chatRepository.allTokenLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val tabs = listOf(
        "General",
        "Spending Categories",
        "To-Do Categories",
        "Broker Presets",
        "Product Types",
        "Accounts",
        "Payment Apps",
        "Quotes",
        "Kite Connect",
        "About & Privacy"
    )

    // Room Flows
    val categories by spendingRepository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    val todoCategories by todoRepository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    val presets by tradingRepository.allPresets.collectAsStateWithLifecycle(initialValue = emptyList())
    val productTypes by tradingRepository.allProductTypes.collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by spendingRepository.allAccountTags.collectAsStateWithLifecycle(initialValue = emptyList())
    val paymentApps by spendingRepository.allPaymentAppTags.collectAsStateWithLifecycle(initialValue = emptyList())

    // Datastore Preference flow
    val userPrefs by userPreferencesRepository.userPrefs.collectAsStateWithLifecycle(initialValue = null)
    
    // Keystore AI Preferences flow
    val aiPrefs by aiPreferencesRepository.aiPrefsState.collectAsStateWithLifecycle(initialValue = AiPrefs.Default)

    // Dialog state
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<SpendingCategory?>(null) }

    var showTodoCategoryDialog by remember { mutableStateOf(false) }
    var editingTodoCategory by remember { mutableStateOf<TodoCategory?>(null) }

    var showPresetDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<BrokerPreset?>(null) }

    var showProductTypeDialog by remember { mutableStateOf(false) }
    var editingProductType by remember { mutableStateOf<ProductType?>(null) }

    var showTagDialog by remember { mutableStateOf<TagType?>(null) }
    var editingAccountTag by remember { mutableStateOf<AccountTag?>(null) }
    var editingPaymentAppTag by remember { mutableStateOf<PaymentAppTag?>(null) }

    // SAF Launchers
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    val success = backupDatabase(context, uri)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, "Backup successful!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Backup failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    val success = restoreDatabase(context, uri)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, "Database restored! Please restart the app.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Restore failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedTab in 1..6) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            1 -> {
                                editingCategory = null
                                showCategoryDialog = true
                            }
                            2 -> {
                                editingTodoCategory = null
                                showTodoCategoryDialog = true
                            }
                            3 -> {
                                editingPreset = null
                                showPresetDialog = true
                            }
                            4 -> {
                                editingProductType = null
                                showProductTypeDialog = true
                            }
                            5 -> {
                                editingAccountTag = null
                                showTagDialog = TagType.ACCOUNT
                            }
                            6 -> {
                                editingPaymentAppTag = null
                                showTagDialog = TagType.PAYMENT_APP
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    userPrefs?.let { prefs ->
                        GeneralSettingsPanel(
                            prefs = prefs,
                            aiPrefs = aiPrefs,
                            tokenLogs = tokenLogs,
                            onUpdateName = { coroutineScope.launch { userPreferencesRepository.setName(it) } },
                            onUpdateTheme = { coroutineScope.launch { userPreferencesRepository.setAppearanceMode(it) } },
                            onUpdateColor = { coroutineScope.launch { userPreferencesRepository.setSeedColor(it) } },
                            onUpdateShape = { coroutineScope.launch { userPreferencesRepository.setShapeStyle(it) } },
                            onUpdateAiProvider = { aiPreferencesRepository.setProvider(it) },
                            onUpdateAiApiKey = { aiPreferencesRepository.setApiKey(it) },
                            onUpdateAiModel = { aiPreferencesRepository.setModel(it) },
                            onUpdateAiCustomEndpoint = { aiPreferencesRepository.setCustomEndpoint(it) },
                            onSaveRates = { geminiIn, geminiOut, openAiIn, openAiOut ->
                                aiPreferencesRepository.setRates(geminiIn, geminiOut, openAiIn, openAiOut)
                            },
                            onClearUsage = {
                                coroutineScope.launch { chatRepository.clearTokenLogs() }
                            },
                            onBackupClick = { backupLauncher.launch("triledge_backup.db") },
                            onRestoreClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
                        )
                    }
                }
                1 -> CategoryList(
                    categories = categories,
                    onEdit = {
                        editingCategory = it
                        showCategoryDialog = true
                    },
                    onDelete = {
                        coroutineScope.launch { spendingRepository.deleteCategory(it) }
                    }
                )
                2 -> TodoCategoryList(
                    categories = todoCategories,
                    onEdit = {
                        editingTodoCategory = it
                        showTodoCategoryDialog = true
                    },
                    onDelete = {
                        coroutineScope.launch { todoRepository.deleteCategory(it) }
                    }
                )
                3 -> PresetList(
                    presets = presets,
                    onEdit = {
                        editingPreset = it
                        showPresetDialog = true
                    },
                    onDelete = {
                        coroutineScope.launch { tradingRepository.deletePreset(it) }
                    }
                )
                4 -> ProductTypeList(
                    productTypes = productTypes,
                    onEdit = {
                        editingProductType = it
                        showProductTypeDialog = true
                    },
                    onDelete = {
                        coroutineScope.launch { tradingRepository.deleteProductType(it) }
                    }
                )
                5 -> AccountTagList(
                    tags = accounts,
                    onEdit = {
                        editingAccountTag = it
                        showTagDialog = TagType.ACCOUNT
                    },
                    onDelete = {
                        coroutineScope.launch { spendingRepository.deleteAccountTag(it) }
                    }
                )
                6 -> PaymentAppTagList(
                    tags = paymentApps,
                    onEdit = {
                        editingPaymentAppTag = it
                        showTagDialog = TagType.PAYMENT_APP
                    },
                    onDelete = {
                        coroutineScope.launch { spendingRepository.deletePaymentAppTag(it) }
                    }
                )
                7 -> QuotesSettingsPanel(quotesRepository = quotesRepository)
                8 -> {
                    userPrefs?.let { prefs ->
                        KiteConnectPanel(
                            prefs = prefs,
                            userPreferencesRepository = userPreferencesRepository,
                            tradingRepository = tradingRepository
                        )
                    }
                }
                9 -> AboutPrivacyPanel()
            }
        }
    }

    // Spending Category Add/Edit Dialog
    if (showCategoryDialog) {
        CategoryEditDialog(
            title = if (editingCategory == null) "Add Spending Category" else "Edit Spending Category",
            initialName = editingCategory?.name ?: "",
            initialColorHex = editingCategory?.colorHex,
            initialIconName = editingCategory?.iconName,
            onDismiss = { showCategoryDialog = false },
            onConfirm = { name, colorHex, iconName ->
                coroutineScope.launch {
                    if (editingCategory == null) {
                        spendingRepository.insertCategory(
                            SpendingCategory(
                                name = name,
                                colorHex = colorHex,
                                iconName = iconName
                            )
                        )
                    } else {
                        spendingRepository.updateCategory(
                            editingCategory!!.copy(
                                name = name,
                                colorHex = colorHex,
                                iconName = iconName
                            )
                        )
                    }
                }
                showCategoryDialog = false
            }
        )
    }

    // To-Do Category Add/Edit Dialog
    if (showTodoCategoryDialog) {
        CategoryEditDialog(
            title = if (editingTodoCategory == null) "Add To-Do Category" else "Edit To-Do Category",
            initialName = editingTodoCategory?.name ?: "",
            initialColorHex = editingTodoCategory?.colorHex,
            initialIconName = editingTodoCategory?.iconName,
            onDismiss = { showTodoCategoryDialog = false },
            onConfirm = { name, colorHex, iconName ->
                coroutineScope.launch {
                    if (editingTodoCategory == null) {
                        todoRepository.insertCategory(
                            TodoCategory(
                                name = name,
                                colorHex = colorHex,
                                iconName = iconName
                            )
                        )
                    } else {
                        todoRepository.updateCategory(
                            editingTodoCategory!!.copy(
                                name = name,
                                colorHex = colorHex,
                                iconName = iconName
                            )
                        )
                    }
                }
                showTodoCategoryDialog = false
            }
        )
    }

    // Broker Preset Edit Dialog
    if (showPresetDialog) {
        BrokerPresetEditDialog(
            preset = editingPreset,
            onDismiss = { showPresetDialog = false },
            onConfirm = { presetData ->
                coroutineScope.launch {
                    if (editingPreset == null) {
                        tradingRepository.insertPreset(presetData)
                    } else {
                        tradingRepository.updatePreset(presetData.copy(id = editingPreset!!.id))
                    }
                }
                showPresetDialog = false
            }
        )
    }

    // Product Type Edit Dialog
    if (showProductTypeDialog) {
        TagEditDialog(
            title = "Product Type",
            initialName = editingProductType?.name ?: "",
            onDismiss = { showProductTypeDialog = false },
            onConfirm = { name ->
                coroutineScope.launch {
                    if (editingProductType == null) {
                        tradingRepository.insertProductType(ProductType(name = name))
                    } else {
                        tradingRepository.updateProductType(editingProductType!!.copy(name = name))
                    }
                }
                showProductTypeDialog = false
            }
        )
    }

    // Account/Payment App Tag Add/Edit Dialog
    if (showTagDialog != null) {
        val dialogType = showTagDialog!!
        val currentName = if (dialogType == TagType.ACCOUNT) {
            editingAccountTag?.name ?: ""
        } else {
            editingPaymentAppTag?.name ?: ""
        }

        TagEditDialog(
            title = if (dialogType == TagType.ACCOUNT) "Account Tag" else "Payment App Tag",
            initialName = currentName,
            onDismiss = { showTagDialog = null },
            onConfirm = { name ->
                coroutineScope.launch {
                    if (dialogType == TagType.ACCOUNT) {
                        if (editingAccountTag == null) {
                            spendingRepository.insertAccountTag(AccountTag(name = name))
                        } else {
                            spendingRepository.updateAccountTag(editingAccountTag!!.copy(name = name))
                        }
                    } else {
                        if (editingPaymentAppTag == null) {
                            spendingRepository.insertPaymentAppTag(PaymentAppTag(name = name))
                        } else {
                            spendingRepository.updatePaymentAppTag(editingPaymentAppTag!!.copy(name = name))
                        }
                    }
                }
                showTagDialog = null
            }
        )
    }
}

@Composable
fun GeneralSettingsPanel(
    prefs: com.triledge.dailyjournal.data.UserPrefs,
    aiPrefs: AiPrefs,
    tokenLogs: List<com.triledge.dailyjournal.data.db.TokenUsageLog>,
    onUpdateName: (String) -> Unit,
    onUpdateTheme: (AppearanceMode) -> Unit,
    onUpdateColor: (com.triledge.dailyjournal.ui.theme.BrandColor) -> Unit,
    onUpdateShape: (ShapeStyle) -> Unit,
    onUpdateAiProvider: (String) -> Unit,
    onUpdateAiApiKey: (String) -> Unit,
    onUpdateAiModel: (String) -> Unit,
    onUpdateAiCustomEndpoint: (String?) -> Unit,
    onSaveRates: (geminiIn: Double, geminiOut: Double, openAiIn: Double, openAiOut: Double) -> Unit,
    onClearUsage: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nameText by remember(prefs.name) { mutableStateOf(prefs.name ?: "") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Name configuration Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("User Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { if (nameText.isNotBlank()) onUpdateName(nameText) },
                        enabled = nameText.isNotBlank() && nameText != prefs.name,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        // Theme and Seed Color configs
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Appearance Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Theme mode selection
                Column {
                    Text("Theme Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppearanceMode.values().filter { it != AppearanceMode.Amoled }.forEach { mode ->
                            val isSelected = prefs.appearanceMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onUpdateTheme(mode) },
                                label = { Text(mode.name) }
                            )
                        }
                    }
                }

                // Brand seed color selection
                Column {
                    Text("Theme Seed Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrandPalette.All.forEach { brandColor ->
                            val isSelected = prefs.seedColor.id == brandColor.id
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(brandColor.seed)
                                    .clickable { onUpdateColor(brandColor) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // AI Chat Configuration Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI Chat Configuration (Opt-In)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Text(
                    text = "Provide your own API Key to enable local AI Chat. This key is stored securely in EncryptedSharedPreferences on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Provider chips
                Column {
                    Text("AI Provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("NONE" to "Disabled", "GEMINI" to "Gemini", "OPENAI" to "OpenAI").forEach { (id, label) ->
                            val isSelected = aiPrefs.provider == id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onUpdateAiProvider(id)
                                    if (id == "GEMINI" && aiPrefs.model.isBlank()) {
                                        onUpdateAiModel("gemini-1.5-flash")
                                    } else if (id == "OPENAI" && aiPrefs.model.isBlank()) {
                                        onUpdateAiModel("gpt-4o-mini")
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                if (aiPrefs.provider != "NONE") {
                    var apiKeyText by remember(aiPrefs.apiKey) { mutableStateOf(aiPrefs.apiKey ?: "") }
                    var modelText by remember(aiPrefs.model) { mutableStateOf(aiPrefs.model) }
                    var endpointText by remember(aiPrefs.customEndpoint) { mutableStateOf(aiPrefs.customEndpoint ?: "") }

                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modelText,
                        onValueChange = { modelText = it },
                        label = { Text("Model Name (e.g. gemini-1.5-flash or gpt-4o-mini)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = endpointText,
                        onValueChange = { endpointText = it },
                        label = { Text("Custom Endpoint (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onUpdateAiApiKey(apiKeyText)
                            onUpdateAiModel(modelText)
                            onUpdateAiCustomEndpoint(endpointText.takeIf { it.isNotBlank() })
                        },
                        enabled = apiKeyText.isNotBlank() && (apiKeyText != aiPrefs.apiKey || modelText != aiPrefs.model || endpointText != (aiPrefs.customEndpoint ?: "")),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save AI Config")
                    }
                }
            }
        }

        // Token Usage Logs and Costs Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI Token Usage & Cost Tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Calculate metrics
                val startOfToday = remember {
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }

                val todayLogs = remember(tokenLogs, startOfToday) {
                    tokenLogs.filter { it.timestamp >= startOfToday }
                }

                fun calculateCost(logs: List<com.triledge.dailyjournal.data.db.TokenUsageLog>): Double {
                    var total = 0.0
                    logs.forEach { log ->
                        val (inRate, outRate) = if (log.provider.equals("GEMINI", ignoreCase = true)) {
                            aiPrefs.geminiInputRate to aiPrefs.geminiOutputRate
                        } else {
                            aiPrefs.openAiInputRate to aiPrefs.openAiOutputRate
                        }
                        total += (log.inputTokens / 1_000_000.0) * inRate + (log.outputTokens / 1_000_000.0) * outRate
                    }
                    return total
                }

                val todayInputTokens = todayLogs.sumOf { it.inputTokens }
                val todayOutputTokens = todayLogs.sumOf { it.outputTokens }
                val todayCost = calculateCost(todayLogs)

                val allInputTokens = tokenLogs.sumOf { it.inputTokens }
                val allOutputTokens = tokenLogs.sumOf { it.outputTokens }
                val allCost = calculateCost(tokenLogs)

                val costFormat = remember { java.text.DecimalFormat("$#,##0.000000") }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Today's Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Input: $todayInputTokens tokens", style = MaterialTheme.typography.bodyMedium)
                        Text("Output: $todayOutputTokens tokens", style = MaterialTheme.typography.bodyMedium)
                        Text("Cost: ${costFormat.format(todayCost)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("All-Time Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Input: $allInputTokens tokens", style = MaterialTheme.typography.bodyMedium)
                        Text("Output: $allOutputTokens tokens", style = MaterialTheme.typography.bodyMedium)
                        Text("Cost: ${costFormat.format(allCost)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Rate Multipliers (USD per million tokens)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Verify current pricing details with your provider before relying on cost estimates.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                var geminiInText by remember(aiPrefs.geminiInputRate) { mutableStateOf(aiPrefs.geminiInputRate.toString()) }
                var geminiOutText by remember(aiPrefs.geminiOutputRate) { mutableStateOf(aiPrefs.geminiOutputRate.toString()) }
                var openaiInText by remember(aiPrefs.openAiInputRate) { mutableStateOf(aiPrefs.openAiInputRate.toString()) }
                var openaiOutText by remember(aiPrefs.openAiOutputRate) { mutableStateOf(aiPrefs.openAiOutputRate.toString()) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = geminiInText,
                        onValueChange = { geminiInText = it },
                        label = { Text("Gemini In") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = geminiOutText,
                        onValueChange = { geminiOutText = it },
                        label = { Text("Gemini Out") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = openaiInText,
                        onValueChange = { openaiInText = it },
                        label = { Text("OpenAI In") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = openaiOutText,
                        onValueChange = { openaiOutText = it },
                        label = { Text("OpenAI Out") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onClearUsage,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset Logs")
                    }

                    Button(
                        onClick = {
                            val gIn = geminiInText.toDoubleOrNull() ?: aiPrefs.geminiInputRate
                            val gOut = geminiOutText.toDoubleOrNull() ?: aiPrefs.geminiOutputRate
                            val oIn = openaiInText.toDoubleOrNull() ?: aiPrefs.openAiInputRate
                            val oOut = openaiOutText.toDoubleOrNull() ?: aiPrefs.openAiOutputRate
                            onSaveRates(gIn, gOut, oIn, oOut)
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Save Rates")
                    }
                }
            }
        }

        // Database backup & restore Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Database Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Text(
                    text = "Backup your local database transactions, tasks, and trading history to a file, or restore a previous backup to this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onBackupClick,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Backup DB")
                    }

                    OutlinedButton(
                        onClick = onRestoreClick,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restore DB")
                    }
                }

                Text(
                    text = "Warning: Restoring will overwrite all current logs and data on this device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

enum class TagType { ACCOUNT, PAYMENT_APP }

@Composable
fun CategoryList(
    categories: List<SpendingCategory>,
    onEdit: (SpendingCategory) -> Unit,
    onDelete: (SpendingCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id }) { cat ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(cat.colorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getIconByName(cat.iconName),
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row {
                        IconButton(onClick = { onEdit(cat) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDelete(cat) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TodoCategoryList(
    categories: List<TodoCategory>,
    onEdit: (TodoCategory) -> Unit,
    onDelete: (TodoCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id }) { cat ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(cat.colorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getIconByName(cat.iconName),
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row {
                        IconButton(onClick = { onEdit(cat) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDelete(cat) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetList(
    presets: List<BrokerPreset>,
    onEdit: (BrokerPreset) -> Unit,
    onDelete: (BrokerPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Verify these preset rates against your broker's current charges and tax sheets before relying on calculated P&L figures.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        items(presets, key = { it.id }) { preset ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = { onEdit(preset) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onDelete(preset) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Brokerage Flat: ₹ ${preset.brokerageFlat}", style = MaterialTheme.typography.bodySmall)
                            Text("Brokerage %: ${preset.brokeragePercent}%", style = MaterialTheme.typography.bodySmall)
                            Text("STT Rate: ${preset.sttPercent}%", style = MaterialTheme.typography.bodySmall)
                            Text("Exch. Tx: ${preset.exchangeTxPercent}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SEBI Charge: ${preset.sebiTurnoverPercent}%", style = MaterialTheme.typography.bodySmall)
                            Text("Stamp Duty: ${preset.stampDutyPercent}%", style = MaterialTheme.typography.bodySmall)
                            Text("GST Rate: ${preset.gstPercent}%", style = MaterialTheme.typography.bodySmall)
                            Text("DP Charge: ₹ ${preset.dpCharge}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductTypeList(
    productTypes: List<ProductType>,
    onEdit: (ProductType) -> Unit,
    onDelete: (ProductType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(productTypes, key = { it.id }) { pt ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pt.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        IconButton(onClick = { onEdit(pt) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDelete(pt) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrokerPresetEditDialog(
    preset: BrokerPreset?,
    onDismiss: () -> Unit,
    onConfirm: (BrokerPreset) -> Unit
) {
    var name by remember { mutableStateOf(preset?.name ?: "") }
    var brokerageFlat by remember { mutableStateOf(preset?.brokerageFlat?.toString() ?: "20.0") }
    var brokeragePercent by remember { mutableStateOf(preset?.brokeragePercent?.toString() ?: "0.03") }
    var sttPercent by remember { mutableStateOf(preset?.sttPercent?.toString() ?: "0.025") }
    var exchangeTxPercent by remember { mutableStateOf(preset?.exchangeTxPercent?.toString() ?: "0.00343") }
    var sebiTurnoverPercent by remember { mutableStateOf(preset?.sebiTurnoverPercent?.toString() ?: "0.0001") }
    var stampDutyPercent by remember { mutableStateOf(preset?.stampDutyPercent?.toString() ?: "0.003") }
    var gstPercent by remember { mutableStateOf(preset?.gstPercent?.toString() ?: "18.0") }
    var dpCharge by remember { mutableStateOf(preset?.dpCharge?.toString() ?: "0.0") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (preset == null) "Add Broker Preset" else "Edit Broker Preset",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name (e.g. Zerodha Intraday)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brokerageFlat,
                        onValueChange = { brokerageFlat = it },
                        label = { Text("Flat Brokerage (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = brokeragePercent,
                        onValueChange = { brokeragePercent = it },
                        label = { Text("Brokerage %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sttPercent,
                        onValueChange = { sttPercent = it },
                        label = { Text("STT Rate %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = exchangeTxPercent,
                        onValueChange = { exchangeTxPercent = it },
                        label = { Text("Exchange Tx %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sebiTurnoverPercent,
                        onValueChange = { sebiTurnoverPercent = it },
                        label = { Text("SEBI turnover %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stampDutyPercent,
                        onValueChange = { stampDutyPercent = it },
                        label = { Text("Stamp Duty %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gstPercent,
                        onValueChange = { gstPercent = it },
                        label = { Text("GST Rate %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dpCharge,
                        onValueChange = { dpCharge = it },
                        label = { Text("DP Charge (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
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
                            if (name.isNotBlank()) {
                                onConfirm(
                                    BrokerPreset(
                                        name = name,
                                        brokerageFlat = brokerageFlat.toDoubleOrNull() ?: 0.0,
                                        brokeragePercent = brokeragePercent.toDoubleOrNull() ?: 0.0,
                                        sttPercent = sttPercent.toDoubleOrNull() ?: 0.0,
                                        exchangeTxPercent = exchangeTxPercent.toDoubleOrNull() ?: 0.0,
                                        sebiTurnoverPercent = sebiTurnoverPercent.toDoubleOrNull() ?: 0.0,
                                        stampDutyPercent = stampDutyPercent.toDoubleOrNull() ?: 0.0,
                                        gstPercent = gstPercent.toDoubleOrNull() ?: 0.0,
                                        dpCharge = dpCharge.toDoubleOrNull() ?: 0.0
                                    )
                                )
                            }
                        },
                        enabled = name.isNotBlank(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun AccountTagList(
    tags: List<AccountTag>,
    onEdit: (AccountTag) -> Unit,
    onDelete: (AccountTag) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tags, key = { it.id }) { tag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        IconButton(onClick = { onEdit(tag) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDelete(tag) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentAppTagList(
    tags: List<PaymentAppTag>,
    onEdit: (PaymentAppTag) -> Unit,
    onDelete: (PaymentAppTag) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tags, key = { it.id }) { tag ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        IconButton(onClick = { onEdit(tag) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDelete(tag) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditDialog(
    title: String,
    initialName: String = "",
    initialColorHex: String? = null,
    initialIconName: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember {
        mutableStateOf(initialColorHex ?: BrandPalette.All.first().id)
    }
    var selectedIcon by remember { mutableStateOf(initialIconName ?: "star") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Pick Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrandPalette.All.forEach { brandColor ->
                        val hex = String.format("#%06X", 0xFFFFFF and brandColor.seed.value.toInt())
                        val isSelected = selectedColor == hex || selectedColor == brandColor.id
                        
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(brandColor.seed)
                                .clickable { selectedColor = hex }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Pick Icon",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvailableIcons.forEach { (iconKey, vector) ->
                        val isSelected = selectedIcon == iconKey
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable { selectedIcon = iconKey }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                       else MaterialTheme.colorScheme.onSurface
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
                            val hex = if (selectedColor.startsWith("#")) selectedColor else {
                                val found = BrandPalette.byId(selectedColor) ?: BrandPalette.Default
                                String.format("#%06X", 0xFFFFFF and found.seed.value.toInt())
                            }
                            if (name.isNotBlank()) {
                                onConfirm(name, hex, selectedIcon)
                            }
                        },
                        enabled = name.isNotBlank(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun TagEditDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (initialName.isEmpty()) "Add $title" else "Edit $title",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                            if (name.isNotBlank()) {
                                onConfirm(name)
                            }
                        },
                        enabled = name.isNotBlank(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ── Quotes Settings Panel ──

@Composable
fun QuotesSettingsPanel(
    quotesRepository: QuotesRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by quotesRepository.settings.collectAsStateWithLifecycle()

    var morningCount by remember { mutableStateOf(quotesRepository.getQuoteCount(QuoteSlot.MORNING)) }
    var eveningCount by remember { mutableStateOf(quotesRepository.getQuoteCount(QuoteSlot.EVENING)) }
    var nightCount by remember { mutableStateOf(quotesRepository.getQuoteCount(QuoteSlot.NIGHT)) }

    fun refreshCounts() {
        morningCount = quotesRepository.getQuoteCount(QuoteSlot.MORNING)
        eveningCount = quotesRepository.getQuoteCount(QuoteSlot.EVENING)
        nightCount = quotesRepository.getQuoteCount(QuoteSlot.NIGHT)
    }

    // File pickers for each slot
    val morningPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@let
            quotesRepository.importQuotes(QuoteSlot.MORNING, text)
            refreshCounts()
            Toast.makeText(context, "Morning quotes imported!", Toast.LENGTH_SHORT).show()
        }
    }
    val eveningPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@let
            quotesRepository.importQuotes(QuoteSlot.EVENING, text)
            refreshCounts()
            Toast.makeText(context, "Evening quotes imported!", Toast.LENGTH_SHORT).show()
        }
    }
    val nightPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@let
            quotesRepository.importQuotes(QuoteSlot.NIGHT, text)
            refreshCounts()
            Toast.makeText(context, "Night quotes imported!", Toast.LENGTH_SHORT).show()
        }
    }

    // Export launchers
    val morningExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            val text = quotesRepository.exportQuotes(QuoteSlot.MORNING)
            context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { w -> w.write(text) }
            Toast.makeText(context, "Morning quotes exported!", Toast.LENGTH_SHORT).show()
        }
    }
    val eveningExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            val text = quotesRepository.exportQuotes(QuoteSlot.EVENING)
            context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { w -> w.write(text) }
            Toast.makeText(context, "Evening quotes exported!", Toast.LENGTH_SHORT).show()
        }
    }
    val nightExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            val text = quotesRepository.exportQuotes(QuoteSlot.NIGHT)
            context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { w -> w.write(text) }
            Toast.makeText(context, "Night quotes exported!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Motivational Quotes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Upload a plain-text file for each time window. Use one quote per line, " +
            "or separate multi-line quotes with a blank line.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Morning
        QuoteSlotCard(
            label = "☀\uFE0F Morning",
            timeRange = "${settings.morningStart}:00 – ${settings.morningEnd}:00",
            count = morningCount,
            onImport = { morningPicker.launch(arrayOf("text/plain", "*/*")) },
            onExport = { morningExporter.launch("morning_quotes.txt") }
        )

        // Evening
        QuoteSlotCard(
            label = "\uD83C\uDF05 Evening",
            timeRange = "${settings.eveningStart}:00 – ${settings.eveningEnd}:00",
            count = eveningCount,
            onImport = { eveningPicker.launch(arrayOf("text/plain", "*/*")) },
            onExport = { eveningExporter.launch("evening_quotes.txt") }
        )

        // Night
        QuoteSlotCard(
            label = "\uD83C\uDF19 Night",
            timeRange = "${settings.nightStart}:00 – ${settings.nightEnd}:00",
            count = nightCount,
            onImport = { nightPicker.launch(arrayOf("text/plain", "*/*")) },
            onExport = { nightExporter.launch("night_quotes.txt") }
        )

        // Time Window Config
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Time Windows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Adjust when each slot is active (24-hour format). " +
                    "Changes apply next time the app opens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                var mStart by remember(settings) { mutableStateOf(settings.morningStart.toString()) }
                var mEnd by remember(settings) { mutableStateOf(settings.morningEnd.toString()) }
                var eEnd by remember(settings) { mutableStateOf(settings.eveningEnd.toString()) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mStart,
                        onValueChange = { mStart = it },
                        label = { Text("Morning Start") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mEnd,
                        onValueChange = { mEnd = it },
                        label = { Text("Morning End") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = eEnd,
                        onValueChange = { eEnd = it },
                        label = { Text("Evening End") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Button(
                    onClick = {
                        val ms = mStart.toIntOrNull() ?: return@Button
                        val me = mEnd.toIntOrNull() ?: return@Button
                        val ee = eEnd.toIntOrNull() ?: return@Button
                        quotesRepository.updateSettings(
                            com.triledge.dailyjournal.data.QuoteSettings(
                                morningStart = ms.coerceIn(0, 23),
                                morningEnd = me.coerceIn(0, 23),
                                eveningStart = me.coerceIn(0, 23),
                                eveningEnd = ee.coerceIn(0, 23),
                                nightStart = ee.coerceIn(0, 23),
                                nightEnd = ms.coerceIn(0, 23)
                            )
                        )
                        Toast.makeText(context, "Time windows updated", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Save Time Windows")
                }
            }
        }
    }
}

@Composable
private fun QuoteSlotCard(
    label: String,
    timeRange: String,
    count: Int,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(timeRange, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "$count quotes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), enabled = count > 0) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Re-uploading replaces all quotes in this slot.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── About & Privacy Panel ──

@Composable
fun AboutPrivacyPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Triledge",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            "Your lightweight, offline-first daily journal for to-dos, spending, and trading.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Text(
            "What Stays on Your Device",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        PrivacyItem(
            icon = Icons.Default.CheckCircle,
            text = "All to-do items, spending entries, and trade records",
            isPositive = true
        )
        PrivacyItem(
            icon = Icons.Default.CheckCircle,
            text = "Your name, preferences, and theming choices",
            isPositive = true
        )
        PrivacyItem(
            icon = Icons.Default.CheckCircle,
            text = "Categories, account tags, broker presets",
            isPositive = true
        )
        PrivacyItem(
            icon = Icons.Default.CheckCircle,
            text = "Quote files and consistency tracker data",
            isPositive = true
        )
        PrivacyItem(
            icon = Icons.Default.CheckCircle,
            text = "Voice audio — transcribed on-device, raw audio never leaves",
            isPositive = true
        )

        HorizontalDivider()

        Text(
            "What May Leave Your Device (Opt-In Only)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        PrivacyItem(
            icon = Icons.Default.Warning,
            text = "Voice transcript text → sent to your configured AI provider for task structuring (Agentic To-Do)",
            isPositive = false
        )
        PrivacyItem(
            icon = Icons.Default.Warning,
            text = "AI Chat messages → sent to your configured AI provider's cloud API",
            isPositive = false
        )
        PrivacyItem(
            icon = Icons.Default.Warning,
            text = "Link preview fetch → a single HTTP request to the shared URL to fetch thumbnail metadata",
            isPositive = false
        )
        PrivacyItem(
            icon = Icons.Default.Warning,
            text = "Kite Connect (if enabled) → fetches trade data from Zerodha using your personal API credentials",
            isPositive = false
        )

        HorizontalDivider()

        Text(
            "What This App Never Does",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        PrivacyItem(
            icon = Icons.Default.Close,
            text = "No analytics SDKs, no ad SDKs, no telemetry",
            isPositive = true
        )
        PrivacyItem(
            icon = Icons.Default.Close,
            text = "No SMS, Call Log, or Notification Listener access",
            isPositive = true
        )
        PrivacyItem(
            icon = Icons.Default.Close,
            text = "No cloud sync, no login system, no account creation",
            isPositive = true
        )
        PrivacyItem(
            icon = Icons.Default.Close,
            text = "No auto-categorization or automated spending nudges",
            isPositive = true
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Permissions Used",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text("• RECORD_AUDIO — on-device voice transcription for Agentic To-Do", style = MaterialTheme.typography.bodySmall)
                Text("• INTERNET — AI Chat, Agentic To-Do AI parsing, link previews, Kite Connect (all opt-in)", style = MaterialTheme.typography.bodySmall)
                Text("• POST_NOTIFICATIONS — recurring task reminders (if enabled)", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PrivacyItem(
    icon: ImageVector,
    text: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPositive) Color(0xFF22C55E) else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Kite Connect Panel ──

@Composable
fun KiteConnectPanel(
    prefs: com.triledge.dailyjournal.data.UserPrefs,
    userPreferencesRepository: UserPreferencesRepository,
    tradingRepository: TradingRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var apiKeyText by remember(prefs.kiteApiKey) { mutableStateOf(prefs.kiteApiKey ?: "") }
    var accessTokenText by remember(prefs.kiteAccessToken) { mutableStateOf(prefs.kiteAccessToken ?: "") }
    var isSyncing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Zerodha Kite Connect",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Integrate Zerodha Kite Connect to automatically import your closed positions. " +
            "Fetched trade data is stored 100% locally and calculated using your presets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Connection Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Kite Integration", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = prefs.kiteEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Show opt-in disclosure Toast / dialog simple
                                Toast.makeText(
                                    context,
                                    "Kite Connect integration enabled. API queries are made on-demand.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            coroutineScope.launch {
                                userPreferencesRepository.setKiteEnabled(enabled)
                            }
                        }
                    )
                }

                if (prefs.kiteEnabled) {
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it
                            coroutineScope.launch { userPreferencesRepository.setKiteApiKey(it) }
                        },
                        label = { Text("Kite API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accessTokenText,
                        onValueChange = {
                            accessTokenText = it
                            coroutineScope.launch { userPreferencesRepository.setKiteAccessToken(it) }
                        },
                        label = { Text("Kite Access Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "How to get credentials:\n" +
                        "1. Create a developer account at kite.trade\n" +
                        "2. Create a Personal app (free)\n" +
                        "3. Copy the API Key, log in to generate Request Token, then exchange for Access Token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    if (isSyncing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing with Zerodha...")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (apiKeyText.isBlank() || accessTokenText.isBlank()) {
                                    Toast.makeText(context, "Please enter both API key and Access token", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSyncing = true
                                coroutineScope.launch {
                                    val kiteTrades = com.triledge.dailyjournal.data.KiteConnectClient.fetchTrades(
                                        apiKeyText.trim(),
                                        accessTokenText.trim()
                                    )
                                    if (kiteTrades.isEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "No trades fetched. Check credentials or connection.", Toast.LENGTH_LONG).show()
                                            isSyncing = false
                                        }
                                        return@launch
                                    }

                                    val matched = com.triledge.dailyjournal.data.KiteConnectClient.matchTrades(kiteTrades)
                                    var count = 0
                                    for (trade in matched) {
                                        tradingRepository.insertTrade(trade)
                                        count++
                                    }

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Successfully synced $count trades!", Toast.LENGTH_LONG).show()
                                        isSyncing = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Sync Trades Now")
                        }
                    }
                }
            }
        }
    }
}
