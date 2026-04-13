@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.profile.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

/**
 * Stores completed segment timings for parkour runs.
 * Route totals are derived at runtime by summing segment durations.
 */
object ParkourSegmentResultsTable : UUIDTable("parkour_segment_results") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val segmentKey = varchar("segment_key", 32)
    val durationMs = long("duration_ms")
    val startedAt = timestamp("started_at")
    val finishedAt = timestamp("finished_at")
}