package com.example.clockwidget.wallpaper

import android.animation.ArgbEvaluator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.example.clockwidget.BatteryState
import com.example.clockwidget.ClockPrefs
import com.example.clockwidget.ClockUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Star(val x: Float, var y: Float, val speed: Float, val size: Float, val phase: Float)

// Màu bầu trời tại 0h, 6h, 12h, 18h, 24h
private val SKY_TOP = intArrayOf(
    Color.parseColor("#0B1026"), Color.parseColor("#3B3A6D"), Color.parseColor("#1E6FD9"),
    Color.parseColor("#4A2C6D"), Color.parseColor("#0B1026")
)
private val SKY_BOTTOM = intArrayOf(
    Color.parseColor("#1B2A4A"), Color.parseColor("#F2A65A"), Color.parseColor("#8FD3F4"),
    Color.parseColor("#E8765A"), Color.parseColor("#1B2A4A")
)
private val COLOR_LOW = Color.parseColor("#EF5350")

/**
 * Live Wallpaper: đồng hồ analog chuyển động mượt (kim giây quét liên tục),
 * nền đổi màu theo giờ, sao lấp lánh trôi lên, vòng pin cập nhật theo dữ liệu thật.
 */
class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    private inner class ClockEngine : Engine() {
        private val ctx: Context get() = this@ClockWallpaperService
        private val handler = Handler(Looper.getMainLooper())
        private val cal = Calendar.getInstance()
        private val evaluator = ArgbEvaluator()
        private val rnd = Random(7)
        private val stars = Array(50) {
            Star(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                speed = 0.004f + rnd.nextFloat() * 0.012f,
                size = 1f + rnd.nextFloat() * 2f,
                phase = rnd.nextFloat() * 6.28f
            )
        }
        private val oval = RectF()

