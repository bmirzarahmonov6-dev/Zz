package uz.zikr.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

/** Boshqa ilovalar ustida turadigan yumaloq zikr hisoblagich. */
class FloatingService : Service() {

    companion object {
        const val ACTION_STOP = "uz.zikr.app.STOP"
        private const val CHANNEL_ID = "zikr_channel"
        private const val NOTIF_ID = 1
        const val SIZE_DP = 76
    }

    private lateinit var wm: WindowManager
    private lateinit var counterView: CounterView
    private lateinit var lp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false

    // Asosiy ilovada (reset, maqsad) o'zgarsa doira ham yangilansin.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refresh()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground()
        addOverlay()
        ZikrPrefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener)
        ZikrPrefs.of(this).edit().putBoolean(ZikrPrefs.RUNNING, true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ZikrPrefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        ZikrPrefs.of(this).edit().putBoolean(ZikrPrefs.RUNNING, false).apply()
        handler.removeCallbacksAndMessages(null)
        if (attached) {
            try {
                wm.removeView(counterView)
            } catch (_: Exception) {
            }
            attached = false
        }
        super.onDestroy()
    }

    // ---------- Bildirishnoma (foreground service uchun majburiy) ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Zikr", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notif), "To'xtatish", stopPi
        ).build()
        return builder
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Zikr")
            .setContentText("Suzuvchi doira yoqilgan")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(stopAction)
            .build()
    }

    private fun startAsForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---------- Suzuvchi doira ----------

    @Suppress("DEPRECATION")
    private fun addOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val sizePx = (SIZE_DP * density).toInt()

        counterView = CounterView(this)
        refresh()

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val prefs = ZikrPrefs.of(this)
        val defX = resources.displayMetrics.widthPixels - sizePx - (16 * density).toInt()
        val defY = (200 * density).toInt()

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(ZikrPrefs.POS_X, defX)
            y = prefs.getInt(ZikrPrefs.POS_Y, defY)
        }
        clampPosition()

        attachTouch(counterView)

        try {
            wm.addView(counterView, lp)
            attached = true
        } catch (e: Exception) {
            // Ruxsat yo'q bo'lsa
            stopSelf()
        }
    }

    private fun clampPosition() {
        val dm = resources.displayMetrics
        val sizePx = (SIZE_DP * dm.density).toInt()
        lp.x = lp.x.coerceIn(0, (dm.widthPixels - sizePx).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (dm.heightPixels - sizePx).coerceAtLeast(0))
    }

    private fun attachTouch(v: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var longPressed = false

        val longPress = Runnable {
            longPressed = true
            buzz(40)
            openApp()
        }

        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    longPressed = false
                    handler.postDelayed(longPress, 700)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        handler.removeCallbacks(longPress)
                    }
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        clampPosition()
                        try {
                            wm.updateViewLayout(counterView, lp)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (moved) {
                        ZikrPrefs.of(this).edit()
                            .putInt(ZikrPrefs.POS_X, lp.x)
                            .putInt(ZikrPrefs.POS_Y, lp.y)
                            .apply()
                    } else if (!longPressed) {
                        increment()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    true
                }
                else -> false
            }
        }
    }

    private fun increment() {
        val prefs = ZikrPrefs.of(this)
        val target = prefs.getInt(ZikrPrefs.TARGET, 33).coerceAtLeast(1)
        val c = prefs.getInt(ZikrPrefs.COUNT, 0) + 1
        prefs.edit().putInt(ZikrPrefs.COUNT, c).apply()
        refresh()
        // Maqsadga yetganda uzunroq tebranish
        buzz(if (c % target == 0) 300 else 20)
    }

    private fun refresh() {
        if (!::counterView.isInitialized) return
        val prefs = ZikrPrefs.of(this)
        counterView.count = prefs.getInt(ZikrPrefs.COUNT, 0)
        counterView.target = prefs.getInt(ZikrPrefs.TARGET, 33).coerceAtLeast(1)
        counterView.invalidate()
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz(ms: Long) {
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vib.vibrate(ms)
        }
    }

    // ---------- Doira ko'rinishi ----------

    private class CounterView(context: Context) : View(context) {
        var count = 0
        var target = 33

        private val d = resources.displayMetrics.density
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * d
            color = Color.parseColor("#6EE7C8")
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * d
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }
        private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9FFFFFF")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 9 * d
            letterSpacing = 0.12f
        }
        private val rect = RectF()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val s = (SIZE_DP * d).toInt()
            setMeasuredDimension(s, s)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            fill.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#2DD4A8"), Color.parseColor("#0B7A66"),
                Shader.TileMode.CLAMP
            )
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = 31 * d

            // yumshoq soya
            shadow.color = Color.argb(16, 0, 0, 0)
            for (i in 1..4) {
                canvas.drawCircle(cx, cy + 2 * d, r + i * d, shadow)
            }

            // asosiy doira
            canvas.drawCircle(cx, cy, r, fill)

            // hoshiya va maqsad sari to'lib boruvchi yoy
            val rr = r - 1.5f * d
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            canvas.drawArc(rect, 0f, 360f, false, border)
            val p = if (count > 0 && count % target == 0) 1f
            else (count % target) / target.toFloat()
            canvas.drawArc(rect, -90f, 360f * p, false, arc)

            // raqam va "ZIKR" yozuvi
            val s = count.toString()
            number.textSize = when {
                s.length >= 5 -> 13f
                s.length == 4 -> 16f
                s.length == 3 -> 20f
                else -> 24f
            } * d
            val ny = (cy - 5 * d) - (number.descent() + number.ascent()) / 2f
            canvas.drawText(s, cx, ny, number)
            val ly = (cy + 15 * d) - (label.descent() + label.ascent()) / 2f
            canvas.drawText("ZIKR", cx, ly, label)
        }
    }
}
