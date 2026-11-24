package uz.nizom.app

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Claude (Anthropic Messages API) bilan oddiy HTTP aloqa. Qo'shimcha kutubxona kerak emas.
 * Natija asosiy oqimda (main thread) qaytariladi: onDone(javob, xato).
 */
object AiClient {

    private val main = Handler(Looper.getMainLooper())

    fun chat(
        apiKey: String,
        model: String,
        system: String,
        history: List<ChatMsg>,
        onDone: (String?, String?) -> Unit
    ) {
        Thread {
            var result: String? = null
            var error: String? = null
            try {
                val messages = JSONArray()
                for (m in history) {
                    messages.put(JSONObject().put("role", m.role).put("content", m.content))
                }
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 1024)
                    .put("system", system)
                    .put("messages", messages)

                val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 20_000
                conn.readTimeout = 90_000
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val content = JSONObject(text).getJSONArray("content")
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") sb.append(block.optString("text"))
                    }
                    val answer = sb.toString().trim()
                    if (answer.isEmpty()) error = "Bo'sh javob keldi." else result = answer
                } else {
                    val msg = try {
                        JSONObject(text).getJSONObject("error").optString("message")
                    } catch (e: Exception) {
                        text.take(200)
                    }
                    error = when (code) {
                        401 -> "API kalit noto'g'ri (401). Sozlamalarda tekshiring."
                        404 -> "Model topilmadi (404). Sozlamalarda model nomini tekshiring. $msg"
                        429 -> "So'rovlar ko'p yoki limit tugadi (429). Birozdan keyin urinib ko'ring."
                        else -> "Xato $code: $msg"
                    }
                }
            } catch (e: Exception) {
                error = "Ulanishda xato: ${e.message}. Internetni tekshiring."
            }
            val finalResult = result
            val finalError = error
            main.post { onDone(finalResult, finalError) }
        }.start()
    }
}
