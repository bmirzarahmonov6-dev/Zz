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
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
        const val BASE_SIZE_DP = 76f
    }

    private lateinit var wm: WindowManager
    private lateinit var counterView: CounterView
    private lateinit var lp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false

    // Ovoz tugmasi uchun
    private var session: MediaSession? = null
    @Volatile
    private var silentOn = false
    private var audioThread: Thread? = null

    private var notifPending = false
    private val notifRunnable = Runnable {
        notifPending = false
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    // Sozlama yoki hisob o'zgarsa doira va bildirishnoma yangilansin.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        refresh()
        scheduleNotification()
        if (key == ZikrPrefs.VOLUME) applyVolumeMode()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground()
        addOverlay()
        ZikrPrefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener)
        ZikrPrefs.of(this).edit().putBoolean(ZikrPrefs.RUNNING, true).apply()
        applyVolumeMode()
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
        stopVolumeCapture()
        if (attached) {
            try {
                wm.removeView(counterView)
            } catch (_: Exception) {
            }
            attached = false
        }
        super.onDestroy()
    }

    // ---------- Bildirishnoma (qulflangan ekranda ham ko'rinadi) ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Zikr", NotificationManager.IMPORTANCE_LOW)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun scheduleNotification() {
        if (notifPending) return
        notifPending = true
        handler.postDelayed(notifRunnable, 400)
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
        val plusPi = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, ZikrReceiver::class.java).setAction(ZikrReceiver.ACTION_PLUS),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val plusAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notif), "+1", plusPi
        ).build()
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notif), "To'xtatish", stopPi
        ).build()

        val prefs = ZikrPrefs.of(this)
        val count = prefs.getInt(ZikrPrefs.COUNT, 0)
        val today = ZikrPrefs.dayCount(this, 0)
        val goal = ZikrPrefs.goal(this)

        return builder
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Zikr: $count")
            .setContentText("Bugun $today / $goal")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(plusAction)
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

    // ---------- Ovoz tugmasi bilan sanash ----------
    // Ekran o'chiq yoki qulflangan bo'lsa ham ishlaydi: tizim ovoz tugmasini
    // "media sessiya"ga yo'naltiradi. Yuqori tugma +1, past tugma -1.

    private fun applyVolumeMode() {
        val on = ZikrPrefs.of(this).getBoolean(ZikrPrefs.VOLUME, false)
        if (on) startVolumeCapture() else stopVolumeCapture()
    }

    @Suppress("DEPRECATION")
    private fun startVolumeCapture() {
        if (session != null) return
        try {
            val s = MediaSession(this, "ZikrVolume")
            s.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            val provider = object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                override fun onAdjustVolume(direction: Int) {
                    handler.post { onVolumeKey(direction) }
                }
            }
            s.setPlaybackToRemote(provider)
            s.isActive = true
            session = s
            startSilentAudio()
        } catch (_: Exception) {
            stopVolumeCapture()
        }
    }

    private fun stopVolumeCapture() {
        silentOn = false
        audioThread = null
        try {
            session?.isActive = false
            session?.release()
        } catch (_: Exception) {
        }
        session = null
    }

    /** Tizim "ijro bo'layotgan" sessiyani tanlashi uchun jimjit ovoz. */
    private fun startSilentAudio() {
        if (silentOn) return
        silentOn = true
        val t = Thread {
            var track: AudioTrack? = null
            try {
                val sr = 8000
                val minBuf = AudioTrack.getMinBufferSize(
                    sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf.coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                val silence = ShortArray(1024)
                while (silentOn) {
                    track.write(silence, 0, silence.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                } catch (_: Exception) {
                }
                try {
                    track?.release()
                } catch (_: Exception) {
                }
            }
        }
        t.isDaemon = true
        audioThread = t
        t.start()
    }

    private fun onVolumeKey(direction: Int) {
        if (direction > 0) {
            val ev = ZikrPrefs.add(this, 1)
            ZikrPrefs.feedback(this, ev)
        } else if (direction < 0) {
            ZikrPrefs.add(this, -1)
            ZikrPrefs.feedback(this, ZikrPrefs.EVENT_NONE)
        }
    }

    // ---------- Suzuvchi doira ----------

    private fun currentSizeDp(): Int =
        ZikrPrefs.of(this).getInt(ZikrPrefs.SIZE, ZikrPrefs.DEFAULT_SIZE)
            .coerceIn(ZikrPrefs.MIN_SIZE, ZikrPrefs.MAX_SIZE)

    private fun sizePx(): Int = (currentSizeDp() * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun addOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val sizePx = sizePx()

        counterView = CounterView(this)
        counterView.sizeDp = currentSizeDp()
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
        val s = sizePx()
        lp.x = lp.x.coerceIn(0, (dm.widthPixels - s).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (dm.heightPixels - s).coerceAtLeast(0))
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
            ZikrPrefs.feedback(this, ZikrPrefs.EVENT_ROUND)
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
        val ev = ZikrPrefs.add(this, 1)
        ZikrPrefs.feedback(this, ev)
    }

    private fun refresh() {
        if (!::counterView.isInitialized) return
        val prefs = ZikrPrefs.of(this)
        val pal = ZikrPrefs.palette(this)
        val newSize = currentSizeDp()
        val sizeChanged = counterView.sizeDp != newSize

        counterView.sizeDp = newSize
        counterView.count = prefs.getInt(ZikrPrefs.COUNT, 0)
        counterView.target = prefs.getInt(ZikrPrefs.TARGET, 33).coerceAtLeast(1)
        counterView.today = ZikrPrefs.dayCount(this, 0)
        counterView.goal = ZikrPrefs.goal(this)
        counterView.setPalette(pal)

        if (sizeChanged && attached) {
            counterView.requestLayout()
            clampPosition()
            try {
                wm.updateViewLayout(counterView, lp)
            } catch (_: Exception) {
            }
        }
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

    // ---------- Doira ko'rinishi ----------

    private class CounterView(context: Context) : View(context) {
        var sizeDp = ZikrPrefs.DEFAULT_SIZE
        var count = 0
        var target = 33
        var today = 0
        var goal = ZikrPrefs.DEFAULT_GOAL

        private var colTop = Color.parseColor("#2DD4A8")
        private var colBottom = Color.parseColor("#0B7A66")
        private var colBorder = Color.parseColor("#6EE7C8")

        private val d = resources.displayMetrics.density
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }
        private val dayTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(60, 255, 255, 255)
        }
        private val dayArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#FBBF24")
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
            letterSpacing = 0.12f
        }
        private val rect = RectF()

        fun setPalette(p: ZikrPalette) {
            if (p.top == colTop && p.bottom == colBottom && p.border == colBorder) return
            colTop = p.top
            colBottom = p.bottom
            colBorder = p.border
            rebuildShader()
        }

        private fun rebuildShader() {
            val h = height.toFloat()
            if (h <= 0f) return
            fill.shader = LinearGradient(0f, 0f, 0f, h, colTop, colBottom, Shader.TileMode.CLAMP)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val s = (sizeDp * d).toInt()
            setMeasuredDimension(s, s)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            rebuildShader()
        }

        override fun onDraw(canvas: Canvas) {
            if (fill.shader == null) rebuildShader()
            val u = d * (sizeDp / BASE_SIZE_DP)
            val cx = width / 2f
            val cy = height / 2f
            val r = 31 * u

            // yumshoq soya
            shadow.color = Color.argb(16, 0, 0, 0)
            for (i in 1..4) {
                canvas.drawCircle(cx, cy + 2 * u, r + i * u, shadow)
            }

            // bugungi maqsad yo'li (tashqi yupqa yoy)
            val dr = r + 3.5f * u
            dayTrack.strokeWidth = 2.5f * u
            dayArc.strokeWidth = 2.5f * u
            rect.set(cx - dr, cy - dr, cx + dr, cy + dr)
            canvas.drawArc(rect, 0f, 360f, false, dayTrack)
            val dp = (today / goal.toFloat()).coerceIn(0f, 1f)
            if (dp > 0f) canvas.drawArc(rect, -90f, 360f * dp, false, dayArc)

            // asosiy doira
            canvas.drawCircle(cx, cy, r, fill)

            // hoshiya va davra sari to'lib boruvchi yoy
            border.strokeWidth = 3 * u
            border.color = colBorder
            arc.strokeWidth = 3 * u
            val rr = r - 1.5f * u
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
            } * u
            label.textSize = 9 * u
            val ny = (cy - 5 * u) - (number.descent() + number.ascent()) / 2f
            canvas.drawText(s, cx, ny, number)
            val ly = (cy + 15 * u) - (label.descent() + label.ascent()) / 2f
            canvas.drawText("ZIKR", cx, ly, label)
        }
    }
}