        private val bgPaint = Paint()
        private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; strokeCap = Paint.Cap.ROUND
        }
        private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; color = Color.WHITE
        }

        private var visible = false
        private var w = 0
        private var h = 0
        private var battery = BatteryState(0, false)
        private var batteryStr = ""
        private var accent = Color.CYAN
        private var showSeconds = true
        private var timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFmt = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        private var timeStr = ""
        private var dateStr = ""
        private var cacheKey = -1L
        private var lastFrame = 0L

        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = updateBattery()
        }
        private val prefsListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> loadPrefs() }

        private val frame = object : Runnable {
            override fun run() {
                drawFrame()
                handler.removeCallbacks(this)
                if (visible) handler.postDelayed(this, if (showSeconds) 33L else 1000L)
            }
        }

        // ---------- vòng đời ----------
        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loadPrefs()
            updateBattery()
            ContextCompat.registerReceiver(
                ctx, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ClockPrefs.prefs(ctx).registerOnSharedPreferenceChangeListener(prefsListener)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height
            cacheKey = -1L // buộc dựng lại gradient theo kích thước mới
            drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                loadPrefs(); updateBattery()
                lastFrame = 0L
                handler.post(frame)
            } else {
                handler.removeCallbacks(frame)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(frame)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(frame)
            try { ctx.unregisterReceiver(batteryReceiver) } catch (e: IllegalArgumentException) { }
            ClockPrefs.prefs(ctx).unregisterOnSharedPreferenceChangeListener(prefsListener)
        }

        // ---------- dữ liệu ----------
        private fun loadPrefs() {
            accent = ClockPrefs.accentColor(ctx)
            showSeconds = ClockPrefs.showSeconds(ctx)
            timeFmt = SimpleDateFormat(ClockUtils.timeFormat(ClockPrefs.use24h(ctx)), Locale.getDefault())
            cacheKey = -1L
        }

        private fun updateBattery() {
            battery = ClockUtils.battery(ctx)
            batteryStr = ClockUtils.batteryText(ctx, battery)
        }

        /** Chỉ dựng lại chuỗi giờ/ngày và gradient khi sang phút mới. */
        private fun rebuildCache(now: Long) {
            val tz = TimeZone.getDefault()
            cal.timeZone = tz
            cal.timeInMillis = now
            timeFmt.timeZone = tz
            dateFmt.timeZone = tz
            val d = Date(now)
            timeStr = timeFmt.format(d)
            dateStr = dateFmt.format(d)

            val hourFraction = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
            val t = hourFraction / 6f
            val i = t.toInt().coerceIn(0, 3)
            val f = t - i
            val top = evaluator.evaluate(f, SKY_TOP[i], SKY_TOP[i + 1]) as Int
            val bottom = evaluator.evaluate(f, SKY_BOTTOM[i], SKY_BOTTOM[i + 1]) as Int
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(), top, bottom, android.graphics.Shader.TileMode.CLAMP
            )
        }

        // ---------- vẽ ----------
        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                render(canvas)
            } catch (e: Exception) {
                // Surface có thể bị huỷ giữa chừng, bỏ qua frame này
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
                }
            }
        }

        private fun render(c: Canvas) {
            if (w == 0 || h == 0) return
            val now = System.currentTimeMillis()
            val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.1f)
            lastFrame = now

            val key = now / 60_000L
            if (key != cacheKey) { cacheKey = key; rebuildCache(now) }
            cal.timeInMillis = now

            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            drawStars(c, dt, now)

            val portrait = h >= w
            val r = if (portrait) w * 0.30f else h * 0.32f
            val cx = if (portrait) w / 2f else w * 0.30f
            val cy = if (portrait) h * 0.36f else h / 2f

            val ms = cal.get(Calendar.MILLISECOND)
            val sec = cal.get(Calendar.SECOND) + if (showSeconds) ms / 1000f else 0f
            val min = cal.get(Calendar.MINUTE) + sec / 60f
            val hour = (cal.get(Calendar.HOUR) % 12) + min / 60f
            drawClock(c, cx, cy, r, hour, min, sec)

            // Giờ số + ngày + pin
            val tx = if (portrait) w / 2f else w * 0.68f
            val ty = if (portrait) cy + r + r * 0.75f else h * 0.46f
            textPaint.textSize = r * 0.55f
            textPaint.color = Color.WHITE
            c.drawText(timeStr, tx, ty, textPaint)
            textPaint.textSize = r * 0.17f
            textPaint.color = 0xCCFFFFFF.toInt()
            c.drawText(dateStr, tx, ty + r * 0.30f, textPaint)
            textPaint.color = if (battery.isLow) COLOR_LOW else accent
            c.drawText(batteryStr, tx, ty + r * 0.55f, textPaint)
        }

        private fun drawStars(c: Canvas, dt: Float, now: Long) {
            val t = now / 1000f
            val density = ctx.resources.displayMetrics.density
            for (s in stars) {
                s.y -= s.speed * dt
                if (s.y < 0f) s.y += 1f
                starPaint.alpha = (90 + 80 * sin(t * 1.5f + s.phase)).toInt().coerceIn(0, 255)
                c.drawCircle(s.x * w, s.y * h, s.size * density, starPaint)
            }
        }

        private fun drawClock(
            c: Canvas, cx: Float, cy: Float, r: Float,
            hour: Float, min: Float, sec: Float
        ) {
            // Vòng pin: nền mờ + cung tiến độ theo % pin
            ringPaint.strokeWidth = r * 0.04f
            ringPaint.color = 0x33FFFFFF
            c.drawCircle(cx, cy, r, ringPaint)
            ringPaint.color = if (battery.isLow) COLOR_LOW else accent
            oval.set(cx - r, cy - r, cx + r, cy + r)
            c.drawArc(oval, -90f, battery.percent * 3.6f, false, ringPaint)

            // Vạch chia 60 phút
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90.0)
                val major = i % 5 == 0
                val outer = r * 0.86f
                val inner = outer - r * (if (major) 0.09f else 0.04f)
                tickPaint.strokeWidth = r * (if (major) 0.018f else 0.008f)
                tickPaint.alpha = if (major) 255 else 140
                c.drawLine(
                    cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat(),
                    cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(), tickPaint
                )
            }

            hand(c, cx, cy, hour / 12f, r * 0.48f, r * 0.045f, Color.WHITE)
            hand(c, cx, cy, min / 60f, r * 0.70f, r * 0.030f, Color.WHITE)
            if (showSeconds) hand(c, cx, cy, sec / 60f, r * 0.78f, r * 0.012f, accent)

            dotPaint.color = accent
            c.drawCircle(cx, cy, r * 0.045f, dotPaint)
        }

        private fun hand(
            c: Canvas, cx: Float, cy: Float, fraction: Float,
            length: Float, width: Float, color: Int
        ) {
            val a = Math.toRadians(fraction * 360.0 - 90.0)
            handPaint.strokeWidth = width
            handPaint.color = color
            c.drawLine(cx, cy, cx + (cos(a) * length).toFloat(), cy + (sin(a) * length).toFloat(), handPaint)
        }
    }
}
