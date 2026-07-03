package com.triledge.dailyjournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triledge.dailyjournal.data.SpendingRepository
import com.triledge.dailyjournal.data.TodoRepository
import com.triledge.dailyjournal.data.TradingRepository
import com.triledge.dailyjournal.data.UserPreferencesRepository
import com.triledge.dailyjournal.data.UserPrefs
import com.triledge.dailyjournal.data.AiPreferencesRepository
import com.triledge.dailyjournal.data.ChatRepository
import com.triledge.dailyjournal.data.QuotesRepository
import com.triledge.dailyjournal.data.db.TriledgeDatabase
import com.triledge.dailyjournal.ui.JournalPagerScreen
import com.triledge.dailyjournal.ui.onboarding.OnboardingScreen
import com.triledge.dailyjournal.ui.theme.AppearanceMode
import com.triledge.dailyjournal.ui.theme.TriledgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppContent()
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val repo = remember(context) {
        UserPreferencesRepository(context.applicationContext)
    }
    val aiPrefsRepo = remember(context) {
        AiPreferencesRepository(context.applicationContext)
    }
    val quotesRepo = remember(context) {
        QuotesRepository(context.applicationContext)
    }

    // ── CRITICAL FIX: use null as sentinel so we render NOTHING until DataStore
    // has emitted real persisted prefs. This eliminates the ~1.5s onboarding
    // flash seen on every launch after the first setup.
    val prefs: UserPrefs? by repo.userPrefs.collectAsStateWithLifecycle(initialValue = null)

    val database = remember(context) {
        TriledgeDatabase.getDatabase(context.applicationContext)
    }
    val spendingRepo = remember(database) {
        SpendingRepository(database)
    }
    val todoRepo = remember(database, context) {
        TodoRepository(database, context.applicationContext)
    }
    val tradingRepo = remember(database) {
        TradingRepository(database)
    }
    val chatRepo = remember(database) {
        ChatRepository(database)
    }

    LaunchedEffect(spendingRepo, todoRepo, tradingRepo) {
        spendingRepo.ensureSeededData()
        todoRepo.ensureSeededData()
        tradingRepo.ensureSeededData()
    }

    // While prefs is null DataStore hasn't emitted yet — render transparent
    // surface in dark mode so there's no white flash either.
    val resolvedPrefs = prefs
    TriledgeTheme(
        appearanceMode = resolvedPrefs?.appearanceMode ?: AppearanceMode.Dark,
        seedColor = resolvedPrefs?.seedColor ?: com.triledge.dailyjournal.ui.theme.BrandPalette.Default,
        shapeStyle = resolvedPrefs?.shapeStyle ?: com.triledge.dailyjournal.ui.theme.ShapeStyle.Rounded
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Don't render anything until we have real prefs from DataStore
            if (resolvedPrefs != null) {
                val name = resolvedPrefs.name?.takeIf { it.isNotBlank() }
                if (name == null) {
                    OnboardingScreen(onSubmit = { repo.setName(it) })
                } else {
                    JournalPagerScreen(
                        userName = name,
                        spendingRepository = spendingRepo,
                        todoRepository = todoRepo,
                        tradingRepository = tradingRepo,
                        chatRepository = chatRepo,
                        userPreferencesRepository = repo,
                        aiPreferencesRepository = aiPrefsRepo,
                        quotesRepository = quotesRepo,
                        database = database
                    )
                }
            }
        }
    }
}