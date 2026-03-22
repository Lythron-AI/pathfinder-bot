package com.pathfinder.gd

import kotlin.math.max

enum class BotInputType(val label: String) {
    TAP("Tap"),
    HOLD("Hold"),
    RELEASE("Release"),
}

data class BotInputAction(
    val atSeconds: Double,
    val type: BotInputType,
    val durationSeconds: Double? = null,
    val note: String,
)

data class BotPlan(
    val title: String,
    val summary: String,
    val actions: List<BotInputAction>,
) {
    fun toLines(): List<String> = buildList {
        add(summary)
        actions.forEachIndexed { i, a ->
            val dur = a.durationSeconds?.let { " | dur ${"%.2f".format(it)}s" }.orEmpty()
            add("${i + 1}. t=${"%.2f".format(a.atSeconds)}s | ${a.type.label}$dur | ${a.note}")
        }
    }
}

object GeometryDashBotPlanner {
    fun createPreviewPlan(config: PhysicsConfig, traj: TrajectoryResult): BotPlan {
        return BotPlan("Preview", "Secuencia sugerida para ${config.mode.label} (config actual).",
            listOf(BotInputAction(0.0, BotInputType.TAP, note = "Activar ${config.trigger.label}")))
    }

    fun createRoutePlan(route: PathfinderResult): BotPlan {
        if (!route.found || route.actions.isEmpty()) return BotPlan("Route", "Sin ruta.", emptyList())
        val actions = route.actions.mapIndexed { i, a ->
            BotInputAction(i * 1.0, BotInputType.TAP, note = "Salto de ${a.fromPlatformId} -> ${a.toPlatformId}")
        }
        return BotPlan("Route", "Ruta de ${route.actions.size} saltos.", actions)
    }
}
