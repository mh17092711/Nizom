package uz.nizom.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var store: NizomStore
    private lateinit var content: FrameLayout
    private val tabButtons = ArrayList<Button>()
    private var currentTab = TAB_HOME

    private var chatContainer: LinearLayout? = null
    private var chatScroll: ScrollView? = null
    private var sending = false
    private var lastError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = NizomStore(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(0, dp(28), 0, 0)
        }
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#ECEFF1"))
        }
        val names = arrayOf("Bosh", "Murabbiy", "Reja", "Sozlamalar")
        for (i in names.indices) {
            val b = Button(this).apply {
                text = names[i]
                textSize = 12f
                isAllCaps = false
                setOnClickListener { showTab(i) }
            }
            tabButtons.add(b)
            nav.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)

        ReminderScheduler.scheduleAll(this)
        showTab(TAB_HOME)
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && currentTab == TAB_HOME) showTab(TAB_HOME)
    }

    // ------------------------------------------------------------------
    // Yordamchi funksiyalar
    // ------------------------------------------------------------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(top: Int = 8): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun fmt(min: Int): String = CoachContext.fmt(min)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun label(text: String, size: Float = 15f, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
        }

    private fun btn(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun smallBtn(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { onClick() }
        }

    private fun edit(
        hint: String,
        number: Boolean = false,
        multiline: Boolean = false,
        password: Boolean = false
    ): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = when {
                number -> InputType.TYPE_CLASS_NUMBER
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multiline) minLines = 2
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }

    private fun newPage(): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        scroll.addView(col)
        return Pair(scroll, col)
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        chatContainer = null
        chatScroll = null
        content.removeAllViews()
        val view: View = when (tab) {
            TAB_COACH -> buildCoach()
            TAB_PLAN -> buildPlan()
            TAB_SETTINGS -> buildSettings()
            else -> buildHome()
        }
        content.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        for (i in tabButtons.indices) {
            tabButtons[i].setTypeface(null, if (i == tab) Typeface.BOLD else Typeface.NORMAL)
            tabButtons[i].alpha = if (i == tab) 1f else 0.6f
        }
    }

    // ------------------------------------------------------------------
    // BOSH
    // ------------------------------------------------------------------

    private fun buildHome(): View {
        val (scroll, col) = newPage()

        col.addView(label("NIZOM", 28f, true), lp(8))
        val name = store.userName
        col.addView(
            label(
                if (name.isBlank()) "Vaqtingiz — nazoratda." else "Salom, $name. Vaqtingiz — nazoratda.",
                15f
            ),
            lp(2)
        )

        col.addView(label(CoachContext.todayStatus(store), 16f), lp(16))

        col.addView(label("Tepadagi pill", 18f, true), lp(20))
        col.addView(btn("Overlay ruxsatini berish / tekshirish") { checkOverlayPermission() }, lp(4))
        col.addView(btn("Pill'ni YOQISH") {
            if (!Settings.canDrawOverlays(this)) {
                checkOverlayPermission()
            } else {
                startService(Intent(this, NizomPillService::class.java))
                toast("Nizom Pill yoqildi")
            }
        }, lp(4))
        col.addView(btn("Pill'ni O'CHIRISH") {
            stopService(Intent(this, NizomPillService::class.java))
            toast("Nizom Pill o'chirildi")
        }, lp(4))

        col.addView(label("Holat va maslahat", 18f, true), lp(20))
        col.addView(label(store.buildAdviceSummary(), 14f), lp(4))

        col.addView(label("Ekran vaqti (bugun)", 18f, true), lp(20))
        if (UsageHelper.hasAccess(this)) {
            val apps = UsageHelper.today(this)
            if (apps.isEmpty()) {
                col.addView(label("Hozircha ma'lumot yo'q.", 14f), lp(4))
            } else {
                val totalMin = apps.sumOf { it.second } / 60_000L
                col.addView(label("Jami: taxminan $totalMin daqiqa", 14f, true), lp(4))
                val lines = apps.take(6).joinToString("\n") { "• ${it.first}: ${it.second / 60_000L} daq" }
                col.addView(label(lines, 14f), lp(4))
            }
        } else {
            col.addView(
                label("Bo'sh vaqtingiz qayerga ketayotganini ko'rish uchun ruxsat bering.", 14f),
                lp(4)
            )
            col.addView(btn("Ekran vaqti ruxsatini berish") {
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: Exception) {
                    toast("Sozlamalarni ochib bo'lmadi")
                }
            }, lp(4))
        }

        return scroll
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } else {
            toast("Overlay ruxsati allaqachon berilgan")
        }
    }

    // ------------------------------------------------------------------
    // MURABBIY (AI suhbat)
    // ------------------------------------------------------------------

    private fun buildCoach(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        scroll.addView(list)
        chatScroll = scroll
        chatContainer = list
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val quickScroll = HorizontalScrollView(this)
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quickRow.addView(btn("☀ Ertalabki reja") {
            sendToCoach(
                "Bugungi kun uchun reja tuz. Jadvalim, vazifalarim va maqsadlarimni hisobga ol. " +
                    "Qisqa va aniq, ustuvorliklar bilan."
            )
        })
        quickRow.addView(btn("🌙 Kun sarhisobi") {
            sendToCoach(
                "Kunni sarhisob qil: nima bajarildi, nima bajarilmadi, vaqt qayerda ketdi, " +
                    "ertaga nimani yaxshilash kerak. Halol va qattiq bo'l."
            )
        })
        quickRow.addView(btn("🔍 Meni tekshir") {
            sendToCoach(
                "Hozir nima qilishim kerak? Jadvalimga qarab menga to'g'ri ayt va meni qattiq tekshir."
            )
        })
        quickScroll.addView(quickRow)
        col.addView(quickScroll, LinearLayout.LayoutParams(MATCH, WRAP))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), dp(4))
        }
        val input = edit("Murabbiyga yozing...", multiline = true)
        input.maxLines = 4
        inputRow.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
        inputRow.addView(btn("Yuborish") {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) {
                input.text.clear()
                sendToCoach(t)
            }
        })
        col.addView(inputRow, LinearLayout.LayoutParams(MATCH, WRAP))

        renderChat()
        return col
    }

    private fun bubble(text: String, user: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#212121"))
            setTextIsSelectable(true)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (user) "#DCEBFF" else "#F1F3F4"))
                cornerRadius = dp(14).toFloat()
            }
        }

    private fun bubbleParams(user: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = if (user) Gravity.END else Gravity.START
            topMargin = dp(8)
            if (user) leftMargin = dp(48) else rightMargin = dp(48)
        }

    private fun renderChat() {
        val list = chatContainer ?: return
        list.removeAllViews()

        val chat = store.getChat()
        if (chat.isEmpty() && !sending) {
            list.addView(
                bubble(
                    "Men Nizom — shaxsiy murabbiyingizman. Jadvalingiz, vazifalaringiz, xarajatlaringiz, " +
                        "ovqatlanishingiz va ekran vaqtingizni bilaman. Yozing yoki pastdagi tugmalardan foydalaning.\n\n" +
                        "Avval Sozlamalar bo'limida API kalitni kiriting.",
                    false
                ),
                bubbleParams(false)
            )
        }
        for (m in chat) {
            val isUser = m.role == "user"
            list.addView(bubble(m.content, isUser), bubbleParams(isUser))
        }
        if (sending) {
            list.addView(bubble("Nizom o'ylayapti...", false), bubbleParams(false))
        }
        val err = lastError
        if (err != null && !sending) {
            val e = label(err, 14f)
            e.setTextColor(Color.parseColor("#C62828"))
            list.addView(e, lp(8))
        }
        chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendToCoach(text: String) {
        if (sending) {
            toast("Javob kutilmoqda...")
            return
        }
        val key = store.apiKey
        if (key.isBlank()) {
            toast("Avval Sozlamalar bo'limida API kalitni kiriting")
            return
        }

        val chat = store.getChat()
        chat.add(ChatMsg("user", text))
        store.saveChat(chat)
        lastError = null
        sending = true
        renderChat()

        var history: List<ChatMsg> = chat.takeLast(16)
        while (history.isNotEmpty() && history.first().role != "user") {
            history = history.drop(1)
        }
        val system = CoachContext.build(this, store)

        AiClient.chat(key, store.model, system, history) { reply, error ->
            sending = false
            if (reply != null) {
                val updated = store.getChat()
                updated.add(ChatMsg("assistant", reply))
                store.saveChat(updated)
            } else {
                lastError = error ?: "Noma'lum xato"
            }
            if (currentTab == TAB_COACH) renderChat()
        }
    }

    // ------------------------------------------------------------------
    // REJA
    // ------------------------------------------------------------------

    private fun buildPlan(): View {
        val (scroll, col) = newPage()

        // ---- Bloklar ----
        col.addView(label("Kun tartibi / Dars (HEMIS)", 20f, true), lp(8))
        val blocks = store.getBlocks().sortedBy { it.startMinutes }
        if (blocks.isEmpty()) col.addView(label("Hali blok yo'q.", 14f), lp(4))
        for (b in blocks) {
            val done = store.isDone(b.id)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val t = label(
                (if (done) "✓ " else "• ") +
                    "${fmt(b.startMinutes)}–${fmt(b.startMinutes + b.durationMinutes)}  ${b.title}",
                15f
            )
            row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(smallBtn(if (done) "↩" else "✓") {
                store.setDone(b.id, !done)
                showTab(TAB_PLAN)
            })
            row.addView(smallBtn("🗑") {
                store.deleteBlock(b.id)
                ReminderScheduler.scheduleAll(this)
                showTab(TAB_PLAN)
            })
            col.addView(row, lp(4))
        }

        col.addView(label("Yangi blok", 16f, true), lp(16))
        val etBlockTitle = edit("Nomi (masalan: 2-Juftlik yoki Kod)")
        val etBlockHour = edit("Boshlanish soati (0..23)", number = true)
        val etBlockMin = edit("Daqiqa (0..59)", number = true)
        val etBlockDur = edit("Davomiyligi, daqiqada (masalan: 80)", number = true)
        col.addView(etBlockTitle, lp(0))
        col.addView(etBlockHour, lp(0))
        col.addView(etBlockMin, lp(0))
        col.addView(etBlockDur, lp(0))
        col.addView(btn("Blokni qo'shish") {
            val bTitle = etBlockTitle.text.toString().trim()
            val h = etBlockHour.text.toString().trim().toIntOrNull()
            val m = etBlockMin.text.toString().trim().toIntOrNull()
            val dur = etBlockDur.text.toString().trim().toIntOrNull()
            if (bTitle.isNotEmpty() && h != null && m != null && dur != null &&
                h in 0..23 && m in 0..59 && dur > 0
            ) {
                val list = store.getBlocks()
                list.add(TimeBlock(System.currentTimeMillis(), bTitle, "Dars / HEMIS", h * 60 + m, dur))
                store.saveBlocks(list)
                ReminderScheduler.scheduleAll(this)
                toast("Blok saqlandi")
                showTab(TAB_PLAN)
            } else {
                toast("Ma'lumotlarni to'g'ri kiriting")
            }
        }, lp(4))

        // ---- Vazifalar ----
        col.addView(label("Vazifa / Oraliq / Imtihon", 20f, true), lp(28))
        val tasks = store.getExamsTasks()
        if (tasks.isEmpty()) col.addView(label("Hali yo'q.", 14f), lp(4))
        for (t in tasks) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(label("• ${t.title} — ${t.dueText}", 15f), LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(smallBtn("✓") {
                store.deleteExamTask(t.id)
                showTab(TAB_PLAN)
            })
            col.addView(row, lp(4))
        }
        val etExamTitle = edit("Vazifa yoki imtihon nomi")
        val etExamDue = edit("Sana yoki muddat (masalan: 25-noyabr)")
        col.addView(etExamTitle, lp(8))
        col.addView(etExamDue, lp(0))
        col.addView(btn("Vazifa/Imtihon qo'shish") {
            val t = etExamTitle.text.toString().trim()
            val d = etExamDue.text.toString().trim()
            if (t.isNotEmpty()) {
                store.addExamTask(t, if (d.isEmpty()) "Belgilangan" else d)
                showTab(TAB_PLAN)
            }
        }, lp(4))

        // ---- Byudjet ----
        col.addView(label("Byudjet va xarajat", 20f, true), lp(28))
        col.addView(
            label(
                "Jami: ${store.budget} so'm | Sarflangan: ${store.totalSpent()} so'm | Qolgan: ${store.remainingBudget()} so'm",
                14f
            ),
            lp(4)
        )
        val etBudget = edit("Umumiy byudjetni o'zgartirish (so'm)", number = true)
        col.addView(etBudget, lp(8))
        col.addView(btn("Byudjetni yangilash") {
            val b = etBudget.text.toString().trim().toLongOrNull()
            if (b != null && b >= 0) {
                store.budget = b
                showTab(TAB_PLAN)
            }
        }, lp(4))
        val etAmount = edit("Xarajat summasi (so'm)", number = true)
        val etNote = edit("Nimaga sarflandi? (Tushlik, Yo'l haqi...)")
        col.addView(etAmount, lp(8))
        col.addView(etNote, lp(0))
        col.addView(btn("Xarajatni yozish") {
            val amt = etAmount.text.toString().trim().toLongOrNull()
            val note = etNote.text.toString().trim()
            if (amt != null && amt > 0) {
                store.addExpense(amt, if (note.isEmpty()) "Xarajat" else note)
                showTab(TAB_PLAN)
            }
        }, lp(4))
        val recent = store.getExpenses().take(5)
        if (recent.isNotEmpty()) {
            col.addView(
                label(recent.joinToString("\n") { "• ${it.note}: -${it.amount} so'm" }, 14f),
                lp(8)
            )
        }
        col.addView(btn("Xarajatlarni tozalash (yangi davr)") {
            store.clearExpenses()
            showTab(TAB_PLAN)
        }, lp(4))

        // ---- Ovqat ----
        col.addView(label("Ovqat va ichimlik", 20f, true), lp(28))
        val etFood = edit("Nima yedingiz yoki ichdingiz? (Suv 0.5L, Palov...)")
        col.addView(etFood, lp(4))
        col.addView(btn("Qayd etish") {
            val f = etFood.text.toString().trim()
            if (f.isNotEmpty()) {
                val nowStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                store.addFood(f, nowStr)
                showTab(TAB_PLAN)
            }
        }, lp(4))
        val foods = store.todayFoods().take(8)
        if (foods.isNotEmpty()) {
            col.addView(
                label(foods.joinToString("\n") { "• ${it.timeText} — ${it.name}" }, 14f),
                lp(8)
            )
        }

        return scroll
    }

    // ------------------------------------------------------------------
    // SOZLAMALAR
    // ------------------------------------------------------------------

    private fun buildSettings(): View {
        val (scroll, col) = newPage()

        col.addView(label("Profil", 20f, true), lp(8))
        col.addView(
            label("Murabbiy sizni shu ma'lumotlar orqali taniydi. Qanchalik aniq yozsangiz, shunchalik foydali.", 13f),
            lp(2)
        )
        val etName = edit("Ismingiz")
        etName.setText(store.userName)
        val etGoals = edit("Maqsadlaringiz (masalan: grantga o'qish, dasturchi bo'lish, 6 oyda ingliz tili)", multiline = true)
        etGoals.setText(store.goals)
        val etAbout = edit("O'zingiz haqingizda (yoshi, o'qishi/ishi, kuchli va zaif tomonlaringiz, odatlaringiz)", multiline = true)
        etAbout.setText(store.about)
        col.addView(etName, lp(8))
        col.addView(etGoals, lp(0))
        col.addView(etAbout, lp(0))

        col.addView(label("Murabbiy qattiqligi", 18f, true), lp(20))
        val rg = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val strictNames = arrayOf("1 — Yumshoq", "2 — Qattiq", "3 — Juda qattiq")
        for (i in strictNames.indices) {
            rg.addView(RadioButton(this).apply {
                id = 200 + i
                text = strictNames[i]
                setTextColor(Color.parseColor("#212121"))
            })
        }
        rg.check(200 + store.strict - 1)
        col.addView(rg, lp(4))

        col.addView(label("AI (Claude)", 18f, true), lp(20))
        col.addView(
            label(
                "API kalitni console.anthropic.com saytidan olasiz (xizmat pullik, ishlatganingizcha to'lanadi). " +
                    "Kalit faqat shu telefonda saqlanadi. Uni hech kimga bermang va GitHub'ga yozmang.",
                13f
            ),
            lp(2)
        )
        val etKey = edit("API kalit (sk-ant-...)", password = true)
        etKey.setText(store.apiKey)
        val etModel = edit("Model nomi")
        etModel.setText(store.model)
        col.addView(etKey, lp(8))
        col.addView(etModel, lp(0))

        col.addView(label("Eslatmalar", 18f, true), lp(20))
        val cbRemind = CheckBox(this).apply {
            text = "Blok, ertalabki reja va kech sarhisob eslatmalari"
            isChecked = store.remindEnabled
            setTextColor(Color.parseColor("#212121"))
        }
        val cbWater = CheckBox(this).apply {
            text = "Suv ichish eslatmasi (07:00–22:00)"
            isChecked = store.waterEnabled
            setTextColor(Color.parseColor("#212121"))
        }
        val etWater = edit("Suv eslatmasi oralig'i, daqiqada (15..480)", number = true)
        etWater.setText(store.waterMinutes.toString())
        col.addView(cbRemind, lp(4))
        col.addView(cbWater, lp(0))
        col.addView(etWater, lp(0))

        col.addView(btn("Saqlash") {
            store.userName = etName.text.toString().trim()
            store.goals = etGoals.text.toString().trim()
            store.about = etAbout.text.toString().trim()
            store.strict = (rg.checkedRadioButtonId - 200 + 1).coerceIn(1, 3)
            store.apiKey = etKey.text.toString().trim()
            val m = etModel.text.toString().trim()
            store.model = if (m.isEmpty()) "claude-sonnet-5-5" else m
            store.remindEnabled = cbRemind.isChecked
            store.waterEnabled = cbWater.isChecked
            store.waterMinutes = etWater.text.toString().trim().toIntOrNull() ?: 120
            store.waterNext = 0L
            ReminderScheduler.scheduleAll(this)
            toast("Saqlandi")
        }, lp(16))

        col.addView(btn("Aniq vaqtli eslatma ruxsati") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    toast("Sozlamalarni ochib bo'lmadi")
                }
            } else {
                toast("Bu qurilmada kerak emas")
            }
        }, lp(8))

        col.addView(btn("Suhbat tarixini tozalash") {
            store.clearChat()
            lastError = null
            toast("Tozalandi")
        }, lp(8))

        col.addView(
            label(
                "Maslahat: Xiaomi, Oppo, Vivo, Huawei telefonlarda eslatmalar o'z vaqtida kelishi uchun " +
                    "Sozlamalar → Ilovalar → Nizom bo'limida batareya cheklovini \"Cheklovsiz\" qiling va " +
                    "\"Avtoishga tushirish\"ni yoqing.",
                13f
            ),
            lp(16)
        )

        return scroll
    }

    private companion object {
        const val TAB_HOME = 0
        const val TAB_COACH = 1
        const val TAB_PLAN = 2
        const val TAB_SETTINGS = 3
    }
}
