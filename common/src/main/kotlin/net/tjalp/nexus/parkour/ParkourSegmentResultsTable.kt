@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.tjalp.nexus.parkour

import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Stores completed segment timings for parkour runs.
 * Route totals are derived at runtime by summing segment durations.
 */
object ParkourSegmentResultsTable : UUIDTable("parkour_segment_results") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val segmentId = uuid("segment_id")
    val durationMs = long("duration_ms")
    val startedAt = timestamp("started_at")
    val finishedAt = timestamp("finished_at")
}
