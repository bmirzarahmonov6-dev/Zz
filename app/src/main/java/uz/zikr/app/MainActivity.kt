package uz.zikr.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var countText: TextView
    private lateinit var roundText: TextView
    private lateinit var statusText: TextView
    private lateinit var todayText: TextView
    private lateinit var dayBar: ProgressBar
    private lateinit var toggleBtn: Button
    private val targetButtons = mutableListOf<Pair<Int, Button>>()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        ZikrPrefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    override fun onPause() {
        ZikrPrefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    // Ilova ochiq paytida ham ovoz tugmasi bilan sanash
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (ZikrPrefs.of(this).getBoolean(ZikrPrefs.VOLUME, false)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if ((event?.repeatCount ?: 0) == 0) {
                    ZikrPrefs.feedback(this, ZikrPrefs.add(this, 1))
                }
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if ((event?.repeatCount ?: 0) == 0) {
                    ZikrPrefs.add(this, -1)
                    ZikrPrefs.feedback(this, ZikrPrefs.EVENT_NONE)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- UI ----------

    private fun buildUi() {
        val (scroll, root) = Ui.page(this)

        root.addView(Ui.text(this, "Zikr", 28f, Color.WHITE))

        statusText = Ui.text(this, "", 14f, Ui.muted)
        root.addView(statusText, Ui.lp(this, 4))

        countText = Ui.text(this, "0", 84f, Color.WHITE)
        countText.setOnClickListener {
            ZikrPrefs.feedback(this, ZikrPrefs.add(this, 1))
        }
        root.addView(countText, Ui.lp(this, 24))

        roundText = Ui.text(this, "", 16f, Ui.muted)
        root.addView(roundText, Ui.lp(this, 0))

        // Bugungi tasbeh yo'li
        todayText = Ui.text(this, "", 14f, Ui.gold)
        root.addView(todayText, Ui.lp(this, 24))

        dayBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Ui.gold)
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#334155"))
        }
        root.addView(dayBar, Ui.lp(this, 6, Ui.dp(this, 12)))

        toggleBtn = Ui.button(this, "", Ui.accent) { toggleOverlay() }
        root.addView(toggleBtn, Ui.lp(this, 28, Ui.dp(this, 56)))

        // Davra uzunligi
        val targets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in listOf(33, 99, 100)) {
            val b = Ui.button(this, "$t", Ui.card) {
                ZikrPrefs.of(this).edit().putInt(ZikrPrefs.TARGET, t).apply()
                refresh()
            }
            targetButtons.add(t to b)
            val p = LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f)
            p.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0)
            targets.addView(b, p)
        }
        root.addView(targets, Ui.lp(this, 16))

        // Statistika va sozlamalar
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val stats = Ui.button(this, "Statistika", Ui.card) {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        val sets = Ui.button(this, "Sozlamalar", Ui.card) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        for (b in listOf(stats, sets)) {
            val p = LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f)
            p.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0)
            nav.addView(b, p)
        }
        root.addView(nav, Ui.lp(this, 12))

        root.addView(Ui.button(this, "Nolga qaytarish", Ui.danger) {
            ZikrPrefs.of(this).edit().putInt(ZikrPrefs.COUNT, 0).apply()
            refresh()
        }, Ui.lp(this, 12, Ui.dp(this, 48)))

        root.addView(
            Ui.text(
                this,
                "Doirani bosing: +1\nSurib joyini o'zgartiring\nUzoq bosing: shu oynani ochadi",
                13f, Ui.faint
            ),
            Ui.lp(this, 24)
        )

        setContentView(scroll)
    }

    // ---------- Holat ----------

    private fun refresh() {
        val prefs = ZikrPrefs.of(this)
        val count = prefs.getInt(ZikrPrefs.COUNT, 0)
        val target = prefs.getInt(ZikrPrefs.TARGET, 33).coerceAtLeast(1)
        val running = prefs.getBoolean(ZikrPrefs.RUNNING, false)
        val canDraw = Settings.canDrawOverlays(this)
        val today = ZikrPrefs.dayCount(this, 0)
        val goal = ZikrPrefs.goal(this)
        val streak = ZikrPrefs.streak(this)

        countText.text = count.toString()
        roundText.text = "${count / target} davra  ·  ${count % target}/$target"

        val pct = (today * 100L / goal).toInt().coerceAtMost(100)
        var line = "Bugun: $today / $goal  ·  $pct%"
        if (streak > 0) line += "  ·  $streak kun ketma-ket"
        todayText.text = line
        dayBar.progress = (today * 1000L / goal).toInt().coerceIn(0, 1000)

        statusText.text = when {
            !canDraw -> "Ruxsat kerak: boshqa ilovalar ustida ko'rsatish"
            running -> "Suzuvchi doira yoqilgan"
            else -> "Suzuvchi doira o'chiq"
        }
        toggleBtn.text = if (running) "Doirani o'chirish" else "Doirani yoqish"
        Ui.setButtonColor(toggleBtn, if (running) Ui.danger else Ui.accent)

        for ((t, b) in targetButtons) {
            Ui.setButtonColor(b, if (t == target) Ui.accent else Ui.card)
        }
    }

    private fun toggleOverlay() {
        val prefs = ZikrPrefs.of(this)
        if (prefs.getBoolean(ZikrPrefs.RUNNING, false)) {
            stopService(Intent(this, FloatingService::class.java))
            prefs.edit().putBoolean(ZikrPrefs.RUNNING, false).apply()
            refresh()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Ruxsatni yoqing, keyin qaytib \"Doirani yoqish\"ni bosing",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        val svc = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
    }
}
