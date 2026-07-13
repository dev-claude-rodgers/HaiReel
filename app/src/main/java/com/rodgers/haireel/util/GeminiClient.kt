package com.rodgers.haireel.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    data class Message(val role: String, val text: String)

    suspend fun chat(
        apiKey: String,
        systemPrompt: String,
        messages: List<Message>
    ): String = withContext(Dispatchers.IO) {
        val conn = URL("$BASE_URL?key=$apiKey").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout   = 60_000

            val contents = JSONArray()
            messages.forEach { msg ->
                contents.put(JSONObject().apply {
                    put("role", msg.role)
                    put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                })
            }

            val body = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 1024)
                })
            }

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                throw Exception("Gemini API エラー($code): $err")
            }

            val resp = JSONObject(conn.inputStream.bufferedReader().readText())
            resp.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } finally {
            conn.disconnect()
        }
    }
}
