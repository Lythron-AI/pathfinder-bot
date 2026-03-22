package com.pathfinder.gd

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class TrajectoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E4A66"); strokeWidth = 1f }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F"); strokeWidth = 7f; style = Paint.Style.STROKE }
    private val landingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6F61"); style = Paint.Style.FILL }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80CBC4"); strokeWidth = 5f }
    private val platformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6DD3CE"); style = Paint.Style.FILL }
    private val spikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6B6B"); style = Paint.Style.FILL }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8BE9FD"); strokeWidth = 5f; style = Paint.Style.STROKE }

    private var points: List<Pair<Float, Float>> = emptyList()
    private var scenario: PathfinderScenario? = null
    private var routedPaths: List<List<Pair<Float, Float>>> = emptyList()

    fun submitTrajectory(result: TrajectoryResult) {
        points = GeometryDashPhysics.toCanvasPoints(result.points, height)
        routedPaths = emptyList()
        invalidate()
    }

    fun submitScenario(result: TrajectoryResult, scenario: PathfinderScenario?, route: PathfinderResult?) {
        this.scenario = scenario
        points = GeometryDashPhysics.toCanvasPoints(result.points, height)
        routedPaths = route?.actions.orEmpty().map { GeometryDashPhysics.toCanvasPoints(it.shiftedPoints, height) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val baseline = height * 0.82f
        drawGrid(canvas)
        canvas.drawLine(0f, baseline, width.toFloat(), baseline, baselinePaint)
        scenario?.let { drawScenario(canvas, it, baseline) }
        if (points.isEmpty()) return
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second) }
        canvas.drawPath(path, pathPaint)
        routedPaths.forEach { r ->
            val rp = Path()
            r.forEachIndexed { i, p -> if (i == 0) rp.moveTo(p.first, p.second) else rp.lineTo(p.first, p.second) }
            canvas.drawPath(rp, routePaint)
        }
        val end = points.last()
        canvas.drawCircle(end.first, end.second, 10f, landingPaint)
    }

    private fun drawScenario(canvas: Canvas, scenario: PathfinderScenario, baseline: Float) {
        val block = 48f
        scenario.platforms.forEach {
            val top = baseline - (it.y * block) - 10f
            val rect = RectF((it.startX * block).toFloat(), top, (it.endX * block).toFloat(), top + 14f)
            canvas.drawRoundRect(rect, 8f, 8f, platformPaint)
        }
        scenario.spikes.forEach {
            val left = (it.x * block).toFloat(); val right = ((it.x + it.width) * block).toFloat()
            val bottom = (baseline - (it.y * block)).toFloat()
            val top = (bottom - (it.height * block).toFloat()); val sp = Path().apply { moveTo(left, bottom); lineTo((left + right) / 2f, top); lineTo(right, bottom); close() }
            canvas.drawPath(sp, spikePaint)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val step = 48; var x = 0; while (x <= width) { canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint); x += step }
        var y = 0; while (y <= height) { canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint); y += step }
    }
}
