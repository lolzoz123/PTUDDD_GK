package com.example.clockwidget

import android.Manifest
import android.app.StatusBarManager
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.clockwidget.ambient.AmbientClockActivity
import com.example.clockwidget.floating.FloatingClockService
import com.example.clockwidget.tile.AmbientClockTileService
import com.example.clockwidget.tile.FloatingClockTileService
import com.example.clockwidget.wallpaper.ClockWallpaperService
import com.example.clockwidget.widget.ClockWidgetProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var switchFloating: MaterialSwitch
    private var syncing = false

    // Android 13+: xin quyền thông báo (cho notification của foreground service), rồi bật đồng hồ nổi
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            FloatingClockService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Live Wallpaper
        findViewById<MaterialButton>(R.id.btnWallpaper).setOnClickListener { openWallpaperPicker() }

        // 2. Quick Settings Tile
        findViewById<MaterialButton>(R.id.btnTileFloating).setOnClickListener {
            requestTile(FloatingClockTileService::class.java, R.string.tile_floating_label, R.drawable.ic_tile_float)
        }
        findViewById<MaterialButton>(R.id.btnTileAmbient).setOnClickListener {
            requestTile(AmbientClockTileService::class.java, R.string.tile_ambient_label, R.drawable.ic_tile_ambient)
        }

        // 3. Always-on Display
        findViewById<MaterialButton>(R.id.btnAmbient).setOnClickListener {
            startActivity(Intent(this, AmbientClockActivity::class.java))
        }

        // 4. Floating widget
        switchFloating = findViewById(R.id.switchFloating)
        switchFloating.setOnCheckedChangeListener { _, checked ->
            if (syncing) return@setOnCheckedChangeListener
            if (checked) enableFloating() else FloatingClockService.stop(this)
        }

        // 5. Adaptive widget
        findViewById<MaterialButton>(R.id.btnPinWidget).setOnClickListener { pinWidget() }

        // Tuỳ chỉnh chung
        buildColorRow()
        findViewById<MaterialSwitch>(R.id.switch24h).apply {
            isChecked = ClockPrefs.use24h(this@MainActivity)
            setOnCheckedChangeListener { _, c ->
                ClockPrefs.setUse24h(this@MainActivity, c)
                ClockWidgetProvider.refreshAll(this@MainActivity)
            }
        }
        findViewById<MaterialSwitch>(R.id.switchSeconds).apply {
            isChecked = ClockPrefs.showSeconds(this@MainActivity)
            setOnCheckedChangeListener { _, c -> ClockPrefs.setShowSeconds(this@MainActivity, c) }
        }
    }

    override fun onResume() {
        super.onResume()
        syncing = true
        switchFloating.isChecked = FloatingClockService.isRunning
        syncing = false
    }

    // ---------- 1. Wallpaper ----------
    private fun openWallpaperPicker() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, ClockWallpaperService::class.java)
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    // ---------- 2. Tile ----------
    private fun requestTile(service: Class<*>, labelRes: Int, iconRes: Int) {
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+: hệ thống hiện hộp thoại "Thêm ô" ngay trong app
            val sbm = getSystemService(StatusBarManager::class.java)
            sbm.requestAddTileService(
                ComponentName(this, service),
                getString(labelRes),
                Icon.createWithResource(this, iconRes),
                mainExecutor
            ) { }
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tile_hint_title)
                .setMessage(getString(R.string.tile_hint_msg, getString(labelRes)))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    // ---------- 4. Floating ----------
    private fun enableFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            syncing = true; switchFloating.isChecked = false; syncing = false
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        FloatingClockService.start(this)
    }

    // ---------- 5. Widget ----------
    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, ClockWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(this, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Chọn màu nhấn ----------
    private fun buildColorRow() {
        val row = findViewById<LinearLayout>(R.id.colorRow)
        row.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (40 * density).toInt()
        val selected = ClockPrefs.accentIndex(this)
        val strokeColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE
        )
        ClockPrefs.ACCENTS.forEachIndexed { index, color ->
            val dot = View(this)
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = size / 3 }
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (index == selected) setStroke((3 * density).toInt(), strokeColor)
            }
            dot.setOnClickListener {
                ClockPrefs.setAccentIndex(this, index)
                ClockWidgetProvider.refreshAll(this)
                buildColorRow()
            }
            row.addView(dot)
        }
    }
}
