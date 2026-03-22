package com.pathfinder.gd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var modeDropdown: AutoCompleteTextView
    private lateinit var speedDropdown: AutoCompleteTextView
    private lateinit var triggerDropdown: AutoCompleteTextView
    private lateinit var scenarioDropdown: AutoCompleteTextView
    private lateinit var scriptDropdown: AutoCompleteTextView
    private lateinit var miniSwitch: SwitchMaterial
    private lateinit var gravitySwitch: SwitchMaterial
    private lateinit var dualSwitch: SwitchMaterial
    private lateinit var twoPlayerSwitch: SwitchMaterial
    private lateinit var inputSlider: Slider
    private lateinit var inputValue: TextView
    private lateinit var recalculateButton: MaterialButton
    private lateinit var pathfindButton: MaterialButton
    private lateinit var packageInput: TextInputEditText
    private lateinit var tapXInput: TextInputEditText
    private lateinit var tapYInput: TextInputEditText
    private lateinit var openAccessibilityButton: MaterialButton
    private lateinit var openGameButton: MaterialButton
    private lateinit var loadScriptButton: MaterialButton
    private lateinit var runScriptButton: MaterialButton
    private lateinit var runBotButton: MaterialButton
    private lateinit var ultraButton: MaterialButton
    private lateinit var copyGdrButton: MaterialButton
    private lateinit var trajectoryView: TrajectoryView


    private lateinit var summaryText: TextView
    private lateinit var detailsText: TextView
    private lateinit var botPlanText: TextView
    private lateinit var detectionText: TextView
    private lateinit var liveBotHint: TextView

    // ── State ─────────────────────────────────────────────────────────────────
    private var currentPlan: BotPlan? = null
    private var loadedScript: LevelScript? = null

    private val gameModes    = GameMode.entries.toTypedArray()
    private val speedProfiles = SpeedProfile.entries.toTypedArray()
    private val triggerTypes  = TriggerType.entries.toTypedArray()
    private val scenarios     = ScenarioLibrary.scenarios
    private val scripts       = LevelScriptLibrary.scripts

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { importScriptFile(it) }
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupDropdowns()
        setupListeners()
        checkPermissions()
        updateBotStatus()
        calculateTrajectory()
    }

    override fun onResume() {
        super.onResume()
        updateBotStatus()
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private fun initViews() {
        modeDropdown        = findViewById(R.id.modeDropdown)
        speedDropdown       = findViewById(R.id.speedDropdown)
        triggerDropdown     = findViewById(R.id.triggerDropdown)
        scenarioDropdown    = findViewById(R.id.scenarioDropdown)
        scriptDropdown      = findViewById(R.id.scriptDropdown)
        miniSwitch          = findViewById(R.id.miniSwitch)
        gravitySwitch       = findViewById(R.id.gravitySwitch)
        dualSwitch          = findViewById(R.id.dualSwitch)
        twoPlayerSwitch     = findViewById(R.id.twoPlayerSwitch)
        inputSlider         = findViewById(R.id.inputSlider)
        inputValue          = findViewById(R.id.inputValue)
        recalculateButton   = findViewById(R.id.recalculateButton)
        pathfindButton      = findViewById(R.id.pathfindButton)
        packageInput        = findViewById(R.id.packageInput)
        tapXInput           = findViewById(R.id.tapXInput)
        tapYInput           = findViewById(R.id.tapYInput)
        openAccessibilityButton = findViewById(R.id.openAccessibilityButton)
        openGameButton      = findViewById(R.id.openGameButton)
        loadScriptButton    = findViewById(R.id.loadScriptButton)
        runScriptButton     = findViewById(R.id.runScriptButton)
        runBotButton        = findViewById(R.id.runBotButton)
        ultraButton         = findViewById(R.id.ultraButton)
        copyGdrButton       = findViewById(R.id.copyGdrButton)
        trajectoryView      = findViewById(R.id.trajectoryView)


        summaryText         = findViewById(R.id.summaryText)
        detailsText         = findViewById(R.id.detailsText)
        botPlanText         = findViewById(R.id.botPlanText)
        detectionText       = findViewById(R.id.detectionText)
        liveBotHint         = findViewById(R.id.liveBotHint)

        packageInput.setText("com.robtopx.geometryjump")
        tapXInput.setText("540")
        tapYInput.setText("2000")
        inputValue.text = "50%"
    }

    private fun setupDropdowns() {
        modeDropdown.setAdapter(ArrayAdapter(this,
            android.R.layout.simple_dropdown_item_1line, gameModes.map { it.label }))
        modeDropdown.setText(gameModes[0].label, false)

        speedDropdown.setAdapter(ArrayAdapter(this,
            android.R.layout.simple_dropdown_item_1line, speedProfiles.map { it.label }))
        speedDropdown.setText(speedProfiles[1].label, false)

        triggerDropdown.setAdapter(ArrayAdapter(this,
            android.R.layout.simple_dropdown_item_1line, triggerTypes.map { it.label }))
        triggerDropdown.setText(triggerTypes[0].label, false)

        scenarioDropdown.setAdapter(ArrayAdapter(this,
            android.R.layout.simple_dropdown_item_1line, scenarios.map { it.label }))
        scenarioDropdown.setText(scenarios[0].label, false)

        val scriptLabels = scripts.map { it.label } + listOf("Importar archivo…")
        scriptDropdown.setAdapter(ArrayAdapter(this,
            android.R.layout.simple_dropdown_item_1line, scriptLabels))
        scriptDropdown.setText(scripts[0].label, false)
    }

    private fun setupListeners() {
        inputSlider.addOnChangeListener { _, value, _ ->
            inputValue.text = "%.0f%%".format(value)
        }
        recalculateButton.setOnClickListener    { calculateTrajectory() }
        pathfindButton.setOnClickListener       { pathfindLevel() }
        openAccessibilityButton.setOnClickListener { openAccessibilitySettings() }
        openGameButton.setOnClickListener       { launchGame() }
        loadScriptButton.setOnClickListener     { loadScript() }
        runScriptButton.setOnClickListener      { runScript() }
        runBotButton.setOnClickListener         { runBot() }
        ultraButton.setOnClickListener          { activateUltra() }
        copyGdrButton.setOnClickListener        { copyGdrToClipboard() }

        dualSwitch.setOnCheckedChangeListener { _, checked ->


            twoPlayerSwitch.isEnabled = checked
        }
        scriptDropdown.setOnItemClickListener { _, _, position, _ ->
            if (position == scripts.size) filePickerLauncher.launch("*/*")
            else onScriptSelected(scripts[position])
        }
    }

    // ── Physics config ────────────────────────────────────────────────────────

    private fun buildConfig(): PhysicsConfig {
        val mode    = gameModes.firstOrNull    { it.label == modeDropdown.text.toString()    } ?: GameMode.CUBE
        val speed   = speedProfiles.firstOrNull{ it.label == speedDropdown.text.toString()   } ?: SpeedProfile.NORMAL
        val trigger = triggerTypes.firstOrNull { it.label == triggerDropdown.text.toString() } ?: TriggerType.JUMP
        return PhysicsConfig(
            mode = mode, speed = speed,
            mini = miniSwitch.isChecked,
            invertedGravity = gravitySwitch.isChecked,
            dualMode = dualSwitch.isChecked,
            twoPlayerSplit = twoPlayerSwitch.isChecked,
            trigger = trigger,
            inputStrength = inputSlider.value / 100.0,
        )
    }

    // ── Trajectory preview ────────────────────────────────────────────────────

    private fun calculateTrajectory() {
        val config = buildConfig()
        val result = GeometryDashPhysics.simulate(config)
        trajectoryView.post { trajectoryView.submitTrajectory(result) }
        summaryText.text = result.summary
        detailsText.text = result.detailLines.joinToString("\n")
        val plan = GeometryDashBotPlanner.createPreviewPlan(config, result)
        currentPlan = plan
        BotExecutionStore.update(plan)
        botPlanText.text = plan.toLines().joinToString("\n")
    }

    // ── Pathfinder — uses real A* via NDK ────────────────────────────────────

    private fun pathfindLevel() {
        // First check if native lib is available; fall back to pure-Kotlin pathfinder
        val nativeAvailable = runCatching { PathfinderEngine }.isSuccess

        if (nativeAvailable) {
            pathfindWithNativeEngine()
        } else {
            pathfindWithKotlinFallback()
        }
    }

    /** Uses the real gd-sim A* compiled via NDK */
    private fun pathfindWithNativeEngine() {
        val lvlString = detectionText.tag as? String
        if (lvlString.isNullOrBlank()) {
            Toast.makeText(this, "Pegá el lvlString en el campo de detección primero", Toast.LENGTH_LONG).show()
            return
        }
        pathfindButton.isEnabled = false
        pathfindButton.text = "0%…"
        detailsText.text = "Corriendo A* nativo (gd-sim)…"

        PathfinderEngine.runAsync(
            lvlString,
            onProgress = { pct ->
                runOnUiThread { pathfindButton.text = "%.0f%%…".format(pct) }
            },
            onDone = { gdr2Bytes ->
                runOnUiThread {
                    pathfindButton.isEnabled = true
                    pathfindButton.text = "Pathfind"
                    if (gdr2Bytes.isEmpty()) {
                        summaryText.text = "A* no encontró ruta"
                        detailsText.text = "Intentá con un lvlString válido o dejalo más tiempo"
                    } else {
                        saveReplay(gdr2Bytes)
                        summaryText.text = "✓ Replay generado (${gdr2Bytes.size} bytes)"
                        detailsText.text = "Guardado en: ${replayFile().absolutePath}"
                        // Build a timing plan from the gdr2 so the bot can execute it
                        val plan = buildPlanFromGdr2(gdr2Bytes)
                        currentPlan = plan
                        BotExecutionStore.update(plan)
                        botPlanText.text = plan.toLines().joinToString("\n")
                    }
                }
            }
        )
    }

    /** Pure-Kotlin BFS pathfinder — works without NDK */
    private fun pathfindWithKotlinFallback() {
        val config = buildConfig()
        val scenario = scenarios.firstOrNull {
            it.label == scenarioDropdown.text.toString()
        } ?: scenarios[0]

        pathfindButton.isEnabled = false
        pathfindButton.text = "Buscando…"

        Thread {
            val route  = GeometryDashPathfinder.findRoute(config, scenario)
            val plan   = GeometryDashBotPlanner.createRoutePlan(route)
            val dummy  = GeometryDashPhysics.simulate(config)
            runOnUiThread {
                trajectoryView.post { trajectoryView.submitScenario(dummy, scenario, route) }
                summaryText.text = if (route.found) "Ruta encontrada ✓" else "Sin ruta válida"
                detailsText.text = route.detailLines.joinToString("\n")
                botPlanText.text = plan.toLines().joinToString("\n")
                currentPlan = plan
                BotExecutionStore.update(plan)
                pathfindButton.isEnabled = true
                pathfindButton.text = "Pathfind"
            }
        }.start()
    }

    // ── lvlString input ───────────────────────────────────────────────────────

    /**
     * The user can paste a raw decompressed GD level string into detectionText.
     * We store it as the tag so pathfindWithNativeEngine() can read it.
     */
    private fun onLvlStringPasted(raw: String) {
        detectionText.tag = raw
        detectionText.text = "lvlString cargado (${raw.length} chars)"
    }

    // ── Script handling ───────────────────────────────────────────────────────

    private fun loadScript() {
        val label = scriptDropdown.text.toString()
        val idx = scripts.indexOfFirst { it.label == label }
        if (idx >= 0) onScriptSelected(scripts[idx])
        else filePickerLauncher.launch("*/*")
    }

    private fun onScriptSelected(script: LevelScript) {
        loadedScript = script
        detectionText.text = LevelScriptPlanner.createLines(script).joinToString("\n")
        val plan = LevelScriptPlanner.createPlan(script, buildConfig().mode)
        currentPlan = plan
        BotExecutionStore.update(plan)
        botPlanText.text = plan.toLines().joinToString("\n")
        Toast.makeText(this, "Script '${script.label}' cargado", Toast.LENGTH_SHORT).show()
    }

    private fun importScriptFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            // Check if it's a raw level string (starts with a digit or letter, not '{')
            if (!text.trimStart().startsWith('{')) {
                onLvlStringPasted(text.trim())
                return
            }
            // Otherwise try to parse as JSON script
            val events = mutableListOf<LevelEvent>()
            val eventsBlock = Regex("\"events\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
                .find(text)?.groupValues?.get(1) ?: ""
            Regex(
                "\"timeSeconds\"\\s*:\\s*([\\d.]+).*?" +
                "\"trigger\"\\s*:\\s*\"([^\"]+)\"" +
                "(?:.*?\"holdSeconds\"\\s*:\\s*([\\d.]+))?",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ).findAll(eventsBlock).forEach { m ->
                val t    = m.groupValues[1].toDoubleOrNull() ?: return@forEach
                val trig = TriggerType.entries.firstOrNull { it.name == m.groupValues[2] } ?: TriggerType.JUMP
                val hold = m.groupValues[3].toDoubleOrNull()
                events += LevelEvent(t, trig, hold)
            }
            val label = Regex("\"label\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: "Importado"
            val tapX  = Regex("\"tapX\"\\s*:\\s*([\\d.]+)").find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 540f
            val tapY  = Regex("\"tapY\"\\s*:\\s*([\\d.]+)").find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 2000f
            val script = LevelScript(label, tapX = tapX, tapY = tapY, events = events)
            tapXInput.setText(tapX.toInt().toString())
            tapYInput.setText(tapY.toInt().toString())
            onScriptSelected(script)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Run script / bot ──────────────────────────────────────────────────────

    private fun runScript() {
        sendPlanToBot("Ejecutando en 3s… (${currentPlan?.actions?.size ?: 0} acciones)")
    }

    private fun runBot() {
        sendPlanToBot("Bot programado (3s de margen)")
    }

    private fun activateUltra() {
        try {
            PathfinderEngine.injectUltra()
            Toast.makeText(this, "MODO ULTRA ACTIVADO ✓ (Requiere inyección)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error MODO ULTRA: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyGdrToClipboard() {
        val bytes = lastGdr2Result
        if (bytes == null) {
            Toast.makeText(this, "Genera un path primero", Toast.LENGTH_SHORT).show()
            return
        }
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Pathfinder GDR2", base64))
        Toast.makeText(this, "GDR2 copiado (Base64) al portapapeles", Toast.LENGTH_SHORT).show()
    }

    private fun sendPlanToBot(toastMsg: String) {


        val plan = currentPlan
        if (plan == null || plan.actions.isEmpty()) {
            Toast.makeText(this, "Genera o carga un plan primero", Toast.LENGTH_SHORT).show()
            return
        }
        val tapX = tapXInput.text.toString().toFloatOrNull() ?: 540f
        val tapY = tapYInput.text.toString().toFloatOrNull() ?: 2000f
        BotExecutionStore.update(plan)
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(BotAccessibilityService.ACTION_START_PLAN).apply {
                putExtra(BotAccessibilityService.EXTRA_TAP_X, tapX)
                putExtra(BotAccessibilityService.EXTRA_TAP_Y, tapY)
            })
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
    }

    // ── gdr2 replay → BotPlan conversion ─────────────────────────────────────

    /**
     * Converts a .gdr2 byte array into a BotPlan so the AccessibilityService
     * can execute it as taps/holds.
     */
    private fun buildPlanFromGdr2(gdr2: ByteArray): BotPlan {
        if (gdr2.size < 16) return BotPlan("gdr2", "Replay vacío", emptyList())
        val fps = readInt32LE(gdr2, 8).coerceAtLeast(1)
        val count = readInt32LE(gdr2, 12)
        val actions = mutableListOf<BotInputAction>()
        var offset = 16
        repeat(count) {
            if (offset + 7 > gdr2.size) return@repeat
            val frame  = readInt32LE(gdr2, offset)
            val down   = gdr2[offset + 6].toInt() != 0
            offset += 7
            val tSec   = frame.toDouble() / fps
            actions += BotInputAction(
                atSeconds = tSec,
                type = if (down) BotInputType.TAP else BotInputType.RELEASE,
                note = "frame $frame",
            )
        }
        return BotPlan(
            title   = "gdr2 replay",
            summary = "Replay de ${actions.size} inputs a ${fps}fps",
            actions = actions,
        )
    }

    private fun readInt32LE(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or
        ((buf[off + 1].toInt() and 0xFF) shl 8) or
        ((buf[off + 2].toInt() and 0xFF) shl 16) or
        ((buf[off + 3].toInt() and 0xFF) shl 24)

    // ── Replay file I/O ───────────────────────────────────────────────────────

    private fun replayFile() = File(getExternalFilesDir(null), "pathfinder_replay.gdr2")

    private fun saveReplay(data: ByteArray) {
        FileOutputStream(replayFile()).use { it.write(data) }
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun launchGame() {
        val pkg = packageInput.text.toString().trim()
        packageManager.getLaunchIntentForPackage(pkg)
            ?.let { startActivity(it) }
            ?: Toast.makeText(this, "GD no encontrado: $pkg", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.FOREGROUND_SERVICE), 1)
        }
    }

    private fun updateBotStatus() {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.id.contains("BotAccessibilityService") }
        liveBotHint.setTextColor(ContextCompat.getColor(this,
            if (enabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        liveBotHint.text = if (enabled)
            "✓ Bot activo"
        else
            "⚠ Habilitar: Ajustes → Accesibilidad → Pathfinder Bot"
    }
}
