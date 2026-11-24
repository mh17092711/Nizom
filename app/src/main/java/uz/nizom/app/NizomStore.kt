package uz.nizom.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TimeBlock(
    val id: Long,
    val title: String,
    val category: String,
    val startMinutes: Int,
    val durationMinutes: Int
)

data class ExpenseItem(
    val id: Long,
    val amount: Long,
    val note: String
)

data class FoodItem(
    val id: Long,
    val name: String,
    val timeText: String
)

data class ExamTaskItem(
    val id: Long,
    val title: String,
    val dueText: String
)

data class ChatMsg(
    val role: String,
    val content: String
)

class NizomStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("nizom_prefs_v1", Context.MODE_PRIVATE)

    // ---------------- Sozlamalar / profil ----------------

    var budget: Long
        get() = prefs.getLong("budget", 150_000L)
        set(value) = prefs.edit().putLong("budget", value).apply()

    var userName: String
        get() = prefs.getString("userName", "") ?: ""
        set(value) = prefs.edit().putString("userName", value).apply()

    var goals: String
        get() = prefs.getString("goals", "") ?: ""
        set(value) = prefs.edit().putString("goals", value).apply()

    var about: String
        get() = prefs.getString("about", "") ?: ""
        set(value) = prefs.edit().putString("about", value).apply()

    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(value) = prefs.edit().putString("apiKey", value).apply()

    var model: String
        get() = prefs.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
        set(value) = prefs.edit().putString("model", value).apply()

    /** 1 = yumshoq, 2 = qattiq, 3 = juda qattiq */
    var strict: Int
        get() = prefs.getInt("strict", 2).coerceIn(1, 3)
        set(value) = prefs.edit().putInt("strict", value.coerceIn(1, 3)).apply()

    var remindEnabled: Boolean
        get() = prefs.getBoolean("remindEnabled", true)
        set(value) = prefs.edit().putBoolean("remindEnabled", value).apply()

    var waterEnabled: Boolean
        get() = prefs.getBoolean("waterEnabled", true)
        set(value) = prefs.edit().putBoolean("waterEnabled", value).apply()

    var waterMinutes: Int
        get() = prefs.getInt("waterMinutes", 120).coerceIn(15, 480)
        set(value) = prefs.edit().putInt("waterMinutes", value.coerceIn(15, 480)).apply()

    var waterNext: Long
        get() = prefs.getLong("waterNext", 0L)
        set(value) = prefs.edit().putLong("waterNext", value).apply()

    // ---------------- Alarm kodlari ----------------

    fun getAlarmCodes(): List<Int> {
        val raw = prefs.getString("alarm_codes", "") ?: ""
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun saveAlarmCodes(codes: Collection<Int>) {
        prefs.edit().putString("alarm_codes", codes.joinToString(",")).apply()
    }

    // ---------------- Vaqt yordamchilari ----------------

    fun todayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun todayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    // ---------------- Bloklar ----------------

    fun getBlocks(): MutableList<TimeBlock> {
        val raw = prefs.getString("blocks_json", null)
        if (raw == null) {
            val defaults = mutableListOf(
                TimeBlock(1L, "1-Juftlik (HEMIS)", "Dars / HEMIS", 9 * 60, 80),
                TimeBlock(2L, "Vazifa va Kod bloki", "Vazifa", 11 * 60, 90),
                TimeBlock(3L, "Tushlik va Suv", "Ovqat/Ichimlik", 13 * 60, 45)
            )
            saveBlocks(defaults)
            return defaults
        }
        val arr = JSONArray(raw)
        val out = mutableListOf<TimeBlock>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                TimeBlock(
                    id = o.optLong("id", i + 1L),
                    title = o.optString("title", "Blok"),
                    category = o.optString("category", "Intizom"),
                    startMinutes = o.optInt("startMinutes", 540),
                    durationMinutes = o.optInt("durationMinutes", 60)
                )
            )
        }
        return out
    }

    fun saveBlocks(list: List<TimeBlock>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("category", b.category)
                    put("startMinutes", b.startMinutes)
                    put("durationMinutes", b.durationMinutes)
                }
            )
        }
        prefs.edit().putString("blocks_json", arr.toString()).apply()
    }

    fun deleteBlock(id: Long) {
        saveBlocks(getBlocks().filter { it.id != id })
    }

    fun isDone(blockId: Long): Boolean {
        val set = prefs.getStringSet("done_" + todayKey(), emptySet()) ?: emptySet()
        return set.contains(blockId.toString())
    }

    fun setDone(blockId: Long, done: Boolean) {
        val key = "done_" + todayKey()
        val set = HashSet<String>(prefs.getStringSet(key, emptySet()) ?: emptySet())
        if (done) set.add(blockId.toString()) else set.remove(blockId.toString())
        prefs.edit().putStringSet(key, set).apply()
    }

    // ---------------- Xarajatlar ----------------

    fun getExpenses(): MutableList<ExpenseItem> {
        val raw = prefs.getString("expenses_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<ExpenseItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                ExpenseItem(
                    id = o.optLong("id", i + 1L),
                    amount = o.optLong("amount", 0L),
                    note = o.optString("note", "Xarajat")
                )
            )
        }
        return out
    }

    fun addExpense(amount: Long, note: String) {
        val list = getExpenses()
        list.add(0, ExpenseItem(System.currentTimeMillis(), amount, note))
        val arr = JSONArray()
        list.take(500).forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("amount", e.amount)
                put("note", e.note)
            })
        }
        prefs.edit().putString("expenses_json", arr.toString()).apply()
    }

    fun clearExpenses() {
        prefs.edit().putString("expenses_json", "[]").apply()
    }

    fun todayExpenses(): List<ExpenseItem> {
        val start = todayStart()
        return getExpenses().filter { it.id >= start }
    }

    // ---------------- Ovqat ----------------

    fun getFoods(): MutableList<FoodItem> {
        val raw = prefs.getString("foods_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<FoodItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                FoodItem(
                    id = o.optLong("id", i + 1L),
                    name = o.optString("name", "Ovqat/Ichimlik"),
                    timeText = o.optString("timeText", "")
                )
            )
        }
        return out
    }

    fun addFood(name: String, timeText: String) {
        val list = getFoods()
        list.add(0, FoodItem(System.currentTimeMillis(), name, timeText))
        val arr = JSONArray()
        list.take(100).forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("timeText", f.timeText)
            })
        }
        prefs.edit().putString("foods_json", arr.toString()).apply()
    }

    fun todayFoods(): List<FoodItem> {
        val start = todayStart()
        return getFoods().filter { it.id >= start }
    }

    // ---------------- Vazifa / imtihon ----------------

    fun getExamsTasks(): MutableList<ExamTaskItem> {
        val raw = prefs.getString("exams_json", null)
        if (raw == null) {
            val defaults = mutableListOf(
                ExamTaskItem(1L, "Oraliq nazorat (HEMIS)", "Yaqinlashmoqda"),
                ExamTaskItem(2L, "Amaliy topshiriq", "Bugun")
            )
            saveExamsTasks(defaults)
            return defaults
        }
        val arr = JSONArray(raw)
        val out = mutableListOf<ExamTaskItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                ExamTaskItem(
                    id = o.optLong("id", i + 1L),
                    title = o.optString("title", "Vazifa"),
                    dueText = o.optString("dueText", "")
                )
            )
        }
        return out
    }

    fun addExamTask(title: String, dueText: String) {
        val list = getExamsTasks()
        list.add(0, ExamTaskItem(System.currentTimeMillis(), title, dueText))
        saveExamsTasks(list)
    }

    fun deleteExamTask(id: Long) {
        saveExamsTasks(getExamsTasks().filter { it.id != id })
    }

    fun saveExamsTasks(list: List<ExamTaskItem>) {
        val arr = JSONArray()
        list.take(40).forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("dueText", item.dueText)
            })
        }
        prefs.edit().putString("exams_json", arr.toString()).apply()
    }

    // ---------------- Suhbat tarixi ----------------

    fun getChat(): MutableList<ChatMsg> {
        val raw = prefs.getString("chat_json", "[]") ?: "[]"
        val out = mutableListOf<ChatMsg>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(ChatMsg(o.optString("role", "user"), o.optString("content", "")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }

    fun saveChat(list: List<ChatMsg>) {
        val arr = JSONArray()
        list.takeLast(60).forEach { m ->
            arr.put(JSONObject().apply {
                put("role", m.role)
                put("content", m.content)
            })
        }
        prefs.edit().putString("chat_json", arr.toString()).apply()
    }

    fun clearChat() {
        prefs.edit().putString("chat_json", "[]").apply()
    }

    // ---------------- Hisob-kitob ----------------

    fun totalSpent(): Long = getExpenses().sumOf { it.amount }
    fun remainingBudget(): Long = budget - totalSpent()

    fun buildAdviceSummary(): String {
        val rem = remainingBudget()
        val spent = totalSpent()
        val foodsCount = todayFoods().size
        val blocks = getBlocks()
        val doneCount = blocks.count { isDone(it.id) }
        val examsCount = getExamsTasks().size

        val tips = mutableListOf<String>()
        if (rem < 0) {
            tips.add("ZARAR: Byudjet minusga o'tdi ($rem so'm). Keraksiz xarajatlarni darhol to'xtating.")
        } else if (budget > 0 && spent > budget * 0.7) {
            tips.add("OGOHLANTIRISH: Byudjetning 70% dan ko'pi sarflandi. Qolgan $rem so'mni ehtiyotkor taqsimlang.")
        } else {
            tips.add("FOYDA: Byudjet nazoratda. Qolgan mablag': $rem so'm.")
        }

        if (foodsCount == 0) {
            tips.add("TARTIB: Bugun ovqat/ichimlik yozuvi yo'q. Ovqatlandingizmi? Suv ichdingizmi?")
        } else {
            tips.add("OVQAT/SUV: bugun $foodsCount ta yozuv qayd etilgan.")
        }

        tips.add("REJA: bugun ${blocks.size} blokdan $doneCount tasi bajarilgan, $examsCount ta vazifa/imtihon nazoratda.")
        return tips.joinToString("\n• ", prefix = "• ")
    }
}
