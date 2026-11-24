package uz.nizom.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

/** Bugungi ekran vaqti (ilovalar bo'yicha). "Foydalanish statistikasi" ruxsati kerak. */
object UsageHelper {

    fun hasAccess(context: Context): Boolean {
        return try {
            val aom = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                aom.unsafeCheckOpNoThrow("android:get_usage_stats", Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                aom.checkOpNoThrow("android:get_usage_stats", Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** (ilova nomi, millisekund) juftliklari, ko'pdan kamga. */
    fun today(context: Context): List<Pair<String, Long>> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val map = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
            val pm = context.packageManager
            map.values
                .filter { it.totalTimeInForeground > 60_000L && it.packageName != context.packageName }
                .sortedByDescending { it.totalTimeInForeground }
                .map { u ->
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(u.packageName, 0)).toString()
                    } catch (e: Exception) {
                        u.packageName
                    }
                    Pair(label, u.totalTimeInForeground)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
