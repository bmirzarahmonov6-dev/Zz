package uz.zikr.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Bosh ekrandagi vidjet: bosilsa +1. */
class ZikrWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, build(context))
        }
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val p = ZikrPrefs.of(ctx)
            val count = p.getInt(ZikrPrefs.COUNT, 0)
            val today = ZikrPrefs.dayCount(ctx, 0)
            val goal = ZikrPrefs.goal(ctx)

            val views = RemoteViews(ctx.packageName, R.layout.widget_zikr)
            views.setTextViewText(R.id.widget_count, count.toString())
            views.setTextViewText(R.id.widget_today, "Bugun $today / $goal")

            val pi = PendingIntent.getBroadcast(
                ctx, 10,
                Intent(ctx, ZikrReceiver::class.java).setAction(ZikrReceiver.ACTION_PLUS),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            return views
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ZikrWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            for (id in ids) mgr.updateAppWidget(id, views)
        }
    }
}
