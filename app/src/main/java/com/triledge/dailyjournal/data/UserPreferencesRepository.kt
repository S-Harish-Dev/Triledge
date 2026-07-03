package com.triledge.dailyjournal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.triledge.dailyjournal.ui.theme.AppearanceMode
import com.triledge.dailyjournal.ui.theme.BrandColor
import com.triledge.dailyjournal.ui.theme.BrandPalette
import com.triledge.dailyjournal.ui.theme.ShapeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "triledge_prefs")

/**
 * Single source of truth for user-level preferences. Held in DataStore Preferences
 * (lean — a single string + a few enum ids don't earn a Room database).
 */
data class UserPrefs(
    val name: String?,
    val appearanceMode: AppearanceMode,
    val seedColor: BrandColor,
    val shapeStyle: ShapeStyle,
    val kiteEnabled: Boolean,
    val kiteApiKey: String?,
    val kiteAccessToken: String?
) {
    companion object {
        val Default = UserPrefs(
            name = null,
            appearanceMode = AppearanceMode.Dark,
            seedColor = BrandPalette.Default,
            shapeStyle = ShapeStyle.Rounded,
            kiteEnabled = false,
            kiteApiKey = null,
            kiteAccessToken = null
        )
    }
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val NAME = stringPreferencesKey("user_name")
        val APPEARANCE = stringPreferencesKey("appearance_mode")
        val SEED = stringPreferencesKey("seed_color_id")
        val SHAPE = stringPreferencesKey("shape_style")
        val KITE_ENABLED = stringPreferencesKey("kite_enabled")
        val KITE_API_KEY = stringPreferencesKey("kite_api_key")
        val KITE_ACCESS_TOKEN = stringPreferencesKey("kite_access_token")
    }

    val userPrefs: Flow<UserPrefs> = context.dataStore.data.map { prefs ->
        UserPrefs(
            name = prefs[Keys.NAME]?.takeIf { it.isNotBlank() },
            appearanceMode = prefs[Keys.APPEARANCE]
                ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
                ?: AppearanceMode.System,
            seedColor = prefs[Keys.SEED]
                ?.let { BrandPalette.byId(it) }
                ?: BrandPalette.Default,
            shapeStyle = prefs[Keys.SHAPE]
                ?.let { runCatching { ShapeStyle.valueOf(it) }.getOrNull() }
                ?: ShapeStyle.Rounded,
            kiteEnabled = prefs[Keys.KITE_ENABLED]?.toBoolean() ?: false,
            kiteApiKey = prefs[Keys.KITE_API_KEY],
            kiteAccessToken = prefs[Keys.KITE_ACCESS_TOKEN]
        )
    }

    suspend fun setName(name: String) {
        context.dataStore.edit { it[Keys.NAME] = name.trim() }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        context.dataStore.edit { it[Keys.APPEARANCE] = mode.name }
    }

    suspend fun setSeedColor(color: BrandColor) {
        context.dataStore.edit { it[Keys.SEED] = color.id }
    }

    suspend fun setShapeStyle(style: ShapeStyle) {
        context.dataStore.edit { it[Keys.SHAPE] = style.name }
    }

    suspend fun setKiteEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KITE_ENABLED] = enabled.toString() }
    }

    suspend fun setKiteApiKey(key: String) {
        context.dataStore.edit { it[Keys.KITE_API_KEY] = key.trim() }
    }

    suspend fun setKiteAccessToken(token: String) {
        context.dataStore.edit { it[Keys.KITE_ACCESS_TOKEN] = token.trim() }
    }
}