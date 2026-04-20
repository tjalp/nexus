@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.feature.parkour

import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents an active parkour run for a single player.
 *
 * A session operates in freestyle mode: every valid node transition is recorded.
 */
data class ParkourSession(
    val playerId: UUID,
    var currentNodeKey: String,
    var currentSegmentStartTime: Instant? = null,
    var lastCheckpointTime: Instant = Clock.System.now(),
    var lastEntrypointTime: Instant = Clock.System.now(),
    val path: MutableList<String> = mutableListOf(),
    val segmentTimings: MutableList<SegmentTiming> = mutableListOf()
) {

    val elapsedTime: Duration
        get() {
            val finishedTotal = segmentTimings.fold(ZERO) { acc, timing -> acc + timing.duration }
            val running = currentSegmentStartTime?.let { Clock.System.now() - it } ?: ZERO
            return finishedTotal + running
        }
}

data class SegmentTiming(
    val segmentKey: String,
    val duration: Duration
)
