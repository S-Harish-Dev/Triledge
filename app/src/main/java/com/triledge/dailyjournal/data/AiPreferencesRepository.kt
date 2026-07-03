package com.triledge.dailyjournal.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiPrefs(
    val provider: String,          // "NONE", "GEMINI", "OPENAI"
    val apiKey: String?,
    val model: String,
    val customEndpoint: String?,
    val geminiInputRate: Double,   // USD per million tokens
    val geminiOutputRate: Double,
    val openAiInputRate: Double,
    val openAiOutputRate: Double
) {
    companion object {
        val Default = AiPrefs(
            provider = "NONE",
            apiKey = null,
            model = "gemini-1.5-flash",
            customEndpoint = null,
            geminiInputRate = 0.075,
            geminiOutputRate = 0.30,
            openAiInputRate = 0.150,
            openAiOutputRate = 0.60
        )
    }
}

class AiPreferencesRepository(private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val prefs = EncryptedSharedPreferences.create(
        "encrypted_ai_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _aiPrefsState = MutableStateFlow(loadPrefs())
    val aiPrefsState: StateFlow<AiPrefs> = _aiPrefsState.asStateFlow()

    private fun loadPrefs(): AiPrefs {
        val provider = prefs.getString("ai_provider", "NONE") ?: "NONE"
        val apiKey = prefs.getString("ai_api_key", null)?.takeIf { it.isNotBlank() }
        val model = prefs.getString("ai_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"
        val endpoint = prefs.getString("ai_custom_endpoint", null)?.takeIf { it.isNotBlank() }
        
        val geminiInput = prefs.getFloat("gemini_in_rate", 0.075f).toDouble()
        val geminiOutput = prefs.getFloat("gemini_out_rate", 0.30f).toDouble()
        val openAiInput = prefs.getFloat("openai_in_rate", 0.150f).toDouble()
        val openAiOutput = prefs.getFloat("openai_out_rate", 0.60f).toDouble()
        
        return AiPrefs(provider, apiKey, model, endpoint, geminiInput, geminiOutput, openAiInput, openAiOutput)
    }

    private fun updateState() {
        _aiPrefsState.value = loadPrefs()
    }

    fun setProvider(provider: String) {
        prefs.edit().putString("ai_provider", provider).apply()
        updateState()
    }

    fun setApiKey(apiKey: String) {
        prefs.edit().putString("ai_api_key", apiKey.trim()).apply()
        updateState()
    }

    fun setModel(model: String) {
        prefs.edit().putString("ai_model", model.trim()).apply()
        updateState()
    }

    fun setCustomEndpoint(endpoint: String?) {
        if (endpoint.isNullOrBlank()) {
            prefs.edit().remove("ai_custom_endpoint").apply()
        } else {
            prefs.edit().putString("ai_custom_endpoint", endpoint.trim()).apply()
        }
        updateState()
    }

    fun setRates(geminiIn: Double, geminiOut: Double, openAiIn: Double, openAiOut: Double) {
        prefs.edit()
            .putFloat("gemini_in_rate", geminiIn.toFloat())
            .putFloat("gemini_out_rate", geminiOut.toFloat())
            .putFloat("openai_in_rate", openAiIn.toFloat())
            .putFloat("openai_out_rate", openAiOut.toFloat())
            .apply()
        updateState()
    }

    fun isVoiceDisclosureShown(): Boolean {
        return prefs.getBoolean("voice_disclosure_shown", false)
    }

    fun setVoiceDisclosureShown(shown: Boolean) {
        prefs.edit().putBoolean("voice_disclosure_shown", shown).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        updateState()
    }
}
