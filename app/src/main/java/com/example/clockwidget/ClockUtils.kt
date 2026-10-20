package com.example.clockwidget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.annotation.StringRes
import java.util.Calendar
import java.util.Date

data class BatteryState(val percent: Int, val charging: Boolean) {
    val isLow: Boolean get() = percent <= 15 && !charging
}

object ClockUtils {

    /** Đọc pin hiện tại (sticky broadcast, không cần đăng ký receiver). */
    fun battery(context: Context): BatteryState {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
        return BatteryState(percent, charging)
    }

    fun batteryText(context: Context, b: BatteryState): String {
        val icon = if (b.charging) "⚡" else "🔋"
        val low = if (b.isLow) " · " + context.getString(R.string.battery_low) else ""
        return "$icon ${b.percent}%$low"
    }

    /** Giờ báo thức kế tiếp, hoặc null nếu chưa đặt. */
    fun nextAlarm(context: Context): String? {
        val am = context.getSystemService(AlarmManager::class.java) ?: return null
        val info = am.nextAlarmClock ?: return null
        return DateFormat.getTimeFormat(context).format(Date(info.triggerTime))
    }

    @StringRes
    fun greetingRes(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..10 -> R.string.greet_morning
        in 11..12 -> R.string.greet_noon
        in 13..17 -> R.string.greet_afternoon
        else -> R.string.greet_evening
    }

    fun timeFormat(use24h: Boolean, withAmPm: Boolean = true): String = when {
        use24h -> "HH:mm"
        withAmPm -> "h:mm a"
        else -> "h:mm"
    }
}
