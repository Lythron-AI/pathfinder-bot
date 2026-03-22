package com.pathfinder.gd

/**
 * Shared store to keep the current active plan accessible to the BotAccessibilityService.
 */
object BotExecutionStore {
    var currentPlan: BotPlan? = null
        private set

    fun update(newPlan: BotPlan) {
        currentPlan = newPlan
    }
}
