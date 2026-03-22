package com.pathfinder.gd

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class GameMode(val label: String) {
    CUBE("Cubo"),
    SHIP("Nave"),
    BALL("Bola"),
    UFO("UFO"),
    WAVE("Wave"),
    ROBOT("Robot"),
    SPIDER("Arana"),
    SWING("Swing"),
}

enum class SpeedProfile(val label: String, val multiplier: Double) {
    SLOW("Lenta 0.6x", 0.6),
    NORMAL("Normal 1.0x", 1.0),
    FAST("Rapida 1.3x", 1.3),
    VERY_FAST("Muy rapida 1.7x", 1.7),
    EXTREME("Extrema 2.1x", 2.1),
}

enum class TriggerType(val label: String, val impulseBlocks: Double, val flipsGravity: Boolean = false) {
    JUMP("Salto base", 2.125),
    YELLOW_ORB("Orbe amarillo", 3.5),
    PURPLE_ORB("Orbe morado", 2.0),
    RED_ORB("Orbe rojo", 6.0),
    GREEN_ORB("Orbe verde", 2.25, flipsGravity = true),
    BLUE_ORB("Orbe azul", 0.0, flipsGravity = true),
    BLACK_ORB("Orbe negro", -4.0),
    YELLOW_PAD("Pad amarillo", 5.0),
    PURPLE_PAD("Pad morado", 2.5),
    RED_PAD("Pad rojo", 9.0),
    BLUE_PAD("Pad azul", 0.0, flipsGravity = true),
}

data class PhysicsConfig(
    val mode: GameMode = GameMode.CUBE,
    val speed: SpeedProfile = SpeedProfile.NORMAL,
    val mini: Boolean = false,
    val invertedGravity: Boolean = false,
    val dualMode: Boolean = false,
    val twoPlayerSplit: Boolean = false,
    val trigger: TriggerType = TriggerType.JUMP,
    val inputStrength: Double = 0.5,
)

data class TrajectoryPoint(val x: Double, val y: Double)

data class TrajectoryResult(
    val points: List<TrajectoryPoint>,
    val peakHeightBlocks: Double,
    val horizontalDistanceBlocks: Double,
    val airtimeSeconds: Double,
    val summary: String,
    val detailLines: List<String>,
)

object GeometryDashPhysics {
    private const val BLOCKS_TO_PIXELS = 48.0
    private const val TIME_STEP = 1.0 / 120.0
    private const val MAX_TIME = 3.0
    private const val BASE_HORIZONTAL_SPEED = 5.36

    fun simulate(config: PhysicsConfig): TrajectoryResult {
        // Simplified emulator for UI preview, matches some gd-sim logic
        val gravityMagnitude = 10.0 * if (config.mini) 1.2 else 1.0
        val gravity = if (config.invertedGravity) gravityMagnitude else -gravityMagnitude
        val horizontalVelocity = BASE_HORIZONTAL_SPEED * config.speed.multiplier
        
        var vx = horizontalVelocity
        var vy = if (config.invertedGravity) -sqrt(2 * gravityMagnitude * config.trigger.impulseBlocks) 
                 else sqrt(2 * gravityMagnitude * config.trigger.impulseBlocks)
        
        if (config.mode == GameMode.SHIP) vy = 0.0 // ship starts flat if no trigger
        
        var x = 0.0
        var y = 0.0
        var elapsed = 0.0
        val pts = mutableListOf(TrajectoryPoint(0.0, 0.0))
        var peak = 0.0
        
        while (elapsed < MAX_TIME) {
            x += vx * TIME_STEP
            y += vy * TIME_STEP
            vy += gravity * TIME_STEP
            elapsed += TIME_STEP
            peak = if (config.invertedGravity) minOf(peak, y) else maxOf(peak, y)
            pts.add(TrajectoryPoint(x, y))
            if (elapsed > 0.05 && ((!config.invertedGravity && y <= 0) || (config.invertedGravity && y >= 0))) {
                pts.add(TrajectoryPoint(x, 0.0))
                break
            }
        }
        
        return TrajectoryResult(
            pts, abs(peak), pts.last().x, elapsed,
            "${config.mode.label} | ${config.trigger.label} | Dist: %.2f".format(pts.last().x),
            listOf("Modo: ${config.mode.label}", "Altura: %.2f".format(abs(peak)), "Tiempo: %.2fs".format(elapsed))
        )
    }

    fun toCanvasPoints(points: List<TrajectoryPoint>, heightPx: Int): List<Pair<Float, Float>> {
        val baseline = heightPx * 0.82f
        return points.map { (it.x * BLOCKS_TO_PIXELS).toFloat() to (baseline - (it.y * BLOCKS_TO_PIXELS)).toFloat() }
    }
}
