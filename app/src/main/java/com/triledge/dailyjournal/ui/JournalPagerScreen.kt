package com.triledge.dailyjournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.triledge.dailyjournal.data.SpendingRepository
import com.triledge.dailyjournal.data.QuotesRepository
import com.triledge.dailyjournal.ui.settings.SettingsScreen
import com.triledge.dailyjournal.ui.tabs.SpendingTab
import com.triledge.dailyjournal.ui.tabs.TodoTab
import com.triledge.dailyjournal.ui.tabs.TradingTab
import kotlinx.coroutines.launch

import com.triledge.dailyjournal.data.TodoRepository
import com.triledge.dailyjournal.data.TradingRepository
import com.triledge.dailyjournal.data.UserPreferencesRepository
import com.triledge.dailyjournal.data.ChatRepository
import com.triledge.dailyjournal.data.AiPreferencesRepository
import com.triledge.dailyjournal.data.db.TriledgeDatabase
import com.triledge.dailyjournal.ui.tabs.ChatTab

private data class TabSpec(val title: String, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec("Home",     Icons.Default.Home),
    TabSpec("Tasks",    Icons.Default.CheckCircle),
    TabSpec("Spending", Icons.Default.ShoppingCart),
    TabSpec("Trading",  Icons.Default.Favorite),
    TabSpec("AI Chat",  Icons.Default.Email)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalPagerScreen(
    userName: String,
    spendingRepository: SpendingRepository,
    todoRepository: TodoRepository,
    tradingRepository: TradingRepository,
    chatRepository: ChatRepository,
    userPreferencesRepository: UserPreferencesRepository,
    aiPreferencesRepository: AiPreferencesRepository,
    quotesRepository: QuotesRepository,
    database: TriledgeDatabase
) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            spendingRepository = spendingRepository,
            todoRepository = todoRepository,
            tradingRepository = tradingRepository,
            chatRepository = chatRepository,
            userPreferencesRepository = userPreferencesRepository,
            aiPreferencesRepository = aiPreferencesRepository,
            quotesRepository = quotesRepository,
            onBack = { showSettings = false }
        )
    } else {
        val pagerState = rememberPagerState(pageCount = { Tabs.size })
        val scope = rememberCoroutineScope()

        var todoAddTrigger     by remember { mutableStateOf(0) }
        var spendingAddTrigger by remember { mutableStateOf(0) }
        var tradingAddTrigger  by remember { mutableStateOf(0) }

        val primaryColor   = MaterialTheme.colorScheme.primary
        val tertiaryColor  = MaterialTheme.colorScheme.tertiary

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            userName = userName,
                            todoRepository = todoRepository,
                            spendingRepository = spendingRepository,
                            tradingRepository = tradingRepository,
                            quotesRepository = quotesRepository,
                            database = database,
                            onNavigateToTab = { tabIndex ->
                                scope.launch { pagerState.animateScrollToPage(tabIndex) }
                            },
                            onSettingsClick = { showSettings = true }
                        )
                        1 -> TodoTab(
                            repository = todoRepository,
                            aiPreferencesRepository = aiPreferencesRepository,
                            chatRepository = chatRepository,
                            addTrigger = todoAddTrigger,
                            onConsumeAddTrigger = { todoAddTrigger = 0 }
                        )
                        2 -> SpendingTab(
                            repository = spendingRepository,
                            addTrigger = spendingAddTrigger,
                            onConsumeAddTrigger = { spendingAddTrigger = 0 }
                        )
                        3 -> TradingTab(
                            repository = tradingRepository,
                            addTrigger = tradingAddTrigger,
                            onConsumeAddTrigger = { tradingAddTrigger = 0 }
                        )
                        4 -> ChatTab(
                            chatRepository = chatRepository,
                            aiPreferencesRepository = aiPreferencesRepository
                        )
                    }
                }
            }

            // ── Glassmorphism Floating Bottom Dock ──────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ── Glass Nav Pill ────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                color = Color(0xFF0F1320).copy(alpha = 0.88f)
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                ),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Tabs.forEachIndexed { index, tab ->
                            val isSelected = pagerState.currentPage == index

                            val itemBg = if (isSelected) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.85f),
                                        tertiaryColor.copy(alpha = 0.75f)
                                    )
                                )
                            } else null

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .then(
                                        if (itemBg != null) Modifier.background(itemBg)
                                        else Modifier
                                    )
                                    .clickable {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                    .padding(
                                        horizontal = if (isSelected) 16.dp else 12.dp,
                                        vertical = 10.dp
                                    )
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) Color.White
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                                    exit = fadeOut() + scaleOut(targetScale = 0.85f)
                                ) {
                                    Row {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Gradient Glow FAB ────────────────────────────────────
                    AnimatedVisibility(
                        visible = pagerState.currentPage in 1..3,
                        enter = fadeIn() + scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .drawBehind {
                                    // Glow halo behind FAB
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                tertiaryColor.copy(alpha = 0.5f),
                                                Color.Transparent
                                            ),
                                            radius = size.width * 0.9f,
                                            center = Offset(size.width / 2, size.height / 2)
                                        ),
                                        radius = size.width * 0.85f
                                    )
                                }
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(primaryColor, tertiaryColor)
                                    )
                                )
                                .clickable {
                                    when (pagerState.currentPage) {
                                        1 -> todoAddTrigger++
                                        2 -> spendingAddTrigger++
                                        3 -> tradingAddTrigger++
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Item",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}