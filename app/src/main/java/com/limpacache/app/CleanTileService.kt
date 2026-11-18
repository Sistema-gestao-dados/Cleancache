package com.limpacache.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Botão "Limpar cache" na barra de notificações (puxe para baixo e toque). */
class CleanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (CleanState.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val service = CleanerService.instance
        if (service == null || !CacheScanner.hasUsageAccess(this)) {
            openApp()
            return
        }
        if (CleanState.running) return
        val ctx = applicationContext
        Thread {
            val list = CacheScanner.scan(ctx)
            CacheScanner.clearOwnCache(ctx)
            Handler(Looper.getMainLooper()).post { service.start(list) }
        }.start()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
}
