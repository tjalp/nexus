@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.parkour

import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

/**
 * Table that stores completed parkour run results.
 * Keyed by a deterministic route key (hash of parkourId + ordered nodeIds).
 */
object ParkourResultsTable : UUIDTable("parkour_results") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val parkourId = uuid("parkour_id")
    val routeKey = varchar("route_key", 64)
    val routeSequence = text("route_sequence")
    val startedAt = timestamp("started_at")
    val finishedAt = timestamp("finished_at")
    val durationMs = long("duration_ms")
}
