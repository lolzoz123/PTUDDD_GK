package com.example.clockwidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/** Cài đặt dùng chung cho cả 5 tính năng (hình nền, tile, AOD, widget nổi, widget). */
object ClockPrefs {
    private const val FILE = "clock_prefs"
    private const val KEY_ACCENT = "accent_index"
    private const val KEY_24H = "use_24h"
    private const val KEY_SECONDS = "show_seconds"

    val ACCENTS: IntArray = intArrayOf(
        Color.parseColor("#4FC3F7"), // xanh dương
        Color.parseColor("#81C784"), // xanh lá
        Color.parseColor("#FFB74D"), // cam
        Color.parseColor("#F06292"), // hồng
        Color.parseColor("#BA68C8")  // tím
    )

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun accentIndex(context: Context): Int =
        prefs(context).getInt(KEY_ACCENT, 0).coerceIn(ACCENTS.indices)

    fun accentColor(context: Context): Int = ACCENTS[accentIndex(context)]

    fun setAccentIndex(context: Context, index: Int) =
        prefs(context).edit().putInt(KEY_ACCENT, index).apply()

    fun use24h(context: Context): Boolean = prefs(context).getBoolean(KEY_24H, true)

    fun setUse24h(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_24H, value).apply()

    fun showSeconds(context: Context): Boolean = prefs(context).getBoolean(KEY_SECONDS, true)

    fun setShowSeconds(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SECONDS, value).apply()
}
