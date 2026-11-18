package com.limpacache.app

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val green = Color.parseColor("#1B8A5A")

    private lateinit var totalText: TextView
    private lateinit var statusText: TextView
    private lateinit var cleanBtn: Button
    private lateinit var setupBox: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var selectAll: CheckBox
    private lateinit var progress: ProgressBar
    private lateinit var refreshBtn: Button

    private var items: List<AppCache> = emptyList()
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    // ------------------------------------------------------------------ tela

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun outlined(): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(2), green)
            cornerRadius = dp(14).toFloat()
        }

    private fun params(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(16), dp(top), dp(16), dp(bottom)) }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F6F5"))
        }

        // topo verde
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(green)
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        header.addView(TextView(this).apply {
            text = "Limpa Cache"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        totalText = TextView(this).apply {
            text = "Verificando..."
            textSize = 16f
            setTextColor(Color.parseColor("#D8F5E7"))
            setPadding(0, dp(4), 0, 0)
        }
        header.addView(totalText)
        root.addView(header, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        // corpo rolável
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setupBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(10))
        }
        body.addView(setupBox, params(top = 16))

        cleanBtn = Button(this).apply {
            text = "LIMPAR CACHE AGORA"
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(green, 18)
            setOnClickListener { onCleanClick() }
        }
        body.addView(
            cleanBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)
            ).apply { setMargins(dp(16), dp(16), dp(16), 0) }
        )

        refreshBtn = Button(this).apply {
            text = "↻  Atualizar números"
            textSize = 15f
            isAllCaps = false
            setTextColor(green)
            background = outlined()
            setOnClickListener {
                statusText.text = "Atualizando..."
                scan()
            }
        }
        body.addView(
            refreshBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { setMargins(dp(16), dp(10), dp(16), 0) }
        )

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(green)
            visibility = View.GONE
        }
        body.addView(
            progress,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)
            ).apply { setMargins(dp(16), dp(14), dp(16), 0) }
        )

        statusText = TextView(this).apply {
            text = "Durante a limpeza a tela vai piscar por alguns segundos. Não toque nela."
            textSize = 14f
            setTextColor(Color.parseColor("#55605B"))
            gravity = Gravity.CENTER
        }
        body.addView(statusText, params(top = 10))

        selectAll = CheckBox(this).apply {
            text = "Selecionar todos"
            isChecked = true
            setOnClickListener {
                items.forEach { it.selected = isChecked }
                renderList()
            }
        }
        body.addView(selectAll, params(top = 12))

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(listBox, params(bottom = 24))

        setContentView(root)
    }

    private fun buildSetup(usage: Boolean, acc: Boolean, overlay: Boolean) {
        setupBox.removeAllViews()
        if (usage && acc && overlay) {
            setupBox.visibility = View.GONE
            return
        }
        setupBox.visibility = View.VISIBLE
        setupBox.addView(TextView(this).apply {
            text = "Antes de começar (só uma vez)"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D2B24"))
        })
        setupBox.addView(
            stepRow(
                "1. Acesso ao uso",
                "Para ver quanto cache cada app tem.",
                usage
            ) { openUsageAccess() }
        )
        setupBox.addView(
            stepRow(
                "2. Acessibilidade",
                "Procure \"Limpa Cache\" e ative. É o robô que toca em \"Limpar cache\".",
                acc
            ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )
        setupBox.addView(
            stepRow(
                "3. Aparecer sobre outros apps",
                "Recomendado: ajuda o robô a abrir as telas em segundo plano.",
                overlay
            ) { openOverlay() }
        )
    }

    private fun stepRow(title: String, sub: String, ok: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D2B24"))
        })
        texts.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(Color.parseColor("#66726C"))
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (ok) {
            row.addView(TextView(this).apply {
                text = "✓"
                textSize = 24f
                setTextColor(green)
                setPadding(dp(12), 0, dp(8), 0)
            })
        } else {
            row.addView(Button(this).apply {
                text = "Ativar"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = rounded(green, 12)
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(dp(88), dp(44)).apply { leftMargin = dp(10) })
        }
        return row
    }

    // ----------------------------------------------------------------- dados

    private fun refresh() {
        val usage = CacheScanner.hasUsageAccess(this)
        val acc = CleanerService.isEnabled(this)
        val overlay = CacheScanner.canOverlay(this)
        buildSetup(usage, acc, overlay)
        if (usage) {
            scan()
        } else {
            items = emptyList()
            listBox.removeAllViews()
            totalText.text = "Faça o passo 1 para ver o cache"
        }
    }

    private fun scan() {
        if (scanning || !CacheScanner.hasUsageAccess(this)) return
        scanning = true
        val ctx = applicationContext
        Thread {
            val list = try {
                CacheScanner.scan(ctx)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                scanning = false
                // mantém as marcações que o usuário já fez
                val unchecked = items.filter { !it.selected }.map { it.pkg }.toSet()
                list.forEach { if (it.pkg in unchecked) it.selected = false }
                items = list
                renderList()
                if (statusText.text == "Atualizando...") statusText.text = "Números atualizados."
            }
        }.start()
    }

    private fun renderList() {
        val sum = items.sumOf { it.bytes }
        totalText.text = if (items.isEmpty()) "Nenhum cache para limpar"
        else "Cache total: ${CacheScanner.format(sum)}  •  ${items.size} apps"
        selectAll.isChecked = items.isNotEmpty() && items.all { it.selected }
        listBox.removeAllViews()
        addSection("Apps instalados", items.filter { !it.system })
        addSection("Apps do sistema", items.filter { it.system })
    }

    /** Um grupo (instalados ou sistema) com caixinha para marcar o grupo inteiro. */
    private fun addSection(title: String, list: List<AppCache>) {
        if (list.isEmpty()) return
        val head = CheckBox(this).apply {
            text = "$title  •  ${list.size} apps  •  ${CacheScanner.format(list.sumOf { it.bytes })}"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(green)
            isChecked = list.all { it.selected }
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener {
                val on = isChecked
                list.forEach { it.selected = on }
                renderList()
            }
        }
        listBox.addView(
            head,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        )
        for (item in list) {
            val box = CheckBox(this).apply {
                text = "${item.label}  •  ${CacheScanner.format(item.bytes)}"
                textSize = 15f
                isChecked = item.selected
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = rounded(Color.WHITE, 10)
                setOnCheckedChangeListener { _, checked ->
                    item.selected = checked
                    selectAll.isChecked = items.all { it.selected }
                    head.isChecked = list.all { it.selected }
                }
            }
            listBox.addView(
                box,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            )
        }
    }

    private fun updateStatus() {
        if (CleanState.running) {
            val n = minOf(CleanState.done + 1, CleanState.total)
            statusText.text = "Limpando $n de ${CleanState.total}: ${CleanState.currentLabel}"
            cleanBtn.text = "PARAR"
            progress.visibility = View.VISIBLE
            progress.progress =
                if (CleanState.total > 0) CleanState.done * 100 / CleanState.total else 0
            refreshBtn.isEnabled = false
        } else {
            cleanBtn.text = "LIMPAR CACHE AGORA"
            progress.visibility = View.GONE
            refreshBtn.isEnabled = true
            val msg = CleanState.finishedMessage
            if (msg != null) {
                CleanState.finishedMessage = null
                statusText.text = msg
                scan()
            }
        }
    }

    // ---------------------------------------------------------------- botões

    private fun onCleanClick() {
        if (CleanState.running) {
            CleanerService.instance?.stop()
            return
        }
        if (!CacheScanner.hasUsageAccess(this)) {
            toast("Faça o passo 1 primeiro.")
            openUsageAccess()
            return
        }
        if (!CleanerService.isEnabled(this)) {
            toast("Ative o \"Limpa Cache\" na Acessibilidade (passo 2).")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val service = CleanerService.instance
        if (service == null) {
            toast("Desligue e ligue de novo o Limpa Cache na Acessibilidade.")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val chosen = items.filter { it.selected }
        if (chosen.isEmpty()) {
            toast("Não há nada marcado para limpar.")
            return
        }
        CacheScanner.clearOwnCache(this)
        service.start(chosen)
    }

    private fun openUsageAccess() {
        try {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun openOverlay() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
