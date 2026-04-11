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
    val parkourId = uuid("parkour_id")
    val routeKey = varchar("route_key", 64)
    val routeName = varchar("route_name", 128).nullable()
    val segmentId = uuid("segment_id")
    val segmentName = varchar("segment_name", 128)
    val segmentOrder = integer("segment_order")
    val durationMs = long("duration_ms")
    val startedAt = timestamp("started_at")
    val finishedAt = timestamp("finished_at")
}
