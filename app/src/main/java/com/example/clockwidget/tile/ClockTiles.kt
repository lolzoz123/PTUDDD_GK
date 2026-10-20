package com.example.clockwidget.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.clockwidget.MainActivity
import com.example.clockwidget.R
import com.example.clockwidget.ambient.AmbientClockActivity
import com.example.clockwidget.floating.FloatingClockService

abstract class BaseTile : TileService() {

    /** Mở Activity từ tile và tự thu gọn bảng cài đặt nhanh. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    protected fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    protected fun render(active: Boolean, subtitle: String?) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = subtitle
        tile.updateTile()
    }
}

/** Tile bật/tắt đồng hồ nổi. */
class FloatingClockTileService : BaseTile() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (FloatingClockService.isRunning) {
            FloatingClockService.stop(this)
            render(false, getString(R.string.tile_off))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            // Chưa có quyền vẽ trên ứng dụng khác -> mở trang cấp quyền
            launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        try {
            FloatingClockService.start(this)
            render(true, getString(R.string.tile_on))
        } catch (e: Exception) {
            // Hệ thống chặn khởi động foreground service -> mở app để người dùng bật thủ công
            launch(Intent(this, MainActivity::class.java))
        }
    }

    private fun refresh() {
        val on = FloatingClockService.isRunning
        render(on, getString(if (on) R.string.tile_on else R.string.tile_off))
    }
}

/** Tile mở nhanh đồng hồ AOD (hiện được trên màn hình khóa). */
class AmbientClockTileService : BaseTile() {

    override fun onStartListening() = render(false, null)

    override fun onClick() = launch(Intent(this, AmbientClockActivity::class.java))
}
