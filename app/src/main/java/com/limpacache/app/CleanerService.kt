package com.limpacache.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * O "robô" do app. Para cada app da fila ele:
 *  1. abre a tela "Informações do app" do Android;
 *  2. entra em "Armazenamento" (se precisar);
 *  3. toca no botão "Limpar cache";
 *  4. passa para o próximo.
 * É assim que o 1Tap Cleaner original faz, porque o Android não deixa
 * um app comum apagar o cache dos outros diretamente.
 */
class CleanerService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CleanerService? = null

        private const val WAIT_BEFORE_READ_MS = 1000L
        private const val TIMEOUT_PER_APP_MS = 9000L
        private const val DISABLED_WAIT_MS = 2500L
        private const val STORAGE_RETRY_MS = 2500L

        /** O serviço de acessibilidade está ligado nas configurações? */
        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val cn = ComponentName(ctx, CleanerService::class.java)
            val full = cn.flattenToString()
            val short = cn.flattenToShortString()
            return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
        }

        private val CLEAR_WORDS = listOf(
            "clear", "limpar", "apagar", "borrar", "vaciar", "effacer", "vider",
            "löschen", "leeren", "svuota", "cancella", "очист", "清除", "delete",
            "excluir", "eliminar", "remove"
        )
        private val DATA_WORDS = listOf("dados", "data", "datos", "daten", "données", "dati")
        private val STORAGE_WORDS = listOf(
            "storage", "armazenamento", "almacenamiento", "speicher", "stockage", "memória", "memoria"
        )
        private val CACHE_WORDS = listOf("cache", "cachê", "caché")

        /** Texto do botão "Limpar cache" (e só dele, nunca "Limpar dados"). */
        fun isClearCacheText(t: String): Boolean =
            t.length < 40 &&
                CACHE_WORDS.any { t.contains(it) } &&
                CLEAR_WORDS.any { t.contains(it) } &&
                DATA_WORDS.none { t.contains(it) } &&
                STORAGE_WORDS.none { t.contains(it) }

        /** Linha "Armazenamento" / "Armazenamento e cache" da tela do app. */
        fun isStorageText(t: String): Boolean =
            t.length < 40 &&
                STORAGE_WORDS.any { t.contains(it) } &&
                CLEAR_WORDS.none { t.contains(it) }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<AppCache>()
    private var current: AppCache? = null
    private var startedAt = 0L
    private var clickedStorageAt = 0L
    private var disabledSince = 0L
    private var clearHandled = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!CleanState.running) return
            attempt()
            if (CleanState.running &&
                System.currentTimeMillis() - startedAt > TIMEOUT_PER_APP_MS
            ) {
                finishCurrent(false)
            }
            handler.postDelayed(this, 400)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        stop()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (CleanState.running) attempt()
    }

    // ---------------------------------------------------------------- controle

    fun start(items: List<AppCache>) {
        if (CleanState.running || items.isEmpty()) return
        queue.clear()
        queue.addAll(items)
        CleanState.total = items.size
        CleanState.done = 0
        CleanState.cleaned = 0
        CleanState.finishedMessage = null
        CleanState.running = true
        next()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 400)
    }

    fun stop() {
        if (!CleanState.running) return
        queue.clear()
        finishAll("Limpeza parada. ${CleanState.cleaned} apps limpos.")
    }

    private fun next() {
        val item = queue.removeFirstOrNull()
        if (item == null) {
            finishAll("Pronto! ${CleanState.cleaned} de ${CleanState.total} apps limpos.")
            return
        }
        current = item
        CleanState.currentLabel = item.label
        startedAt = System.currentTimeMillis()
        clickedStorageAt = 0L
        disabledSince = 0L
        clearHandled = false
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${item.pkg}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            finishCurrent(false)
        }
    }

    private fun finishCurrent(success: Boolean) {
        CleanState.done++
        if (success) CleanState.cleaned++
        next()
    }

    private fun finishAll(message: String) {
        CleanState.running = false
        CleanState.finishedMessage = message
        current = null
        handler.removeCallbacks(ticker)
        handler.postDelayed({
            try {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
            } catch (_: Exception) {
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }, 500)
    }

    // ------------------------------------------------------------ olhar a tela

    private fun attempt() {
        val item = current ?: return
        val now = System.currentTimeMillis()
        if (clearHandled || now - startedAt < WAIT_BEFORE_READ_MS) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() == packageName) return

        val clear = findNode(root, 0) { isClearCacheText(it) }
        if (clear != null) {
            val button = clickableOf(clear) ?: clear
            if (button.isEnabled) {
                clearHandled = true
                click(clear)
                handler.postDelayed({
                    if (CleanState.running && current === item) finishCurrent(true)
                }, 700)
            } else {
                // botão cinza: ou o cache está zerado, ou o Android ainda está calculando
                if (disabledSince == 0L) disabledSince = now
                if (now - disabledSince > DISABLED_WAIT_MS) {
                    clearHandled = true
                    finishCurrent(false)
                }
            }
            return
        }

        if (now - clickedStorageAt > STORAGE_RETRY_MS) {
            val storage = findNode(root, 0) { isStorageText(it) }
            if (storage != null && click(storage)) clickedStorageAt = now
        }
    }

    private fun nodeText(n: AccessibilityNodeInfo): String? =
        (n.text ?: n.contentDescription)?.toString()?.lowercase()

    private fun findNode(
        n: AccessibilityNodeInfo?,
        depth: Int,
        matches: (String) -> Boolean
    ): AccessibilityNodeInfo? {
        if (n == null || depth > 30) return null
        val t = nodeText(n)
        if (t != null && matches(t)) return n
        for (i in 0 until n.childCount) {
            val found = findNode(n.getChild(i), depth + 1, matches)
            if (found != null) return found
        }
        return null
    }

    private fun clickableOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 6) {
            if (n.isClickable) return n
            n = n.parent
            hops++
        }
        return null
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        val target = clickableOf(node) ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
