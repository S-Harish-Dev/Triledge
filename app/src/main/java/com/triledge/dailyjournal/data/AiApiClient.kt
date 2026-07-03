package com.triledge.dailyjournal.data

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AiResponse(
    val text: String,
    val promptTokens: Int,
    val completionTokens: Int
)

object AiApiClient {

    suspend fun getAiResponse(
        provider: String,
        apiKey: String,
        model: String,
        customEndpoint: String?,
        prompt: String,
        history: List<com.triledge.dailyjournal.data.db.ChatMessage>
    ): AiResponse = withContext(Dispatchers.IO) {
        val cleanUrl = if (provider.equals("GEMINI", ignoreCase = true)) {
            val endpoint = customEndpoint?.takeIf { it.isNotBlank() } ?: "https://generativelanguage.googleapis.com"
            val queryParam = if (endpoint.contains("?")) "&key=$apiKey" else "?key=$apiKey"
            "$endpoint/v1beta/models/$model:generateContent$queryParam"
        } else {
            val endpoint = customEndpoint?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1"
            "$endpoint/chat/completions"
        }

        val url = URL(cleanUrl)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (provider.equals("OPENAI", ignoreCase = true)) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val requestBody = if (provider.equals("GEMINI", ignoreCase = true)) {
                buildGeminiPayload(prompt, history)
            } else {
                buildOpenAiPayload(model, prompt, history)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                parseResponse(provider, response)
            } else {
                val errorStream = conn.errorStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val msg = runCatching {
                    JSONObject(errorMsg).getJSONObject("error").getString("message")
                }.getOrDefault(errorMsg.take(200))
                throw Exception("Error (code $responseCode): $msg")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun buildGeminiPayload(prompt: String, history: List<com.triledge.dailyjournal.data.db.ChatMessage>): String {
        val contents = JSONArray()
        
        // Add history messages
        history.forEach { msg ->
            val contentObj = JSONObject()
            val role = if (msg.role == "user") "user" else "model"
            contentObj.put("role", role)
            
            val parts = JSONArray()
            val textObj = JSONObject()
            textObj.put("text", msg.content)
            parts.put(textObj)
            contentObj.put("parts", parts)
            
            contents.put(contentObj)
        }

        // Add current user prompt
        val currentContent = JSONObject()
        currentContent.put("role", "user")
        val currentParts = JSONArray()
        val currentText = JSONObject()
        currentText.put("text", prompt)
        currentParts.put(currentText)
        currentContent.put("parts", currentParts)
        contents.put(currentContent)

        val root = JSONObject()
        root.put("contents", contents)
        return root.toString()
    }

    private fun buildOpenAiPayload(model: String, prompt: String, history: List<com.triledge.dailyjournal.data.db.ChatMessage>): String {
        val messages = JSONArray()
        
        // Add history messages
        history.forEach { msg ->
            val msgObj = JSONObject()
            val role = if (msg.role == "user") "user" else "assistant"
            msgObj.put("role", role)
            msgObj.put("content", msg.content)
            messages.put(msgObj)
        }

        // Add current user prompt
        val currentMsg = JSONObject()
        currentMsg.put("role", "user")
        currentMsg.put("content", prompt)
        messages.put(currentMsg)

        val root = JSONObject()
        root.put("model", model)
        root.put("messages", messages)
        return root.toString()
    }

    private fun parseResponse(provider: String, jsonStr: String): AiResponse {
        val root = JSONObject(jsonStr)
        val text = if (provider.equals("GEMINI", ignoreCase = true)) {
            root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } else {
            root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }

        var promptTokens = 0
        var completionTokens = 0

        if (provider.equals("GEMINI", ignoreCase = true)) {
            val usage = root.optJSONObject("usageMetadata")
            if (usage != null) {
                promptTokens = usage.optInt("promptTokenCount", 0)
                completionTokens = usage.optInt("candidatesTokenCount", 0)
            }
        } else {
            val usage = root.optJSONObject("usage")
            if (usage != null) {
                promptTokens = usage.optInt("prompt_tokens", 0)
                completionTokens = usage.optInt("completion_tokens", 0)
            }
        }

        return AiResponse(text, promptTokens, completionTokens)
    }
}
