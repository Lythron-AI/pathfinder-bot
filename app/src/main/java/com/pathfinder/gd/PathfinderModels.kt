package com.pathfinder.gd

import kotlin.math.abs

data class PlatformSegment(val id: String, val startX: Double, val endX: Double, val y: Double)
data class SpikeHazard(val x: Double, val y: Double, val width: Double = 1.0, val height: Double = 1.0)
data class PathfinderScenario(
    val label: String,
    val platforms: List<PlatformSegment>,
    val spikes: List<SpikeHazard>,
    val startPlatformId: String,
    val goalPlatformId: String,
)
data class PathAction(
    val config: PhysicsConfig,
    val fromPlatformId: String,
    val toPlatformId: String,
    val shiftedPoints: List<TrajectoryPoint>,
)
data class PathfinderResult(
    val found: Boolean,
    val actions: List<PathAction>,
    val detailLines: List<String>,
)

object ScenarioLibrary {
    val scenarios = listOf(
        PathfinderScenario("Intro Jump", listOf(PlatformSegment("start", 0.0, 2.0, 0.0), PlatformSegment("mid1", 4.7, 6.6, 1.0), PlatformSegment("goal", 13.0, 16.5, 0.0)), listOf(SpikeHazard(3.1, 0.0)), "start", "goal")
    )
}

object GeometryDashPathfinder {
    fun findRoute(baseConfig: PhysicsConfig, scenario: PathfinderScenario): PathfinderResult {
        return PathfinderResult(false, emptyList(), listOf("Kotlin BFS deshabilitado en favor del engine nativo C++."))
    }
}
