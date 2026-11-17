package uz.zikr.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Barcha ekranlar uchun umumiy kichik yordamchilar. */
object Ui {
    val bg = Color.parseColor("#0B1220")
    val card = Color.parseColor("#1B2638")
    val accent = Color.parseColor("#0F766E")
    val danger = Color.parseColor("#7F1D1D")
    val muted = Color.parseColor("#94A3B8")
    val faint = Color.parseColor("#64748B")
    val gold = Color.parseColor("#FBBF24")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun page(ctx: Context): Pair<ScrollView, LinearLayout> {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 24), dp(ctx, 40), dp(ctx, 24), dp(ctx, 32))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return Pair(scroll, root)
    }

    fun lp(ctx: Context, top: Int, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        p.topMargin = dp(ctx, top)
        return p
    }

    fun text(ctx: Context, s: String, size: Float, color: Int, gravity: Int = Gravity.CENTER): TextView =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            this.gravity = gravity
        }

    fun button(ctx: Context, label: String, color: Int, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(ctx, 14).toFloat()
            }
            setOnClickListener { onClick() }
        }

    fun setButtonColor(b: Button, color: Int) {
        (b.background as GradientDrawable).setColor(color)
    }
}
