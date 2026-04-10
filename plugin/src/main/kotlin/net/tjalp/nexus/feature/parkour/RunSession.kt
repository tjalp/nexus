package net.tjalp.nexus.feature.parkour

import java.util.*

/**
 * Represents an active parkour run for a single player.
 *
 * A session always operates in "freestyle" mode: every valid node transition
 * is recorded in [path] and split times are tracked.  If the player has a route
 * pinned starting at [currentNodeId], [activeRouteSequence] and [activeRouteIndex]
 * are populated so we can detect route completion and auto-finish.
 */
data class RunSession(
    val playerId: UUID,
    val parkourId: UUID,
    var currentNodeId: UUID,
    val runStartMs: Long = System.currentTimeMillis(),
    var lastCheckpointMs: Long = System.currentTimeMillis(),
    var lastEntrypointMs: Long = System.currentTimeMillis(),
    val path: MutableList<UUID> = mutableListOf(),
    /** Non-null when the player has a pinned route that started at the first entry node. */
    val activeRouteKey: String? = null,
    val activeRouteSequence: List<UUID>? = null,
    var activeRouteIndex: Int = 0
) {
    val elapsedMs: Long get() = System.currentTimeMillis() - runStartMs
    val checkpointSplitMs: Long get() = System.currentTimeMillis() - lastCheckpointMs
    val entrySplitMs: Long get() = System.currentTimeMillis() - lastEntrypointMs

    /** Returns true if the pinned route is currently being tracked. */
    val hasActiveRoute: Boolean get() = activeRouteSequence != null

    /**
     * Advances the pinned-route tracker by one node.
     * Should be called only when [toNodeId] matches the next expected node in the sequence.
     */
    fun advanceRoute() {
        activeRouteIndex++
    }

    /** Returns whether [toNodeId] is the next expected node in the pinned route. */
    fun isNextRouteNode(toNodeId: UUID): Boolean {
        if (activeRouteSequence == null) return false
        val nextIndex = activeRouteIndex + 1
        return nextIndex < activeRouteSequence.size && activeRouteSequence[nextIndex] == toNodeId
    }

    /** Returns true if the pinned route is now complete (finish node reached). */
    val isRouteComplete: Boolean
        get() = activeRouteSequence != null && activeRouteIndex >= activeRouteSequence.size - 1
}
