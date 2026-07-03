package com.triledge.dailyjournal.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triledge.dailyjournal.data.QuotesRepository
import com.triledge.dailyjournal.data.SpendingRepository
import com.triledge.dailyjournal.data.TodoRepository
import com.triledge.dailyjournal.data.TradingCalculator
import com.triledge.dailyjournal.data.TradingRepository
import com.triledge.dailyjournal.data.db.TriledgeDatabase
import com.triledge.dailyjournal.data.db.UsageDate
import com.triledge.dailyjournal.ui.greeting.GreetingStrip
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    userName: String,
    todoRepository: TodoRepository,
    spendingRepository: SpendingRepository,
    tradingRepository: TradingRepository,
    quotesRepository: QuotesRepository,
    database: TriledgeDatabase,
    onNavigateToTab: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todoItems      by todoRepository.allItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val spendingEntries by spendingRepository.allEntries.collectAsStateWithLifecycle(initialValue = emptyList())
    val trades         by tradingRepository.allTrades.collectAsStateWithLifecycle(initialValue = emptyList())
    val usageDates     by database.usageDateDao().getAllDatesFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    LaunchedEffect(todayStr) {
        database.usageDateDao().insertDate(UsageDate(todayStr))
    }

    val quote = remember { quotesRepository.getRandomQuote() }

    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val endOfToday   = startOfToday + 24L * 60 * 60 * 1000
    val startOfWeek  = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // ── Stats ──────────────────────────────────────────────────────────────
    val urgentItems    = todoItems.filter { !it.item.isCompleted && it.item.isPriority }
    val todayItems     = todoItems.filter { !it.item.isCompleted && it.item.dueDate != null && it.item.dueDate >= startOfToday && it.item.dueDate < endOfToday }
    val previewItems   = (urgentItems + todayItems).distinctBy { it.item.id }.take(4)
    val totalActive    = todoItems.count { !it.item.isCompleted }
    val completedToday = todoItems.count { it.item.isCompleted && it.item.createdAt >= startOfToday }
    val totalToday     = todoItems.count { it.item.createdAt >= startOfToday || (it.item.dueDate != null && it.item.dueDate >= startOfToday && it.item.dueDate < endOfToday) }
    val completionRate = if (totalToday > 0) completedToday.toFloat() / totalToday else 0f

    val todaySpend = spendingEntries.filter { it.entry.timestamp >= startOfToday }.sumOf { it.entry.amount }
    val weekSpend  = spendingEntries.filter { it.entry.timestamp >= startOfWeek  }.sumOf { it.entry.amount }

    val last7DaysSpend = remember(spendingEntries, startOfToday) {
        (0..6).map { daysAgo ->
            val dayStart = startOfToday - daysAgo * 24L * 60 * 60 * 1000
            val dayEnd   = dayStart + 24L * 60 * 60 * 1000
            spendingEntries.filter { it.entry.timestamp in dayStart until dayEnd }.sumOf { it.entry.amount }
        }.reversed()
    }

    val weekNetPnl = trades.filter { it.trade.exitTime >= startOfWeek }.sumOf { trade ->
        val calc = trade.preset?.let { TradingCalculator.calculate(trade.trade, it) }
        calc?.netPnL ?: ((trade.trade.sellPrice - trade.trade.buyPrice) * trade.trade.quantity)
    }

    val streak    = remember(usageDates) { calculateStreak(usageDates.map { it.dateString }) }
    val totalDays = usageDates.size
    val currencyFormat = remember { DecimalFormat("₹#,##0") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Greeting strip — only visible on Home ─────────────────────────
        GreetingStrip(
            name = userName,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // ── Editorial Quote Card — Glass ───────────────────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            cornerRadiusDp = 28.dp,
            glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "\u201C",
                    fontSize = 96.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-10).dp, y = (-44).dp)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = quote ?: "Add custom quotes in Settings → Quotes to see daily inspiration here.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Daily Inspiration",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // ── Stat Cards Row ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassStatCard(
                title = "Spent Today",
                value = currencyFormat.format(todaySpend),
                icon = Icons.Default.ShoppingCart,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(2) }
            )

            val pnlPositive = weekNetPnl >= 0
            val pnlAccent   = if (pnlPositive) Color(0xFF34D399) else MaterialTheme.colorScheme.error
            GlassStatCard(
                title = "Week P&L",
                value = (if (pnlPositive) "+" else "") + currencyFormat.format(weekNetPnl),
                icon = if (pnlPositive) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                accentColor = pnlAccent,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(3) }
            )
        }

        // ── Tasks Ring + 7-Day Spend Graph ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Custom arc progress ring
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToTab(1) },
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Tasks Done",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val animatedProgress by animateFloatAsState(
                        targetValue = completionRate,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "arc_progress"
                    )
                    val primary = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(86.dp)) {
                        Canvas(modifier = Modifier.size(86.dp)) {
                            val strokeW = 8.dp.toPx()
                            val inset   = strokeW / 2
                            val arcRect = Size(size.width - inset * 2, size.height - inset * 2)

                            // Track
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcRect,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                            // Progress arc with glow
                            if (animatedProgress > 0f) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            primary.copy(alpha = 0.6f),
                                            primary,
                                            primary
                                        )
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcRect,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$completedToday/$totalToday",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(completionRate * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Animated 7-day Bezier spend graph
            GlassCard(
                modifier = Modifier
                    .weight(1.3f)
                    .clickable { onNavigateToTab(2) },
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "7-Day Spend",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    val graphAlpha by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 800, delayMillis = 300),
                        label = "graph_appear"
                    )
                    val sparkColor = MaterialTheme.colorScheme.primary

                    Canvas(modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .graphicsLayer(alpha = graphAlpha)
                    ) {
                        val maxVal = last7DaysSpend.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
                        val path     = Path()
                        val fillPath = Path()
                        val points   = last7DaysSpend.mapIndexed { idx, value ->
                            val x = idx * (size.width / 6f)
                            val y = size.height - (value.toFloat() / maxVal * size.height * 0.82f) - 2f
                            Offset(x, y)
                        }
                        if (points.isNotEmpty()) {
                            path.moveTo(points[0].x, points[0].y)
                            fillPath.moveTo(points[0].x, size.height)
                            fillPath.lineTo(points[0].x, points[0].y)

                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]; val p1 = points[i + 1]
                                val cp1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                                val cp2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
                                path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p1.x, p1.y)
                                fillPath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p1.x, p1.y)
                            }

                            fillPath.lineTo(points.last().x, size.height)
                            fillPath.close()

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        sparkColor.copy(alpha = 0.30f),
                                        sparkColor.copy(alpha = 0.04f)
                                    ),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )
                            drawPath(
                                path = path,
                                color = sparkColor,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // End-point glow dot
                            points.last().let { pt ->
                                drawCircle(
                                    color = sparkColor.copy(alpha = 0.3f),
                                    radius = 8.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = sparkColor,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Week: ${currencyFormat.format(weekSpend)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Upcoming Tasks Timeline Card ───────────────────────────────────
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToTab(1) },
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Upcoming Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "$totalActive active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (previewItems.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🎉", fontSize = 28.sp)
                        Text(
                            "All tasks done!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Enjoy your day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    previewItems.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Timeline connector
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(24.dp)
                            ) {
                                val dotColor = if (item.item.isPriority)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary

                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .drawBehind {
                                            drawCircle(
                                                color = dotColor.copy(alpha = 0.28f),
                                                radius = size.width * 0.9f
                                            )
                                        }
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                if (idx < previewItems.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(20.dp)
                                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = item.item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.item.isPriority) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "URGENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (item.item.dueDate != null) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.item.dueDate)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Habit Streak + Heatmap Card ────────────────────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            cornerRadiusDp = 24.dp,
            glowColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Habit Journey",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (streak >= 2) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "🔥 On fire",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StreakStat(
                        value = "$streak",
                        label = if (streak == 1) "day streak" else "day streak",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                    StreakStat(
                        value = "$totalDays",
                        label = "total days",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Heatmap — last 28 days with glow on today
                val dateSet = remember(usageDates) { usageDates.map { it.dateString }.toSet() }
                val last28 = remember {
                    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val cal = Calendar.getInstance()
                    (0..27).map { daysAgo ->
                        cal.timeInMillis = System.currentTimeMillis() - daysAgo * 24L * 60 * 60 * 1000
                        fmt.format(cal.time)
                    }.reversed()
                }

                val activeColor   = MaterialTheme.colorScheme.primary
                val inactiveColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                val todayFmt      = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    last28.forEach { dateStr ->
                        val isActive  = dateStr in dateSet
                        val isToday   = dateStr == todayFmt
                        val cellColor = when {
                            isActive  -> activeColor
                            else      -> inactiveColor
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .then(
                                    if (isToday && isActive) Modifier.drawBehind {
                                        drawRoundRect(
                                            color = activeColor.copy(alpha = 0.4f),
                                            cornerRadius = CornerRadius(6.dp.toPx()),
                                            size = Size(size.width * 1.6f, size.height * 1.6f),
                                            topLeft = Offset(-size.width * 0.3f, -size.height * 0.3f)
                                        )
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(4.dp))
                                .background(cellColor)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "28 days ago",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Bottom padding for nav dock
        Spacer(Modifier.height(96.dp))
    }
}

// ── Glass Card ─────────────────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    cornerRadiusDp: Dp = 20.dp,
    glowColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }

    Box(
        modifier = modifier
            .then(
                if (glowColor != Color.Transparent) Modifier.drawBehind {
                    drawRoundRect(
                        color = glowColor,
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        size = Size(size.width * 1.05f, size.height * 1.1f),
                        topLeft = Offset(-size.width * 0.025f, -size.height * 0.05f)
                    )
                } else Modifier
            )
            .clip(shape)
            .background(
                color = surface.copy(alpha = 0.60f)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.03f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

// ── Glass Stat Card ────────────────────────────────────────────────────────

@Composable
private fun GlassStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        cornerRadiusDp = 24.dp,
        glowColor = accentColor.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Streak Stat Cell ──────────────────────────────────────────────────────

@Composable
private fun StreakStat(value: String, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Streak calculator ─────────────────────────────────────────────────────

private fun calculateStreak(dateStrings: List<String>): Int {
    if (dateStrings.isEmpty()) return 0
    val fmt     = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayStr = fmt.format(Date())
    val sorted  = dateStrings.toSortedSet().toList().reversed()

    if (sorted.isEmpty()) return 0

    val cal = Calendar.getInstance()
    var checkDate = todayStr

    if (sorted.first() != todayStr) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
        checkDate = fmt.format(cal.time)
        if (!sorted.contains(checkDate)) return 0
    }

    cal.time = fmt.parse(sorted.first())!!
    var streak = 1
    for (i in 1 until sorted.size) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val expected = fmt.format(cal.time)
        if (sorted[i] == expected) streak++ else break
    }
    return streak
}
