package com.pathfinder.gd

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Kotlin interface to the native pathfinder A* engine (gd-sim + A*).
 *
 * Usage:
 *   PathfinderEngine.runAsync(lvlString,
 *       onProgress = { pct -> ... },
 *       onDone = { gdr2Bytes -> ... }
 *   )
 */
object PathfinderEngine {

    private const val TAG = "PathfinderEngine"
    private val callbackCounter = AtomicLong(0)

    // Pending callbacks keyed by callbackId
    private val pendingProgress = mutableMapOf<Long, (Double) -> Unit>()
    private val pendingDone     = mutableMapOf<Long, (ByteArray) -> Unit>()

    init {
        System.loadLibrary("pathfinder_jni")
        Log.i(TAG, "Native library loaded")
    }

    // ── Native declarations ───────────────────────────────────────────────────

    @JvmStatic private external fun pathfindSync(lvlString: String): ByteArray
    @JvmStatic private external fun pathfindAsync(lvlString: String, callbackId: Long)
    @JvmStatic external fun cancelPathfind()
    @JvmStatic external fun injectUltra()

    // ── JNI callbacks (called from native thread) ─────────────────────────────


    /** Called by native code on progress update */
    @JvmStatic
    fun onProgress(callbackId: Long, percent: Double) {
        pendingProgress[callbackId]?.invoke(percent)
    }

    /** Called by native code when pathfinding is done */
    @JvmStatic
    fun onDone(callbackId: Long, result: ByteArray) {
        pendingDone[callbackId]?.invoke(result)
        pendingProgress.remove(callbackId)
        pendingDone.remove(callbackId)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run pathfinder synchronously on the current thread (should be a background thread).
     * Returns .gdr2 bytes or empty array if failed.
     */
    fun runSync(lvlString: String): ByteArray = pathfindSync(lvlString)

    /**
     * Run pathfinder asynchronously using native thread pool.
     * [onProgress] is called from a native thread — marshall to main thread as needed.
     * [onDone] is called from a native thread with the final .gdr2 bytes.
     */
    fun runAsync(
        lvlString: String,
        onProgress: (Double) -> Unit = {},
        onDone: (ByteArray) -> Unit,
    ) {
        val id = callbackCounter.incrementAndGet()
        pendingProgress[id] = onProgress
        pendingDone[id] = onDone
        Log.i(TAG, "Starting async pathfind id=$id, lvl size=${lvlString.length}")
        pathfindAsync(lvlString, id)
    }

    /**
     * Cancel the current async run (sets the stop flag in native code).
     */
    fun cancel() { cancelPathfind() }
}
