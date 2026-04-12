package net.tjalp.nexus.feature.parkour

import java.util.*

/**
 * Represents an active parkour run for a single player.
 *
 * A session operates in freestyle mode: every valid node transition is recorded.
 */
data class RunSession(
    val playerId: UUID,
    var currentNodeId: UUID,
    val runStartMs: Long = System.currentTimeMillis(),
    var currentSegmentStartMs: Long = System.currentTimeMillis(),
    var lastCheckpointMs: Long = System.currentTimeMillis(),
    var lastEntrypointMs: Long = System.currentTimeMillis(),
    val path: MutableList<UUID> = mutableListOf(),
    val segmentTimings: MutableList<SegmentTiming> = mutableListOf()
) {
    val elapsedMs: Long get() = System.currentTimeMillis() - runStartMs
    val checkpointSplitMs: Long get() = System.currentTimeMillis() - lastCheckpointMs
    val entrySplitMs: Long get() = System.currentTimeMillis() - lastEntrypointMs
}

data class SegmentTiming(
    val segmentId: UUID,
    val durationMs: Long
)
