package com.example.clockwidget.floating

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextClock
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.clockwidget.ClockPrefs
import com.example.clockwidget.ClockUtils
import com.example.clockwidget.MainActivity
import com.example.clockwidget.R
import com.example.clockwidget.tile.FloatingClockTileService
import kotlin.math.abs

/**
 * Widget nổi: đồng hồ hiển thị trên mọi ứng dụng (TYPE_APPLICATION_OVERLAY).
 * Kéo để di chuyển, chạm để mở rộng/thu gọn, bấm ✕ để đóng.
 * Cần quyền "Hiển thị trên các ứng dụng khác" (SYSTEM_ALERT_WINDOW).
 */
class FloatingClockService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.clockwidget.ACTION_STOP_FLOATING"
        private const val CHANNEL_ID = "floating_clock"
        private const val NOTIF_ID = 1001

        /** Trạng thái theo tiến trình; tile và MainActivity đọc giá trị này. */
        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FloatingClockService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingClockService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private var root: View? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateBattery()
    }
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyStyle() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ClockPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        notifyTile()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) addOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        try { unregisterReceiver(batteryReceiver) } catch (e: IllegalArgumentException) { }
        ClockPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        root?.let { try { wm.removeView(it) } catch (e: IllegalArgumentException) { } }
        root = null
        notifyTile()
        super.onDestroy()
    }

    // ---------- Foreground notification ----------
    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, FloatingClockService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_float)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ---------- Overlay ----------
    @SuppressLint("InflateParams")
    private fun addOverlay() {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_clock, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(120)
        }
        view.findViewById<View>(R.id.floatClose).setOnClickListener { stopSelf() }
        attachDragAndTap(view, lp)
        wm.addView(view, lp)
        root = view
        applyStyle()
        updateBattery()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragAndTap(view: View, lp: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = (startX + dx).coerceAtLeast(0)
                        lp.y = (startY + dy).coerceAtLeast(0)
                        wm.updateViewLayout(view, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        v.performClick()
                        toggleExtra()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleExtra() {
        val extra = root?.findViewById<View>(R.id.floatExtra) ?: return
        extra.visibility = if (extra.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun applyStyle() {
        val v = root ?: return
        val fmt = ClockUtils.timeFormat(ClockPrefs.use24h(this))
        v.findViewById<TextClock>(R.id.floatTime).apply {
            setTextColor(ClockPrefs.accentColor(this@FloatingClockService))
            format12Hour = fmt
            format24Hour = fmt
        }
    }

    private fun updateBattery() {
        root?.findViewById<TextView>(R.id.floatBattery)?.text =
            ClockUtils.batteryText(this, ClockUtils.battery(this))
    }

    private fun notifyTile() {
        TileService.requestListeningState(this, ComponentName(this, FloatingClockTileService::class.java))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
