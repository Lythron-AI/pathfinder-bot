package com.pathfinder.gd

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class BotAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    companion object {
        private const val TAG = "PathfinderBot"
        const val ACTION_START_PLAN = "com.pathfinder.gd.ACTION_START_PLAN"
        const val ACTION_STOP       = "com.pathfinder.gd.ACTION_STOP"
        const val EXTRA_TAP_X       = "tapX"
        const val EXTRA_TAP_Y       = "tapY"
        private const val PRE_START_MS = 3000L
    }

    private val planReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_START_PLAN -> {
                    val tapX = intent.getFloatExtra(EXTRA_TAP_X, 540f)
                    val tapY = intent.getFloatExtra(EXTRA_TAP_Y, 2000f)
                    BotExecutionStore.currentPlan?.let { schedulePlan(it, tapX, tapY) }
                        ?: Log.w(TAG, "No plan in store")
                }
                ACTION_STOP -> stopAll()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Connected")
        LocalBroadcastManager.getInstance(this).registerReceiver(
            planReceiver,
            IntentFilter().apply {
                addAction(ACTION_START_PLAN)
                addAction(ACTION_STOP)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(planReceiver)
        stopAll()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopAll() }

    // ── Scheduling ────────────────────────────────────────────────────────────

    private fun schedulePlan(plan: BotPlan, tapX: Float, tapY: Float) {
        stopAll()
        isRunning = true
        Log.d(TAG, "Scheduling ${plan.actions.size} actions, tapX=$tapX tapY=$tapY")

        plan.actions.forEach { action ->
            val delayMs = PRE_START_MS + (action.atSeconds * 1000.0).toLong()
            handler.postDelayed({
                if (!isRunning) return@postDelayed
                when (action.type) {
                    BotInputType.TAP     -> performTap(tapX, tapY, 50L)
                    BotInputType.HOLD    -> {
                        val holdMs = ((action.durationSeconds ?: 0.1) * 1000.0).toLong().coerceAtLeast(16L)
                        performTap(tapX, tapY, holdMs)
                    }
                    BotInputType.RELEASE -> { /* stroke ends on its own */ }
                }
                Log.d(TAG, "+${delayMs}ms → ${action.type} | ${action.note}")
            }, delayMs)
        }

        // Auto-stop 2s after last action
        val lastMs = plan.actions.maxOfOrNull { it.atSeconds }?.toLong()?.times(1000L) ?: 0L
        handler.postDelayed({ stopAll() }, PRE_START_MS + lastMs + 2000L)
    }

    private fun performTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) =
                    Log.v(TAG, "Tap OK ($x,$y) ${durationMs}ms")
                override fun onCancelled(g: GestureDescription) =
                    Log.w(TAG, "Tap cancelled ($x,$y)")
            },
            null
        )
    }

    private fun stopAll() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Stopped")
    }
}
