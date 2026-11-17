package uz.zikr.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Bildirishnoma va vidjetdagi "+1" tugmalari shu yerga keladi. */
class ZikrReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PLUS = "uz.zikr.app.PLUS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PLUS) {
            val ev = ZikrPrefs.add(context, 1)
            ZikrPrefs.feedback(context, ev)
        }
    }
}
