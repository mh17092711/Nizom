package uz.nizom.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

class NizomPillService : Service() {

    private var windowManager: WindowManager? = null
    private var pillContainer: LinearLayout? = null
    private var compactText: TextView? = null
    private var expandedText: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isExpanded = false
    private var autoCollapseRunnable: Runnable? = null

    private val tickerRunnable = object : Runnable {
        override fun run() {
            updatePillContent()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(12)
            val padV = dp(5)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6101318"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }

        val compact = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11.5f
            maxLines = 1
        }

        val expanded = TextView(this).apply {
            setTextColor(Color.parseColor("#E8EEF5"))
            textSize = 11f
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }

        container.addView(compact)
        container.addView(expanded)

        container.setOnClickListener {
            toggleExpanded()
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(6)
        }

        pillContainer = container
        compactText = compact
        expandedText = expanded
        layoutParams = params

        windowManager?.addView(container, params)
        updatePillContent()
        handler.post(tickerRunnable)
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        expandedText?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        autoCollapseRunnable?.let { handler.removeCallbacks(it) }
        if (isExpanded) {
            val collapse = Runnable {
                isExpanded = false
                expandedText?.visibility = View.GONE
                updatePillContent()
            }
            autoCollapseRunnable = collapse
            handler.postDelayed(collapse, 8000L)
        }
        updatePillContent()
    }

    private fun updatePillContent() {
        val store = NizomStore(this)
        val cal = Calendar.getInstance()
        val nowSecOfDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
            cal.get(Calendar.MINUTE) * 60 +
            cal.get(Calendar.SECOND)

        val blocks = store.getBlocks()
        val activeBlock = blocks.firstOrNull { b ->
            val startSec = b.startMinutes * 60
            val endSec = startSec + b.durationMinutes * 60
            nowSecOfDay in startSec until endSec
        }

        val nextBlock = blocks
            .filter { it.startMinutes * 60 > nowSecOfDay }
            .minByOrNull { it.startMinutes }

        if (activeBlock != null) {
            val endSec = (activeBlock.startMinutes + activeBlock.durationMinutes) * 60
            val remSec = (endSec - nowSecOfDay).coerceAtLeast(0)
            val mm = remSec / 60
            val ss = remSec % 60
            compactText?.text = String.format(
                Locale.US,
                "NIZOM • %s  -%02d:%02d",
                activeBlock.title,
                mm,
                ss
            )
        } else {
            val nextInfo = if (nextBlock != null) {
                val h = nextBlock.startMinutes / 60
                val m = nextBlock.startMinutes % 60
                String.format(Locale.US, "Keyingi: %s (%02d:%02d)", nextBlock.title, h, m)
            } else {
                "Faol blok yo'q"
            }
            compactText?.text = "NIZOM • $nextInfo"
        }

        if (isExpanded) {
            val nextExam = store.getExamsTasks().firstOrNull()?.let { "${it.title} (${it.dueText})" }
                ?: "Vazifa/Imtihon kiritilmagan"
            val details = buildString {
                append("Holat: ")
                append(activeBlock?.let { "${it.title} [${it.category}]" } ?: "Bo'sh/Kutilmoqda")
                append("\nQolgan byudjet: ${store.remainingBudget()} so'm")
                append("\nYaqin vazifa/imtihon: $nextExam")
                append("\n(Yana bosing yoki 8s da yig'iladi)")
            }
            expandedText?.text = details
        }

        val currentParams = layoutParams
        val view = pillContainer
        if (currentParams != null && view != null) {
            try {
                windowManager?.updateViewLayout(view, currentParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(tickerRunnable)
        autoCollapseRunnable?.let { handler.removeCallbacks(it) }
        pillContainer?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        pillContainer = null
        super.onDestroy()
    }
}
