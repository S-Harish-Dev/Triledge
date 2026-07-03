package com.triledge.dailyjournal.ui.tabs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triledge.dailyjournal.data.SpendingRepository
import com.triledge.dailyjournal.data.db.SpendingEntry
import com.triledge.dailyjournal.data.db.SpendingEntryWithRelations
import com.triledge.dailyjournal.ui.settings.getIconByName
import com.triledge.dailyjournal.ui.settings.parseHexColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SpendingTab(
    repository: SpendingRepository,
    addTrigger: Int = 0,
    onConsumeAddTrigger: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var viewMode by remember { mutableStateOf(0) } // 0 = Transactions, 1 = Summary
    var showAddDialog by remember { mutableStateOf(false) }

    val entries by repository.allEntries.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by repository.allAccountTags.collectAsStateWithLifecycle(initialValue = emptyList())
    val paymentApps by repository.allPaymentAppTags.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(addTrigger) {
        if (addTrigger > 0) {
            showAddDialog = true
            onConsumeAddTrigger()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // View Mode Selector
            TabRow(selectedTabIndex = viewMode) {
                Tab(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    text = { Text("Transactions") }
                )
                Tab(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    text = { Text("Summary") }
                )
            }

            Spacer(Modifier.height(8.dp))

            if (viewMode == 0) {
                TransactionsView(
                    entries = entries,
                    onDelete = { entry ->
                        coroutineScope.launch { repository.deleteEntry(entry) }
                    }
                )
            } else {
                SummaryView(entries = entries)
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            categories = categories,
            accounts = accounts,
            paymentApps = paymentApps,
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, categoryId, note, timestamp, accountId, paymentId ->
                coroutineScope.launch {
                    repository.insertEntry(
                        SpendingEntry(
                            amount = amount,
                            categoryId = categoryId,
                            note = note,
                            timestamp = timestamp,
                            accountTagId = accountId,
                            paymentAppTagId = paymentId
                        )
                    )
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TransactionsView(
    entries: List<SpendingEntryWithRelations>,
    onDelete: (SpendingEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No expenses added yet.\nTap the + button to log one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.entry.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Icon Badge
                        val categoryColor = item.category?.colorHex?.let { parseHexColor(it) }
                            ?: MaterialTheme.colorScheme.surfaceVariant
                        val categoryIcon = item.category?.iconName?.let { getIconByName(it) }
                            ?: getIconByName("star")

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(categoryColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = null,
                                tint = if (item.category != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        // Middle details
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.category?.name ?: "Uncategorized",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format("₹%.2f", item.entry.amount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (!item.entry.note.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = item.entry.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Bottom Tags & Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.accountTag != null) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(item.accountTag.name) },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                    if (item.paymentApp != null) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(item.paymentApp.name) },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = dateFormat.format(Date(item.entry.timestamp)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Delete button
                        IconButton(onClick = { onDelete(item.entry) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryView(
    entries: List<SpendingEntryWithRelations>,
    modifier: Modifier = Modifier
) {
    var periodTab by remember { mutableStateOf(0) } // 0 = Today, 1 = Week, 2 = Month
    val periods = listOf("Today", "This Week", "This Month")

    var breakdownTab by remember { mutableStateOf(0) } // 0 = Category, 1 = Account, 2 = Payment App
    val breakdowns = listOf("By Category", "By Account", "By Payment App")

    val filteredEntries = remember(entries, periodTab) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val startOfToday = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val limitTimestamp = when (periodTab) {
            0 -> startOfToday
            1 -> now - 7L * 24 * 60 * 60 * 1000 // last 7 days
            else -> now - 30L * 24 * 60 * 60 * 1000 // last 30 days
        }
        entries.filter { it.entry.timestamp >= limitTimestamp }
    }

    val totalSpent = filteredEntries.sumOf { it.entry.amount }

    // Aggregations
    val categorySummary = remember(filteredEntries) {
        filteredEntries
            .groupBy { it.category?.name ?: "Uncategorized" }
            .mapValues { (_, list) -> list.sumOf { it.entry.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val accountSummary = remember(filteredEntries) {
        filteredEntries
            .groupBy { it.accountTag?.name ?: "No Account" }
            .mapValues { (_, list) -> list.sumOf { it.entry.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val paymentAppSummary = remember(filteredEntries) {
        filteredEntries
            .groupBy { it.paymentApp?.name ?: "No App" }
            .mapValues { (_, list) -> list.sumOf { it.entry.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    LazyColumn(
         modifier = modifier.fillMaxSize(),
         contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
         verticalArrangement = Arrangement.spacedBy(16.dp)
     ) {
        // Period Select Row (Custom pill selection)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                periods.forEachIndexed { index, name ->
                    val isSelected = periodTab == index
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(containerColor)
                            .clickable { periodTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Total Spent Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Total Spent",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = String.format("₹%.2f", totalSpent),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Breakdown Selector (Custom pill selection)
        if (filteredEntries.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    breakdowns.forEachIndexed { index, name ->
                        val isSelected = breakdownTab == index
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(containerColor)
                                .clickable { breakdownTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Aggregation List
        if (filteredEntries.isEmpty()) {
            item {
                Text(
                    text = "No logs in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            item {
                when (breakdownTab) {
                    0 -> SummarySection(title = "By Category", data = categorySummary)
                    1 -> SummarySection(title = "By Account", data = accountSummary)
                    else -> SummarySection(title = "By Payment App", data = paymentAppSummary)
                }
            }
        }
    }
}

@Composable
fun SummarySection(
    title: String,
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        data.forEach { (label, amt) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format("₹%.2f", amt),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExpenseDialog(
    categories: List<com.triledge.dailyjournal.data.db.SpendingCategory>,
    accounts: List<com.triledge.dailyjournal.data.db.AccountTag>,
    paymentApps: List<com.triledge.dailyjournal.data.db.PaymentAppTag>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, categoryId: Long?, note: String?, timestamp: Long, accountId: Long?, paymentId: Long?) -> Unit
) {
    val context = LocalContext.current
    var amountStr by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }

    val calendar = remember { Calendar.getInstance() }
    var timestamp by remember { mutableStateOf(calendar.timeInMillis) }
    var showCustomDatePicker by remember { mutableStateOf(false) }

    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedPaymentId by remember { mutableStateOf<Long?>(null) }

    val dateTimeFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Prefill tags if defaults exist
    LaunchedEffect(categories, accounts, paymentApps) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            selectedCategoryId = categories.first().id
        }
        if (selectedAccountId == null && accounts.isNotEmpty()) {
            selectedAccountId = accounts.first().id
        }
        if (selectedPaymentId == null && paymentApps.isNotEmpty()) {
            selectedPaymentId = paymentApps.first().id
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
                        text = "Add Expense",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Optional Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Date Picker trigger
                item {
                    Column {
                        Text(
                            text = "Date & Time",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCustomDatePicker = true
                                }
                        ) {
                            Text(
                                text = dateTimeFormat.format(Date(timestamp)),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp)
                            )
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

                // Account Selection
                item {
                    Column {
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            accounts.forEach { acc ->
                                FilterChip(
                                    selected = selectedAccountId == acc.id,
                                    onClick = { selectedAccountId = acc.id },
                                    label = { Text(acc.name) }
                                )
                            }
                        }
                    }
                }

                // Payment App Selection
                item {
                    Column {
                        Text(
                            text = "Payment App",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            paymentApps.forEach { app ->
                                FilterChip(
                                    selected = selectedPaymentId == app.id,
                                    onClick = { selectedPaymentId = app.id },
                                    label = { Text(app.name) }
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
                                val amount = amountStr.toDoubleOrNull() ?: 0.0
                                if (amount > 0) {
                                    onConfirm(
                                        amount,
                                        selectedCategoryId,
                                        note.takeIf { it.isNotBlank() },
                                        timestamp,
                                        selectedAccountId,
                                        selectedPaymentId
                                    )
                                }
                            },
                            enabled = amountStr.toDoubleOrNull() != null && amountStr.toDouble() > 0,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    if (showCustomDatePicker) {
        com.triledge.dailyjournal.ui.components.CustomDatePickerDialog(
            initialDateMillis = timestamp,
            onDismiss = { showCustomDatePicker = false },
            onConfirm = { dateMillis ->
                calendar.timeInMillis = dateMillis
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        timestamp = calendar.timeInMillis
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
                showCustomDatePicker = false
            }
        )
    }
}
