package com.limpacache.app

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import java.io.File
import java.util.Locale

/** Um app com o tamanho do seu cache. */
data class AppCache(
    val pkg: String,
    val label: String,
    val bytes: Long,
    var selected: Boolean = true
)

/** Estado da limpeza em andamento (lido pela tela principal). */
object CleanState {
    @Volatile var running = false
    @Volatile var total = 0
    @Volatile var done = 0
    @Volatile var cleaned = 0
    @Volatile var currentLabel = ""
    @Volatile var finishedMessage: String? = null
}

object CacheScanner {

    /** O usuário liberou "Acesso ao uso"? (necessário para ler o tamanho do cache) */
    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** Lista os apps que têm cache, do maior para o menor. */
    fun scan(ctx: Context): List<AppCache> {
        val pm = ctx.packageManager
        val stats = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val user = Process.myUserHandle()
        val result = ArrayList<AppCache>()
        for (info in pm.getInstalledApplications(0)) {
            if (info.packageName == ctx.packageName) continue
            try {
                val bytes = stats
                    .queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
                    .cacheBytes
                if (bytes > 0) {
                    result.add(AppCache(info.packageName, pm.getApplicationLabel(info).toString(), bytes))
                }
            } catch (_: Exception) {
                // app sem permissão de leitura: ignora
            }
        }
        result.sortByDescending { it.bytes }
        return result
    }

    /** Apaga o cache do próprio Limpa Cache (isso o Android permite sem robô). */
    fun clearOwnCache(ctx: Context): Long {
        var freed = 0L
        val dirs = listOfNotNull(ctx.cacheDir, ctx.externalCacheDir, ctx.codeCacheDir)
        for (dir in dirs) {
            freed += sizeOf(dir)
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }
        return freed
    }

    private fun sizeOf(f: File): Long =
        if (f.isDirectory) (f.listFiles()?.sumOf { sizeOf(it) } ?: 0L) else f.length()

    fun format(bytes: Long): String {
        val loc = Locale("pt", "BR")
        return when {
            bytes >= 1L shl 30 -> String.format(loc, "%.1f GB", bytes / 1073741824.0)
            bytes >= 1L shl 20 -> String.format(loc, "%.0f MB", bytes / 1048576.0)
            bytes >= 1024 -> String.format(loc, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
