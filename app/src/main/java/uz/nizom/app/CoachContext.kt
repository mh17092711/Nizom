package uz.nizom.app

import android.content.Context
import java.util.Calendar
import java.util.Locale

/** AI murabbiy uchun tizim ko'rsatmasi va foydalanuvchining bugungi holati. */
object CoachContext {

    private val DAYS = arrayOf(
        "Yakshanba", "Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba"
    )

    fun fmt(min: Int): String =
        String.format(Locale.US, "%02d:%02d", (min / 60) % 24, min % 60)

    private fun nowMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** Bosh sahifadagi qisqa holat. */
    fun todayStatus(store: NizomStore): String {
        val cal = Calendar.getInstance()
        val nowMin = nowMinutes()
        val blocks = store.getBlocks().sortedBy { it.startMinutes }
        val doneCount = blocks.count { store.isDone(it.id) }
        val active = blocks.firstOrNull {
            nowMin >= it.startMinutes && nowMin < it.startMinutes + it.durationMinutes
        }
        val next = blocks.firstOrNull { it.startMinutes > nowMin }

        val activeText = active?.title ?: "faol blok yo'q"
        val nextText = if (next != null) "${next.title} (${fmt(next.startMinutes)})" else "yo'q"
        return "${DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]}, ${fmt(nowMin)}\n" +
            "Hozir: $activeText\n" +
            "Keyingi: $nextText\n" +
            "Bajarilgan bloklar: $doneCount/${blocks.size}"
    }

    fun build(context: Context, store: NizomStore): String {
        val strictText = when (store.strict) {
            1 -> "1/3 (yumshoq: mehribon va rag'batlantiruvchi, lekin aniq)"
            3 -> "3/3 (juda qattiq: bahonaga yo'l qo'yma, talabchan va to'g'ridan-to'g'ri)"
            else -> "2/3 (qattiq: to'g'ri va talabchan, lekin g'amxo'r)"
        }

        val sb = StringBuilder()
        sb.append(
            "Sen NIZOM — foydalanuvchining shaxsiy murabbiyi va hayotiy maslahatchisisan. " +
                "Maqsading: uning vaqtini nazoratda ushlash, kunini tartibga solish, salomatligi " +
                "(uyqu, ovqat, suv, harakat), o'qishi va moliyaviy intizomi bo'yicha yordam berish " +
                "hamda kelajagi uchun to'g'ri yo'nalish ko'rsatish.\n\n"
        )
        sb.append("USLUB:\n")
        sb.append("- Foydalanuvchining o'zi qattiqqo'l murabbiy so'radi. Qattiqlik darajasi: $strictText.\n")
        sb.append("- To'g'ri, aniq va bahonalarni qabul qilmaydigan bo'l, lekin hech qachon haqorat qilma, kamsitma yoki sharmanda qilma. Qattiqlik g'amxo'rlikdan kelsin.\n")
        sb.append("- O'zbek tilida, oddiy va qisqa yoz (odatda 4-8 qator). Har javob oxirida bitta aniq keyingi qadam ber.\n")
        sb.append("- Faqat quyidagi ma'lumotlarga tayan. Ma'lumot yetishmasa, taxmin qilma, qisqa savol ber.\n")
        sb.append("- Jadvaldan chetga chiqilgan bo'lsa, aniq ayt (qaysi blok, qancha kechikdi).\n")
        sb.append("- Tibbiy tashxis qo'yma; jiddiy sog'liq masalalarida shifokorga murojaat qilishni tavsiya qil.\n")
        sb.append(
            "- Agar foydalanuvchi o'zini juda yomon his qilayotgani, umidsizlik, o'ziga zarar yetkazish " +
                "yoki yashashni xohlamaslik haqida yozsa: qattiqqo'llikni darhol to'xtat, iliq va xotirjam bo'l, " +
                "his-tuyg'ulariga quloq sol, ishongan insoniga yoki mutaxassisga murojaat qilishni yumshoq " +
                "tavsiya qil. Bunday paytda jadval va intizom haqida gapirma.\n\n"
        )

        sb.append("FOYDALANUVCHI HAQIDA:\n")
        sb.append("Ismi: ${if (store.userName.isBlank()) "ko'rsatilmagan" else store.userName}\n")
        sb.append("Maqsadlari: ${if (store.goals.isBlank()) "ko'rsatilmagan" else store.goals}\n")
        sb.append("O'zi haqida: ${if (store.about.isBlank()) "ko'rsatilmagan" else store.about}\n\n")

        sb.append("BUGUNGI HOLAT:\n")
        sb.append(todayStatus(store)).append("\n\n")

        val nowMin = nowMinutes()
        sb.append("Bugungi jadval:\n")
        val blocks = store.getBlocks().sortedBy { it.startMinutes }
        if (blocks.isEmpty()) sb.append("- (jadval bo'sh)\n")
        for (b in blocks) {
            val end = b.startMinutes + b.durationMinutes
            val status = when {
                store.isDone(b.id) -> "BAJARILDI"
                nowMin >= b.startMinutes && nowMin < end -> "HOZIR JARAYONDA"
                nowMin < b.startMinutes -> "KUTILMOQDA"
                else -> "O'TIB KETDI, BAJARILMAGAN"
            }
            sb.append("- ${fmt(b.startMinutes)}-${fmt(end)} ${b.title} [$status]\n")
        }

        sb.append("\nVazifa / imtihonlar:\n")
        val tasks = store.getExamsTasks()
        if (tasks.isEmpty()) sb.append("- yo'q\n")
        for (t in tasks) sb.append("- ${t.title} (${t.dueText})\n")

        val todaySpent = store.todayExpenses().sumOf { it.amount }
        sb.append("\nByudjet: jami ${store.budget} so'm, sarflangan ${store.totalSpent()} so'm, qolgan ${store.remainingBudget()} so'm. Bugungi xarajat: $todaySpent so'm.\n")

        val foods = store.todayFoods()
        sb.append("Bugungi ovqat/ichimlik: ")
        if (foods.isEmpty()) sb.append("hech narsa yozilmagan.\n")
        else sb.append(foods.joinToString("; ") { "${it.timeText} ${it.name}" }).append("\n")

        sb.append("Ekran vaqti (bugun): ")
        if (!UsageHelper.hasAccess(context)) {
            sb.append("ruxsat berilmagan.\n")
        } else {
            val apps = UsageHelper.today(context)
            if (apps.isEmpty()) {
                sb.append("ma'lumot yo'q.\n")
            } else {
                val totalMin = apps.sumOf { it.second } / 60_000L
                sb.append("jami taxminan $totalMin daqiqa. Eng ko'p: ")
                sb.append(apps.take(6).joinToString(", ") { "${it.first} ${it.second / 60_000L} daq" })
                sb.append("\n")
            }
        }
        return sb.toString()
    }
}
