package uz.nizom.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

object ReminderScheduler {

    const val ACTION_FIRE = "uz.nizom.app.REMINDER_FIRE"
    private const val CHANNEL = "nizom_reminders"

    private const val CODE_WATER = 1
    private const val CODE_MORNING = 2
    private const val CODE_EVENING = 3

    // ---------------- Matnlar (qattiqlik darajasiga qarab) ----------------

    private fun pick(level: Int, soft: String, firm: String, harsh: String): String =
        when (level) {
            1 -> soft
            3 -> harsh
            else -> firm
        }

    private fun startText(level: Int, title: String) = pick(
        level,
        "«$title» vaqti keldi. Boshlaymizmi?",
        "«$title» boshlanadi. Telefonni qo'ying va ishga o'ting.",
        "«$title» VAQTI. Bahona yo'q. Hozir boshlang."
    )

    private fun soonText(level: Int, title: String) = pick(
        level,
        "5 daqiqadan keyin: $title. Tayyorlanib oling.",
        "5 daqiqa qoldi: $title. Hozirdan tayyorlaning.",
        "5 daqiqa: $title. Telefonni qo'ying, tayyor turing."
    )

    private fun waterText(level: Int) = pick(
        level,
        "Suv ichish vaqti. Bir stakan suv iching.",
        "Suv iching. Hozir.",
        "Suv ichmayapsiz. Hozir bir stakan iching va Nizom'ga yozing."
    )

    private fun morningText(level: Int) = pick(
        level,
        "Xayrli tong! Bugungi rejani birga tuzamizmi?",
        "Ertalabki reja vaqti. Nizom'ni oching va bugungi rejani tuzing.",
        "Kun boshlandi. Rejasiz kun — yo'qotilgan kun. Hozir Nizom'ni oching."
    )

    private fun eveningText(level: Int) = pick(
        level,
        "Kunni sarhisob qilish vaqti. Bugun nima yaxshi o'tdi?",
        "Kun sarhisobi vaqti. Nima bajarildi, nima qoldi? Halol yozing.",
        "Sarhisob vaqti. Bajarilmagan ishlarni yashirmang, hozir Nizom'ga yozing."
    )

    // ---------------- Rejalashtirish ----------------

    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        val store = NizomStore(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (code in store.getAlarmCodes()) cancelAlarm(app, am, code)

        val codes = mutableListOf<Int>()
        if (!store.remindEnabled) {
            store.saveAlarmCodes(codes)
            return
        }

        val level = store.strict
        val now = System.currentTimeMillis()

        for (b in store.getBlocks()) {
            val base = (b.id % 1_000_000L).toInt() * 4 + 100

            setAlarm(
                app, am, base, nextOccurrence(b.startMinutes, now),
                "start", "Nizom • ${b.title}", startText(level, b.title)
            )
            codes.add(base)

            val soonMin = (b.startMinutes - 5 + 1440) % 1440
            setAlarm(
                app, am, base + 1, nextOccurrence(soonMin, now),
                "soon", "Nizom • 5 daqiqa qoldi", soonText(level, b.title)
            )
            codes.add(base + 1)
        }

        setAlarm(
            app, am, CODE_MORNING, nextOccurrence(7 * 60 + 30, now),
            "morning", "Nizom • Ertalabki reja", morningText(level)
        )
        codes.add(CODE_MORNING)

        setAlarm(
            app, am, CODE_EVENING, nextOccurrence(21 * 60 + 30, now),
            "evening", "Nizom • Kun sarhisobi", eveningText(level)
        )
        codes.add(CODE_EVENING)

        if (store.waterEnabled) {
            var next = store.waterNext
            if (next <= now) {
                next = computeWaterNext(now, store.waterMinutes)
                store.waterNext = next
            }
            setAlarm(app, am, CODE_WATER, next, "water", "Nizom • Suv", waterText(level))
            codes.add(CODE_WATER)
        }

        store.saveAlarmCodes(codes)
    }

    private fun nextOccurrence(minutesOfDay: Int, now: Long): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        cal.set(Calendar.MINUTE, minutesOfDay % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Suv eslatmasi faqat 07:00 - 22:00 oralig'ida. */
    private fun computeWaterNext(now: Long, minutes: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now + minutes * 60_000L
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour >= 22) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 7)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else if (hour < 7) {
            cal.set(Calendar.HOUR_OF_DAY, 7)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun setAlarm(
        ctx: Context,
        am: AlarmManager,
        code: Int,
        at: Long,
        type: String,
        title: String,
        text: String
    ) {
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra("type", type)
            .putExtra("title", title)
            .putExtra("text", text)
        val pi = PendingIntent.getBroadcast(
            ctx, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(ctx: Context, am: AlarmManager, code: Int) {
        val intent = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        val pi = PendingIntent.getBroadcast(
            ctx, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        pi.cancel()
    }

    // ---------------- Bildirishnoma ----------------

    fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL, "Nizom eslatmalari", NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }

        val n = builder
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        nm.notify(id, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val type = intent.getStringExtra("type") ?: return
        val title = intent.getStringExtra("title") ?: "Nizom"
        val text = intent.getStringExtra("text") ?: ""

        if (type == "water") NizomStore(context).waterNext = 0L

        ReminderScheduler.notify(context, (type + title).hashCode(), title, text)
        ReminderScheduler.scheduleAll(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.scheduleAll(context)
    }
}
