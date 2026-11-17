package uz.zikr.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

class StatsActivity : Activity() {

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, root) = Ui.page(this)
        content = root
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun statCard(value: String, label: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@StatsActivity, 8), Ui.dp(this@StatsActivity, 16), Ui.dp(this@StatsActivity, 8), Ui.dp(this@StatsActivity, 16))
            background = GradientDrawable().apply {
                setColor(Ui.card)
                cornerRadius = Ui.dp(this@StatsActivity, 14).toFloat()
            }
        }
        box.addView(Ui.text(this, value, 24f, Color.WHITE))
        box.addView(Ui.text(this, label, 12f, Ui.muted))
        return box
    }

    private fun row(a: LinearLayout, b: LinearLayout, top: Int) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in listOf(a, b)) {
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            p.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0)
            r.addView(c, p)
        }
        content.addView(r, Ui.lp(this, top))
    }

    private fun build() {
        content.removeAllViews()

        val today = ZikrPrefs.dayCount(this, 0)
        val week = ZikrPrefs.sumDays(this, 7)
        val month = ZikrPrefs.sumDays(this, 30)
        val total = ZikrPrefs.totalAll(this)
        val streak = ZikrPrefs.streak(this)
        val goal = ZikrPrefs.goal(this)

        content.addView(Ui.text(this, "Statistika", 28f, Color.WHITE))
        content.addView(Ui.text(this, "Kunlik maqsad: $goal", 14f, Ui.muted), Ui.lp(this, 4))

        row(statCard(today.toString(), "Bugun"), statCard(week.toString(), "7 kun"), 24)
        row(statCard(month.toString(), "30 kun"), statCard(total.toString(), "Jami"), 8)
        row(
            statCard("$streak", "Ketma-ket maqsad kunlari"),
            statCard((week / 7).toString(), "O'rtacha (kuniga, 7 kun)"),
            8
        )

        content.addView(Ui.text(this, "Oxirgi 7 kun", 16f, Color.WHITE), Ui.lp(this, 28))

        val names = arrayOf("Ya", "Du", "Se", "Ch", "Pa", "Ju", "Sh")
        val values = IntArray(7)
        val labels = Array(7) { "" }
        for (i in 0 until 7) {
            val offset = 6 - i
            values[i] = ZikrPrefs.dayCount(this, offset)
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -offset)
            labels[i] = names[c.get(Calendar.DAY_OF_WEEK) - 1]
        }
        content.addView(BarChartView(this, values, labels, goal), Ui.lp(this, 8, Ui.dp(this, 220)))

        content.addView(Ui.button(this, "Orqaga", Ui.card) { finish() }, Ui.lp(this, 28, Ui.dp(this, 48)))
    }

    private class BarChartView(
        context: Context,
        private val values: IntArray,
        private val labels: Array<String>,
        private val goal: Int
    ) : View(context) {
        private val d = resources.displayMetrics.density
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val goalLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
            color = Color.parseColor("#FBBF24")
            pathEffect = DashPathEffect(floatArrayOf(10f * d, 6f * d), 0f)
        }
        private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 11f * d
        }
        private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textAlign = Paint.Align.CENTER
            textSize = 12f * d
            typeface = Typeface.DEFAULT_BOLD
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val bottomPad = 24f * d
            val topPad = 20f * d
            val chartH = h - bottomPad - topPad
            val n = values.size
            val slot = w / n
            val barW = slot * 0.56f
            val maxV = maxOf(values.maxOrNull() ?: 0, goal, 1)

            // maqsad chizig'i
            val gy = topPad + chartH * (1f - goal / maxV.toFloat())
            canvas.drawLine(0f, gy, w, gy, goalLine)

            for (i in 0 until n) {
                val cx = slot * i + slot / 2f
                val bh = chartH * (values[i] / maxV.toFloat())
                val top = topPad + chartH - bh
                bar.color = if (values[i] >= goal) Color.parseColor("#2DD4A8")
                else Color.parseColor("#0F766E")
                rect.set(cx - barW / 2f, top, cx + barW / 2f, topPad + chartH)
                canvas.drawRoundRect(rect, 6f * d, 6f * d, bar)
                if (values[i] > 0) {
                    canvas.drawText(values[i].toString(), cx, top - 4f * d, valueText)
                }
                canvas.drawText(labels[i], cx, h - 6f * d, labelText)
            }
        }
    }
}
