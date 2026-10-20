package com.example.clockwidget.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.clockwidget.ClockPrefs
import com.example.clockwidget.ClockUtils
import com.example.clockwidget.R
import kotlin.random.Random

/**
 * Đồng hồ kiểu Always-on Display:
 *  - nền đen hoàn toàn (tiết kiệm pin trên OLED), độ sáng thấp
 *  - hiện được trên màn hình khóa (showWhenLocked) và giữ màn hình luôn sáng
 *  - mỗi phút tự dịch chuyển vị trí vài pixel để tránh burn-in
 *  - chạm vào màn hình để thoát
 */
class AmbientClockActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: View
    private lateinit var tvBattery: TextView
    private lateinit var tvAlarm: TextView
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshInfo()
    }

    private val pixelShift = object : Runnable {
        override fun run() {
            val max = (24 * resources.displayMetrics.density).toInt()
            content.animate()
                .translationX(Random.nextInt(-max, max + 1).toFloat())
                .translationY(Random.nextInt(-max, max + 1).toFloat())
                .setDuration(2000)
                .start()
            handler.postDelayed(this, 60_000L)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hiển thị trên màn hình khóa
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.1f }

        setContentView(R.layout.activity_ambient)

        // Toàn màn hình, ẩn thanh trạng thái / điều hướng
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        content = findViewById(R.id.ambientContent)
        tvBattery = findViewById(R.id.ambientBattery)
        tvAlarm = findViewById(R.id.ambientAlarm)

        val fmt = ClockUtils.timeFormat(ClockPrefs.use24h(this), withAmPm = false)
        findViewById<TextClock>(R.id.ambientTime).apply {
            setTextColor(ClockPrefs.accentColor(this@AmbientClockActivity))
            format12Hour = fmt
            format24Hour = fmt
        }
        findViewById<View>(R.id.ambientRoot).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        refreshInfo()
        handler.post(pixelShift)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(pixelShift)
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
    }

    private fun refreshInfo() {
        tvBattery.text = ClockUtils.batteryText(this, ClockUtils.battery(this))
        val alarm = ClockUtils.nextAlarm(this)
        if (alarm != null) {
            tvAlarm.text = "⏰ $alarm"
            tvAlarm.visibility = View.VISIBLE
        } else {
            tvAlarm.visibility = View.GONE
        }
    }
}
