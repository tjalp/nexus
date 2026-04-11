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
    var currentSegmentStartMs: Long = System.currentTimeMillis(),
    var lastCheckpointMs: Long = System.currentTimeMillis(),
    var lastEntrypointMs: Long = System.currentTimeMillis(),
    val path: MutableList<UUID> = mutableListOf(),
    val segmentTimings: MutableList<SegmentTiming> = mutableListOf(),
    /** Non-null when the player has a pinned route that started at the first entry node. */
    val activeRouteKey: String? = null,
    val activeRouteName: String? = null,
    val activeRouteSegmentIds: List<UUID>? = null,
    var activeRouteIndex: Int = -1
) {
    val elapsedMs: Long get() = System.currentTimeMillis() - runStartMs
    val checkpointSplitMs: Long get() = System.currentTimeMillis() - lastCheckpointMs
    val entrySplitMs: Long get() = System.currentTimeMillis() - lastEntrypointMs

    /** Returns true if the pinned route is currently being tracked. */
    val hasActiveRoute: Boolean get() = activeRouteSegmentIds != null

    /**
     * Advances the pinned-route tracker by one node.
     * Should be called only when [segmentId] matches the next expected segment in the sequence.
     */
    fun advanceRoute(segmentId: UUID) {
        val elapsedForSegment = System.currentTimeMillis() - currentSegmentStartMs
        segmentTimings += SegmentTiming(segmentId = segmentId, durationMs = elapsedForSegment)
        currentSegmentStartMs = System.currentTimeMillis()
        activeRouteIndex++
    }

    /** Returns whether [segmentId] is the next expected segment in the active route. */
    fun isNextRouteSegment(segmentId: UUID): Boolean {
        if (activeRouteSegmentIds == null) return false
        val nextIndex = activeRouteIndex + 1
        return nextIndex < activeRouteSegmentIds.size && activeRouteSegmentIds[nextIndex] == segmentId
    }

    /** Returns true if the pinned route is now complete (finish node reached). */
    val isRouteComplete: Boolean
        get() = activeRouteSegmentIds != null && activeRouteIndex >= activeRouteSegmentIds.size - 1
}

data class SegmentTiming(
    val segmentId: UUID,
    val durationMs: Long
)
