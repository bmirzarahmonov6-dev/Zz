package uz.zikr.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val goalButtons = mutableListOf<Pair<Int, Button>>()
    private val swatches = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = ZikrPrefs.of(this)
        val (scroll, root) = Ui.page(this)

        root.addView(Ui.text(this, "Sozlamalar", 28f, Color.WHITE))

        // ---- Kunlik maqsad ----
        root.addView(title("Kunlik maqsad"), Ui.lp(this, 28))
        val goalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (g in listOf(100, 300, 500, 1000)) {
            val b = Ui.button(this, "$g", Ui.card) {
                prefs.edit().putInt(ZikrPrefs.GOAL, g).apply()
                refreshGoals()
            }
            goalButtons.add(g to b)
            val p = LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f)
            p.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0)
            goalRow.addView(b, p)
        }
        root.addView(goalRow, Ui.lp(this, 8))
        root.addView(
            Ui.text(this, "Maqsadga yetganda uzun tebranish bo'ladi", 12f, Ui.faint),
            Ui.lp(this, 6)
        )

        // ---- Doira o'lchami ----
        root.addView(title("Doira o'lchami"), Ui.lp(this, 28))
        val sizeNow = prefs.getInt(ZikrPrefs.SIZE, ZikrPrefs.DEFAULT_SIZE)
            .coerceIn(ZikrPrefs.MIN_SIZE, ZikrPrefs.MAX_SIZE)
        val sizeLabel = Ui.text(this, "$sizeNow dp", 14f, Ui.muted)
        val seek = SeekBar(this).apply {
            max = ZikrPrefs.MAX_SIZE - ZikrPrefs.MIN_SIZE
            progress = sizeNow - ZikrPrefs.MIN_SIZE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress + ZikrPrefs.MIN_SIZE
                    sizeLabel.text = "$v dp"
                    if (fromUser) prefs.edit().putInt(ZikrPrefs.SIZE, v).apply()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(seek, Ui.lp(this, 8))
        root.addView(sizeLabel, Ui.lp(this, 0))

        // ---- Doira rangi ----
        root.addView(title("Doira rangi"), Ui.lp(this, 28))
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, pal) in ZikrPrefs.palettes.withIndex()) {
            val v = View(this)
            v.setOnClickListener {
                prefs.edit().putInt(ZikrPrefs.COLOR, i).apply()
                refreshSwatches()
            }
            swatches.add(v)
            val p = LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f)
            p.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0)
            colorRow.addView(v, p)
        }
        root.addView(colorRow, Ui.lp(this, 8))

        // ---- Ovoz tugmasi ----
        root.addView(title("Ovoz tugmasi"), Ui.lp(this, 28))
        val sw = Switch(this).apply {
            text = "Ovoz tugmasi bilan sanash"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.getBoolean(ZikrPrefs.VOLUME, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(ZikrPrefs.VOLUME, checked).apply()
                if (checked && !prefs.getBoolean(ZikrPrefs.RUNNING, false)) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Ekran o'chiq paytida ishlashi uchun suzuvchi doira yoqilgan bo'lsin",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        root.addView(sw, Ui.lp(this, 8))
        root.addView(
            Ui.text(
                this,
                "Yuqori tugma: +1\nPast tugma: -1\nYoqilganda ovoz balandligi tugmalari ishlamaydi",
                12f, Ui.faint, Gravity.START
            ),
            Ui.lp(this, 6)
        )

        // ---- Vidjet ----
        root.addView(title("Vidjet"), Ui.lp(this, 28))
        root.addView(
            Ui.text(
                this,
                "Bosh ekrandagi bo'sh joyni uzoq bosing, \"Vidjetlar\"dan Zikr'ni tanlang. Vidjetni bossangiz +1 bo'ladi.",
                13f, Ui.muted, Gravity.START
            ),
            Ui.lp(this, 6)
        )

        root.addView(Ui.button(this, "Orqaga", Ui.card) { finish() }, Ui.lp(this, 32, Ui.dp(this, 48)))

        setContentView(scroll)
        refreshGoals()
        refreshSwatches()
    }

    private fun title(s: String): TextView = Ui.text(this, s, 16f, Color.WHITE, Gravity.START)

    private fun refreshGoals() {
        val g = ZikrPrefs.goal(this)
        for ((v, b) in goalButtons) {
            Ui.setButtonColor(b, if (v == g) Ui.accent else Ui.card)
        }
    }

    private fun refreshSwatches() {
        val sel = ZikrPrefs.of(this).getInt(ZikrPrefs.COLOR, 0)
            .coerceIn(0, ZikrPrefs.palettes.size - 1)
        for ((i, v) in swatches.withIndex()) {
            val pal = ZikrPrefs.palettes[i]
            v.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pal.top, pal.bottom)
            ).apply {
                cornerRadius = Ui.dp(this@SettingsActivity, 12).toFloat()
                if (i == sel) setStroke(Ui.dp(this@SettingsActivity, 3), Color.WHITE)
            }
        }
    }
}
