package com.pathfinder.gd

data class LevelEvent(
    val timeSeconds: Double,
    val trigger: TriggerType,
    val holdSeconds: Double? = null,
    val note: String = "",
)

data class LevelScript(
    val label: String,
    val packageName: String = "com.robtopx.geometryjump",
    val tapX: Float = 540f,
    val tapY: Float = 1600f,
    val events: List<LevelEvent>,
)

object LevelScriptLibrary {
    val scripts = listOf(
        LevelScript("Stereo Madness Demo", events = listOf(
            LevelEvent(0.92, TriggerType.JUMP, note = "Salto simple"),
            LevelEvent(1.86, TriggerType.JUMP, note = "Doble pico"),
            LevelEvent(3.14, TriggerType.YELLOW_PAD, note = "Pad amarillo")
        )),
        LevelScript("Cube Timing Test", events = listOf(
            LevelEvent(0.75, TriggerType.JUMP, note = "Entrada"),
            LevelEvent(1.50, TriggerType.JUMP, note = "Gap"),
            LevelEvent(2.18, TriggerType.RED_ORB, note = "Orb alto")
        ))
    )
}

object LevelScriptPlanner {
    fun createPlan(script: LevelScript, mode: GameMode): BotPlan {
        val actions = script.events.flatMap { event ->
            val noteSuffix = if (event.note.isBlank()) event.trigger.label else "${event.trigger.label} | ${event.note}"
            if (mode == GameMode.SHIP || mode == GameMode.ROBOT || event.holdSeconds != null) {
                val hold = event.holdSeconds ?: 0.10
                listOf(
                    BotInputAction(event.timeSeconds, BotInputType.HOLD, hold, noteSuffix),
                    BotInputAction(event.timeSeconds + hold, BotInputType.RELEASE, note = "Soltar | ${event.note.ifBlank { event.trigger.label }}")
                )
            } else {
                listOf(BotInputAction(event.timeSeconds, BotInputType.TAP, note = noteSuffix))
            }
        }
        return BotPlan("Script ${script.label}", "Plan por timing con ${script.events.size} eventos.", actions)
    }

    fun createLines(script: LevelScript): List<String> = buildList {
        add("Paquete: ${script.packageName}")
        add("Tap: x=${script.tapX.toInt()}, y=${script.tapY.toInt()}")
        script.events.forEachIndexed { i, e ->
            val hold = e.holdSeconds?.let { " | hold ${"%.2f".format(it)}s" }.orEmpty()
            add("${i + 1}. t=${"%.2f".format(e.timeSeconds)}s | ${e.trigger.label}$hold${if (e.note.isBlank()) "" else " | ${e.note}"}")
        }
    }
}
