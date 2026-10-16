package net.tjalp.nexus.feature.parkour

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.model.ParkourSegmentResultsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Repository for accessing parkour leaderboard data from the database.
 */
object ParkourLeaderboardRepository {

    private val db; get() = NexusPlugin.database

    /**
     * Retrieves the top leaderboard entries for a specific parkour segment.
     *
     * @param segmentKey The unique key identifying the parkour segment.
     * @param limit The maximum number of leaderboard entries to retrieve.
     * @return An ordered list of [ParkourLeaderboardEntry] objects representing the top entries.
     */
    suspend fun getTop(segmentKey: String, limit: Int) = suspendTransaction(db) {
        val results = ParkourSegmentResultsTable
            .select(
                ParkourSegmentResultsTable.profileId,
                ParkourSegmentResultsTable.duration,
                ParkourSegmentResultsTable.finishedAt
            )
            .where { ParkourSegmentResultsTable.segmentKey eq segmentKey }
            .orderBy(ParkourSegmentResultsTable.duration, order = SortOrder.ASC)
            .limit(limit)
            .map { row ->
                ParkourLeaderboardEntry(
                    profileId = row[ParkourSegmentResultsTable.profileId].value,
                    duration = row[ParkourSegmentResultsTable.duration],
                    finishedAt = row[ParkourSegmentResultsTable.finishedAt]
                )
            }

        return@suspendTransaction results
    }
}

data class ParkourLeaderboardEntry(
    val profileId: UUID,
    val duration: Duration,
    val finishedAt: Instant
)