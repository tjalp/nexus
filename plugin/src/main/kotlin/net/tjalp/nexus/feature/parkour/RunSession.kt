@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.feature.parkour

import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents an active parkour run for a single player.
 *
 * A session operates in freestyle mode: every valid node transition is recorded.
 */
data class RunSession(
    val playerId: UUID,
    var currentNodeKey: String,
    val runStartTime: Instant = Clock.System.now(),
    var currentSegmentStartTime: Instant = Clock.System.now(),
    var lastCheckpointTime: Instant = Clock.System.now(),
    var lastEntrypointTime: Instant = Clock.System.now(),
    val path: MutableList<String> = mutableListOf(),
    val segmentTimings: MutableList<SegmentTiming> = mutableListOf()
) {
    val elapsedTime: Duration get() = Clock.System.now() - runStartTime
    val checkpointSplitTime: Duration get() = Clock.System.now() - lastCheckpointTime
    val entrySplitTime: Duration get() = Clock.System.now() - lastEntrypointTime
}

data class SegmentTiming(
    val segmentKey: String,
    val duration: Duration
)
