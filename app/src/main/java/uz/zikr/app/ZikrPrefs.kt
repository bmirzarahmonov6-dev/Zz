package uz.zikr.app

import android.content.Context
import android.content.SharedPreferences

object ZikrPrefs {
    const val FILE = "zikr"
    const val COUNT = "count"
    const val TARGET = "target"
    const val RUNNING = "running"
    const val POS_X = "pos_x"
    const val POS_Y = "pos_y"

    fun of(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
