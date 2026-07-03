package com.triledge.dailyjournal.ui.tabs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triledge.dailyjournal.data.TradeCalculationResult
import com.triledge.dailyjournal.data.TradingCalculator
import com.triledge.dailyjournal.data.TradingRepository
import com.triledge.dailyjournal.data.db.BrokerPreset
import com.triledge.dailyjournal.data.db.ProductType
import com.triledge.dailyjournal.data.db.TradeItem
import com.triledge.dailyjournal.data.db.TradeItemWithPreset
import androidx.compose.material.icons.automirrored.filled.List
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TradingTab(
    repository: TradingRepository,
    addTrigger: Int = 0,
    onConsumeAddTrigger: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    // Filter states
    var filterSegment by remember { mutableStateOf("All Segments") }
    var filterProductType by remember { mutableStateOf("All Products") }
    var filterPreset by remember { mutableStateOf("All Presets") }
    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }
    var activeDatePickerField by remember { mutableStateOf<String?>(null) }

    // Dynamic Lists
    val trades by repository.allTrades.collectAsStateWithLifecycle(initialValue = emptyList())
    val presets by repository.allPresets.collectAsStateWithLifecycle(initialValue = emptyList())
    val productTypes by repository.allProductTypes.collectAsStateWithLifecycle(initialValue = emptyList())

    val df = remember { DecimalFormat("0.##") }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Filtered trades
    LaunchedEffect(addTrigger) {
        if (addTrigger > 0) {
            showAddDialog = true
            onConsumeAddTrigger()
        }
    }

    val filteredTrades = remember(trades, filterStartDate, filterEndDate, filterSegment, filterProductType, filterPreset) {
        trades.filter { item ->
            val matchesStart = filterStartDate == null || item.trade.entryTime >= filterStartDate!!
            val matchesEnd = filterEndDate == null || item.trade.exitTime <= (filterEndDate!! + 24L * 60 * 60 * 1000)
            val matchesSegment = filterSegment == "All Segments" || item.trade.segment.equals(filterSegment, ignoreCase = true)
            val matchesProduct = filterProductType == "All Products" || item.trade.productType.equals(filterProductType, ignoreCase = true)
            val matchesPreset = filterPreset == "All Presets" || item.preset?.name.equals(filterPreset, ignoreCase = true)

            matchesStart && matchesEnd && matchesSegment && matchesProduct && matchesPreset
        }
    }

    val stats = remember(filteredTrades) {
        var totalGross = 0.0
        var totalCharges = 0.0
        var totalNet = 0.0
        var wins = 0
        filteredTrades.forEach { item ->
            val calc = TradingCalculator.calculate(item.trade, item.preset)
            totalGross += calc.grossPnL
            totalCharges += calc.totalCharges
            totalNet += calc.netPnL
            if (calc.netPnL > 0) {
                wins++
            }
        }
        val winRate = if (filteredTrades.isNotEmpty()) (wins.toDouble() / filteredTrades.size) * 100.0 else 0.0
        
        object {
            val gross = totalGross
            val charges = totalCharges
            val net = totalNet
            val totalCount = filteredTrades.size
            val rate = winRate
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
            // Stats Dashboard
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Trading Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Net P&L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = (if (stats.net >= 0) "+" else "") + "₹ ${df.format(stats.net)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.net >= 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Win Rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${df.format(stats.rate)}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Gross P&L", style = MaterialTheme.typography.bodySmall)
                            Text("₹ ${df.format(stats.gross)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Charges", style = MaterialTheme.typography.bodySmall)
                            Text("₹ ${df.format(stats.charges)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Trades Count", style = MaterialTheme.typography.bodySmall)
                            Text("${stats.totalCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Collapsible / Scrollable Filters Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Filters", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            // Filter Chips Rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Date Range row
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeDatePickerField = "filterStart"
                            }
                    ) {
                        Text(
                            text = filterStartDate?.let { dateFormat.format(Date(it)) } ?: "Start Date",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeDatePickerField = "filterEnd"
                            }
                    ) {
                        Text(
                            text = filterEndDate?.let { dateFormat.format(Date(it)) } ?: "End Date",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    if (filterStartDate != null || filterEndDate != null) {
                        IconButton(
                            onClick = {
                                filterStartDate = null
                                filterEndDate = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Dates", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Dropdowns row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    var showSegmentMenu by remember { mutableStateOf(false) }
                    var showProductMenu by remember { mutableStateOf(false) }
                    var showPresetMenu by remember { mutableStateOf(false) }

                    // Segment Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        InputChip(
                            selected = filterSegment != "All Segments",
                            onClick = { showSegmentMenu = true },
                            label = { Text(filterSegment, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = showSegmentMenu, onDismissRequest = { showSegmentMenu = false }) {
                            DropdownMenuItem(text = { Text("All Segments") }, onClick = { filterSegment = "All Segments"; showSegmentMenu = false })
                            listOf("equity intraday", "equity delivery", "F&O", "currency", "commodity").forEach { seg ->
                                DropdownMenuItem(text = { Text(seg) }, onClick = { filterSegment = seg; showSegmentMenu = false })
                            }
                        }
                    }

                    // Product Type Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        InputChip(
                            selected = filterProductType != "All Products",
                            onClick = { showProductMenu = true },
                            label = { Text(filterProductType, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = showProductMenu, onDismissRequest = { showProductMenu = false }) {
                            DropdownMenuItem(text = { Text("All Products") }, onClick = { filterProductType = "All Products"; showProductMenu = false })
                            productTypes.forEach { pt ->
                                DropdownMenuItem(text = { Text(pt.name) }, onClick = { filterProductType = pt.name; showProductMenu = false })
                            }
                        }
                    }

                    // Broker Preset Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        InputChip(
                            selected = filterPreset != "All Presets",
                            onClick = { showPresetMenu = true },
                            label = { Text(filterPreset, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                            DropdownMenuItem(text = { Text("All Presets") }, onClick = { filterPreset = "All Presets"; showPresetMenu = false })
                            presets.forEach { pr ->
                                DropdownMenuItem(text = { Text(pr.name) }, onClick = { filterPreset = pr.name; showPresetMenu = false })
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Trades List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredTrades.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No trades found matching filters.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredTrades, key = { it.trade.id }) { tradeItem ->
                        TradeRow(
                            tradeWithPreset = tradeItem,
                            onDelete = {
                                coroutineScope.launch { repository.deleteTrade(tradeItem.trade) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTradeDialog(
            presets = presets,
            productTypes = productTypes,
            onDismiss = { showAddDialog = false },
            onConfirm = { trade ->
                coroutineScope.launch { repository.insertTrade(trade) }
                showAddDialog = false
            }
        )
    }

    when (activeDatePickerField) {
        "filterStart" -> {
            com.triledge.dailyjournal.ui.components.CustomDatePickerDialog(
                initialDateMillis = filterStartDate,
                onDismiss = { activeDatePickerField = null },
                onConfirm = { dateMillis ->
                    filterStartDate = dateMillis
                    activeDatePickerField = null
                }
            )
        }
        "filterEnd" -> {
            com.triledge.dailyjournal.ui.components.CustomDatePickerDialog(
                initialDateMillis = filterEndDate,
                onDismiss = { activeDatePickerField = null },
                onConfirm = { dateMillis ->
                    filterEndDate = dateMillis
                    activeDatePickerField = null
                }
            )
        }
    }
}

@Composable
fun TradeRow(
    tradeWithPreset: TradeItemWithPreset,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val df = remember { DecimalFormat("0.##") }
    val calc = remember(tradeWithPreset) {
        TradingCalculator.calculate(tradeWithPreset.trade, tradeWithPreset.preset)
    }
    val dtFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tradeWithPreset.trade.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tradeWithPreset.trade.segment, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(20.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Qty: ${tradeWithPreset.trade.quantity} | Buy: ₹ ${tradeWithPreset.trade.buyPrice} | Sell: ₹ ${tradeWithPreset.trade.sellPrice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (calc.netPnL >= 0) "+" else "") + "₹ ${df.format(calc.netPnL)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (calc.netPnL >= 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Gross: ₹ ${df.format(calc.grossPnL)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider()
                    
                    Text(
                        text = "Entry: ${dtFormat.format(Date(tradeWithPreset.trade.entryTime))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Exit: ${dtFormat.format(Date(tradeWithPreset.trade.exitTime))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Preset used: ${tradeWithPreset.preset?.name ?: "None"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    Text("Charges Breakdown:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Brokerage: ₹ ${df.format(calc.brokerage)}", style = MaterialTheme.typography.bodySmall)
                            Text("STT: ₹ ${df.format(calc.stt)}", style = MaterialTheme.typography.bodySmall)
                            Text("Exch. Tx: ₹ ${df.format(calc.exchangeTx)}", style = MaterialTheme.typography.bodySmall)
                            Text("SEBI Charge: ₹ ${df.format(calc.sebiTurnover)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Stamp Duty: ₹ ${df.format(calc.stampDuty)}", style = MaterialTheme.typography.bodySmall)
                            Text("GST: ₹ ${df.format(calc.gst)}", style = MaterialTheme.typography.bodySmall)
                            Text("DP Charges: ₹ ${df.format(calc.dpCharges)}", style = MaterialTheme.typography.bodySmall)
                            Text("Total: ₹ ${df.format(calc.totalCharges)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Trade", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTradeDialog(
    presets: List<BrokerPreset>,
    productTypes: List<ProductType>,
    onDismiss: () -> Unit,
    onConfirm: (TradeItem) -> Unit
) {
    val context = LocalContext.current
    var symbol by remember { mutableStateOf("") }
    var buyPrice by remember { mutableStateOf("") }
    var sellPrice by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }

    var selectedSegment by remember { mutableStateOf("equity intraday") }
    var selectedProductType by remember { mutableStateOf("") }
    var selectedPresetId by remember { mutableStateOf<Long?>(null) }

    // Date/Time fields
    val entryCal = remember { Calendar.getInstance() }
    val exitCal = remember { Calendar.getInstance() }
    
    var entryTime by remember { mutableStateOf(entryCal.timeInMillis) }
    var exitTime by remember { mutableStateOf(exitCal.timeInMillis) }
    var activeDatePickerField by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Auto-select first preset and product type if available
    LaunchedEffect(presets, productTypes) {
        if (selectedPresetId == null && presets.isNotEmpty()) {
            selectedPresetId = presets.first().id
        }
        if (selectedProductType.isEmpty() && productTypes.isNotEmpty()) {
            selectedProductType = productTypes.first().name
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
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
                        text = "Add Trade Record",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it },
                        label = { Text("Instrument / Symbol (e.g. INFY)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = buyPrice,
                            onValueChange = { buyPrice = it },
                            label = { Text("Buy Price") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sellPrice,
                            onValueChange = { sellPrice = it },
                            label = { Text("Sell Price") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Date Time Picker Buttons
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Entry Date & Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeDatePickerField = "entryTime"
                                }
                        ) {
                            Text(
                                text = dateFormat.format(Date(entryTime)),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text("Exit Date & Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeDatePickerField = "exitTime"
                                }
                        ) {
                            Text(
                                text = dateFormat.format(Date(exitTime)),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Segment Selection
                item {
                    Column {
                        Text("Segment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("equity intraday", "equity delivery", "F&O", "currency", "commodity").forEach { seg ->
                                FilterChip(
                                    selected = selectedSegment == seg,
                                    onClick = { selectedSegment = seg },
                                    label = { Text(seg) }
                                )
                            }
                        }
                    }
                }

                // Product Type Selection
                item {
                    Column {
                        Text("Product Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            productTypes.forEach { pt ->
                                FilterChip(
                                    selected = selectedProductType == pt.name,
                                    onClick = { selectedProductType = pt.name },
                                    label = { Text(pt.name) }
                                )
                            }
                        }
                    }
                }

                // Broker Preset Selection
                item {
                    Column {
                        Text("Broker preset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = selectedPresetId == preset.id,
                                    onClick = { selectedPresetId = preset.id },
                                    label = { Text(preset.name) }
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
                                val bPrice = buyPrice.toDoubleOrNull() ?: 0.0
                                val sPrice = sellPrice.toDoubleOrNull() ?: 0.0
                                val qty = quantity.toIntOrNull() ?: 0
                                if (symbol.isNotBlank() && bPrice > 0 && sPrice > 0 && qty > 0) {
                                    onConfirm(
                                        TradeItem(
                                            symbol = symbol,
                                            buyPrice = bPrice,
                                            sellPrice = sPrice,
                                            quantity = qty,
                                            segment = selectedSegment,
                                            productType = selectedProductType,
                                            presetId = selectedPresetId,
                                            entryTime = entryTime,
                                            exitTime = exitTime
                                        )
                                    )
                                }
                            },
                            enabled = symbol.isNotBlank() && buyPrice.toDoubleOrNull() != null && sellPrice.toDoubleOrNull() != null && quantity.toIntOrNull() != null,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    when (activeDatePickerField) {
        "entryTime" -> {
            com.triledge.dailyjournal.ui.components.CustomDatePickerDialog(
                initialDateMillis = entryTime,
                onDismiss = { activeDatePickerField = null },
                onConfirm = { dateMillis ->
                    val cal = Calendar.getInstance().apply { timeInMillis = entryTime }
                    val dateCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    cal.set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                    cal.set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                    cal.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                    
                    android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hour)
                            cal.set(Calendar.MINUTE, minute)
                            entryTime = cal.timeInMillis
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    ).show()
                    activeDatePickerField = null
                }
            )
        }
        "exitTime" -> {
            com.triledge.dailyjournal.ui.components.CustomDatePickerDialog(
                initialDateMillis = exitTime,
                onDismiss = { activeDatePickerField = null },
                onConfirm = { dateMillis ->
                    val cal = Calendar.getInstance().apply { timeInMillis = exitTime }
                    val dateCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    cal.set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                    cal.set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                    cal.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                    
                    android.app.TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hour)
                            cal.set(Calendar.MINUTE, minute)
                            exitTime = cal.timeInMillis
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    ).show()
                    activeDatePickerField = null
                }
            )
        }
    }
}
