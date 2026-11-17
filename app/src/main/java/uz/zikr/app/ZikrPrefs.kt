package uz.zikr.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.Calendar
import java.util.Locale

class ZikrPalette(val name: String, val top: Int, val bottom: Int, val border: Int)

object ZikrPrefs {
    const val FILE = "zikr"
    const val COUNT = "count"
    const val TARGET = "target"
    const val RUNNING = "running"
    const val POS_X = "pos_x"
    const val POS_Y = "pos_y"
    const val GOAL = "daily_goal"
    const val SIZE = "size_dp"
    const val COLOR = "color_idx"
    const val VOLUME = "volume_keys"

    const val EVENT_NONE = 0
    const val EVENT_ROUND = 1
    const val EVENT_GOAL = 2

    const val DEFAULT_GOAL = 300
    const val DEFAULT_SIZE = 76
    const val MIN_SIZE = 52
    const val MAX_SIZE = 120

    val palettes: List<ZikrPalette> = listOf(
        ZikrPalette("Yashil", Color.parseColor("#2DD4A8"), Color.parseColor("#0B7A66"), Color.parseColor("#6EE7C8")),
        ZikrPalette("Ko'k", Color.parseColor("#38BDF8"), Color.parseColor("#1D4ED8"), Color.parseColor("#7DD3FC")),
        ZikrPalette("Binafsha", Color.parseColor("#A78BFA"), Color.parseColor("#6D28D9"), Color.parseColor("#C4B5FD")),
        ZikrPalette("Oltin", Color.parseColor("#FBBF24"), Color.parseColor("#B45309"), Color.parseColor("#FDE68A")),
        ZikrPalette("Pushti", Color.parseColor("#F472B6"), Color.parseColor("#BE185D"), Color.parseColor("#FBCFE8")),
        ZikrPalette("Kulrang", Color.parseColor("#64748B"), Color.parseColor("#0F172A"), Color.parseColor("#94A3B8"))
    )

    fun of(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun palette(ctx: Context): ZikrPalette {
        val i = of(ctx).getInt(COLOR, 0).coerceIn(0, palettes.size - 1)
        return palettes[i]
    }

    fun goal(ctx: Context): Int = of(ctx).getInt(GOAL, DEFAULT_GOAL).coerceAtLeast(1)

    // ---------- Kunlik statistika ----------

    /** offset = 0 bugun, 1 kecha, ... */
    fun dayKey(offset: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -offset)
        return String.format(
            Locale.US, "d_%04d%02d%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun dayCount(ctx: Context, offset: Int): Int = of(ctx).getInt(dayKey(offset), 0)

    fun sumDays(ctx: Context, days: Int): Int {
        var s = 0
        for (i in 0 until days) s += dayCount(ctx, i)
        return s
    }

    fun totalAll(ctx: Context): Int {
        var s = 0
        for ((k, v) in of(ctx).all) {
            if (k.startsWith("d_") && v is Int) s += v
        }
        return s
    }

    /** Ketma-ket maqsad bajarilgan kunlar. Bugun hali yetmagan bo'lsa, kechadan sanaladi. */
    fun streak(ctx: Context): Int {
        val g = goal(ctx)
        var i = if (dayCount(ctx, 0) >= g) 0 else 1
        var n = 0
        while (i < 3650 && dayCount(ctx, i) >= g) {
            n++
            i++
        }
        return n
    }

    // ---------- Hisoblash ----------

    /** delta: +1 yoki -1. Hodisa kodini qaytaradi (davra yoki maqsad tugadimi). */
    @Synchronized
    fun add(ctx: Context, delta: Int): Int {
        val p = of(ctx)
        val target = p.getInt(TARGET, 33).coerceAtLeast(1)
        val g = goal(ctx)
        val oldCount = p.getInt(COUNT, 0)
        val newCount = (oldCount + delta).coerceAtLeast(0)
        val key = dayKey(0)
        val oldDay = p.getInt(key, 0)
        val newDay = (oldDay + (newCount - oldCount)).coerceAtLeast(0)
        p.edit().putInt(COUNT, newCount).putInt(key, newDay).apply()
        ZikrWidget.updateAll(ctx)

        var ev = EVENT_NONE
        if (delta > 0) {
            if (newDay == g && oldDay < g) {
                ev = EVENT_GOAL
            } else if (newCount > 0 && newCount % target == 0) {
                ev = EVENT_ROUND
            }
        }
        return ev
    }

    @Suppress("DEPRECATION")
    fun feedback(ctx: Context, event: Int) {
        val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            val effect = when (event) {
                EVENT_GOAL -> VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 120, 200, 120, 400), -1
                )
                EVENT_ROUND -> VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                else -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vib.vibrate(effect)
        } else {
            when (event) {
                EVENT_GOAL -> vib.vibrate(longArrayOf(0, 200, 120, 200, 120, 400), -1)
                EVENT_ROUND -> vib.vibrate(300)
                else -> vib.vibrate(20)
            }
        }
    }
}
