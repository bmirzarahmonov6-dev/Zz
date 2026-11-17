package uz.zikr.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bgColor = Color.parseColor("#0B1220")
    private val cardColor = Color.parseColor("#1B2638")
    private val accent = Color.parseColor("#0F766E")
    private val danger = Color.parseColor("#7F1D1D")

    private lateinit var countText: TextView
    private lateinit var roundText: TextView
    private lateinit var statusText: TextView
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

    // ---------- UI ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(24), dp(48), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = "Zikr"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        })

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        root.addView(statusText, lp(top = 4))

        countText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 84f
            gravity = Gravity.CENTER
        }
        root.addView(countText, lp(top = 32))

        roundText = TextView(this).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 16f
            gravity = Gravity.CENTER
        }
        root.addView(roundText, lp(top = 0))

        toggleBtn = styledButton("", accent) { toggleOverlay() }
        root.addView(toggleBtn, lp(top = 40, height = dp(56)))

        // Maqsad tanlash
        val targets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in listOf(33, 99, 100)) {
            val b = styledButton("$t", cardColor) {
                ZikrPrefs.of(this).edit().putInt(ZikrPrefs.TARGET, t).apply()
                refresh()
            }
            targetButtons.add(t to b)
            val p = LinearLayout.LayoutParams(0, dp(48), 1f)
            p.setMargins(dp(4), 0, dp(4), 0)
            targets.addView(b, p)
        }
        root.addView(targets, lp(top = 16))

        root.addView(styledButton("Nolga qaytarish", danger) {
            ZikrPrefs.of(this).edit().putInt(ZikrPrefs.COUNT, 0).apply()
            refresh()
        }, lp(top = 16, height = dp(48)))

        root.addView(TextView(this).apply {
            text = "Doirani bosing: +1\nSurib joyini o'zgartiring\nUzoq bosing: shu oynani ochadi"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 13f
            gravity = Gravity.CENTER
        }, lp(top = 24))

        setContentView(root)
    }

    private fun lp(top: Int, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        p.topMargin = dp(top)
        return p
    }

    private fun styledButton(label: String, color: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { onClick() }
        }

    private fun setButtonColor(b: Button, color: Int) {
        (b.background as GradientDrawable).setColor(color)
    }

    // ---------- Holat ----------

    private fun refresh() {
        val prefs = ZikrPrefs.of(this)
        val count = prefs.getInt(ZikrPrefs.COUNT, 0)
        val target = prefs.getInt(ZikrPrefs.TARGET, 33).coerceAtLeast(1)
        val running = prefs.getBoolean(ZikrPrefs.RUNNING, false)
        val canDraw = Settings.canDrawOverlays(this)

        countText.text = count.toString()
        roundText.text = "${count / target} davra  ·  ${count % target}/$target"

        statusText.text = when {
            !canDraw -> "Ruxsat kerak: boshqa ilovalar ustida ko'rsatish"
            running -> "Suzuvchi doira yoqilgan"
            else -> "Suzuvchi doira o'chiq"
        }
        toggleBtn.text = if (running) "Doirani o'chirish" else "Doirani yoqish"
        setButtonColor(toggleBtn, if (running) danger else accent)

        for ((t, b) in targetButtons) {
            setButtonColor(b, if (t == target) accent else cardColor)
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
