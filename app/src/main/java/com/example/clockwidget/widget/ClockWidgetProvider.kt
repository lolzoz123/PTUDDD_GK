package com.example.clockwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.example.clockwidget.ClockPrefs
import com.example.clockwidget.ClockUtils
import com.example.clockwidget.MainActivity
import com.example.clockwidget.R

/**
 * Widget thích ứng (Adaptive):
 *  - THÍCH ỨNG THEO MÀN HÌNH: 3 layout (nhỏ 2x1, vừa 3x2, lớn 4x3). Android 12+ nhận một
 *    Map<SizeF, RemoteViews> và tự chọn layout vừa với kích thước widget; Android cũ hơn chọn
 *    theo OPTION_APPWIDGET_MIN_WIDTH/HEIGHT.
 *  - THÍCH ỨNG THEO DỮ LIỆU: lời chào đổi theo giờ, pin yếu thì đổi sang màu đỏ, hiện biểu tượng
 *    sạc, chip báo thức chỉ hiện khi có báo thức kế tiếp.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
        scheduleRefresh(context)
    }

    // Người dùng kéo giãn widget -> dựng lại để chọn layout phù hợp (Android < 12)
    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        update(context, manager, appWidgetId)
    }

    override fun onEnabled(context: Context) = scheduleRefresh(context)

    override fun onDisabled(context: Context) = cancelRefresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.clockwidget.ACTION_WIDGET_REFRESH"
        private const val REFRESH_INTERVAL = AlarmManager.INTERVAL_FIFTEEN_MINUTES
        private val COLOR_LOW = Color.parseColor("#EF5350")

        /** Gọi khi người dùng đổi màu/định dạng giờ trong app. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val small = build(context, R.layout.widget_small)
            val medium = build(context, R.layout.widget_medium)
            val large = build(context, R.layout.widget_large)

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(
                    mapOf(
                        SizeF(110f, 40f) to small,
                        SizeF(180f, 110f) to medium,
                        SizeF(250f, 180f) to large
                    )
                )
            } else {
                val o = manager.getAppWidgetOptions(id)
                val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                when {
                    w >= 250 && h >= 180 -> large
                    w >= 180 && h >= 110 -> medium
                    else -> small
                }
            }
            manager.updateAppWidget(id, views)
        }

        private fun build(context: Context, layout: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, layout)
            val accent = ClockPrefs.accentColor(context)

            // Định dạng giờ theo cài đặt của người dùng
            val fmt = ClockUtils.timeFormat(
                ClockPrefs.use24h(context), withAmPm = layout != R.layout.widget_small
            )
            rv.setCharSequence(R.id.time, "setFormat12Hour", fmt)
            rv.setCharSequence(R.id.time, "setFormat24Hour", fmt)
            rv.setTextColor(R.id.time, accent)

            rv.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            if (layout == R.layout.widget_small) return rv

            // ----- Dữ liệu động (layout vừa & lớn) -----
            val battery = ClockUtils.battery(context)
            rv.setTextViewText(R.id.greeting, context.getString(ClockUtils.greetingRes()))
            rv.setTextColor(R.id.greeting, accent)
            rv.setTextViewText(R.id.battery, ClockUtils.batteryText(context, battery))
            rv.setTextColor(R.id.battery, if (battery.isLow) COLOR_LOW else Color.WHITE)

            if (layout == R.layout.widget_large) {
                val alarm = ClockUtils.nextAlarm(context)
                if (alarm != null) {
                    rv.setViewVisibility(R.id.alarm, View.VISIBLE)
                    rv.setTextViewText(R.id.alarm, "⏰ $alarm")
                    rv.setOnClickPendingIntent(
                        R.id.alarm,
                        PendingIntent.getActivity(
                            context, 1,
                            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                } else {
                    rv.setViewVisibility(R.id.alarm, View.GONE)
                }
            }
            return rv
        }

        // ----- Làm mới định kỳ (pin, báo thức, lời chào) mỗi ~15 phút, không đánh thức máy -----
        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun scheduleRefresh(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            am.setInexactRepeating(
                AlarmManager.RTC, System.currentTimeMillis() + REFRESH_INTERVAL,
                REFRESH_INTERVAL, refreshIntent(context)
            )
        }

        private fun cancelRefresh(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
        }
    }
}
