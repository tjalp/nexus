package net.tjalp.nexus.feature.parkour

import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.format.NamedTextColor.WHITE
import net.kyori.adventure.text.`object`.ObjectContents.playerHead
import net.tjalp.nexus.api.leaderboard.LeaderboardScore
import net.tjalp.nexus.api.leaderboard.LeaderboardScoreSource
import net.tjalp.nexus.profile.attachment.GeneralTable
import net.tjalp.nexus.profile.model.ParkourSegmentResultsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Duration

class ParkourLeaderboardSource(val segmentKey: String) : LeaderboardScoreSource {

    override suspend fun getScores(limit: Int): List<LeaderboardScore> = suspendTransaction {
        val bestPerProfile = ParkourSegmentResultsTable
            .join(
                GeneralTable,
                JoinType.LEFT,
                ParkourSegmentResultsTable.profileId,
                GeneralTable.profileId
            )
            .select(
                ParkourSegmentResultsTable.profileId,
                ParkourSegmentResultsTable.duration,
                ParkourSegmentResultsTable.finishedAt,
                GeneralTable.lastKnownName
            )
            .where { ParkourSegmentResultsTable.segmentKey eq segmentKey }
            .withDistinctOn(ParkourSegmentResultsTable.profileId)
            .orderBy(
                ParkourSegmentResultsTable.profileId to SortOrder.ASC,
                ParkourSegmentResultsTable.duration to SortOrder.ASC,
                ParkourSegmentResultsTable.finishedAt to SortOrder.ASC
            )
            .alias("best_per_profile")

        val profileId = bestPerProfile[ParkourSegmentResultsTable.profileId]
        val duration = bestPerProfile[ParkourSegmentResultsTable.duration]
        val finishedAt = bestPerProfile[ParkourSegmentResultsTable.finishedAt]
        val lastKnownName = bestPerProfile[GeneralTable.lastKnownName]

        val results = bestPerProfile
            .selectAll()
            .orderBy(
                duration to SortOrder.ASC,
                finishedAt to SortOrder.ASC
            )
            .limit(limit)
            .map { row ->
                val display = textOfChildren(
                    `object`(playerHead(row[profileId].value)).color(WHITE),
                    space(),
                    text(row[lastKnownName] ?: "<unknown>")
                )

                LeaderboardScore(
                    display = display,
                    value = text("⌚ " + formatDuration(row[duration]))
                )
            }

        return@suspendTransaction results
    }

    private fun formatDuration(duration: Duration): String {
        val totalMillis = duration.inWholeMilliseconds
        val minutes = totalMillis / 60_000
        val seconds = (totalMillis / 1_000) % 60
        val millis = totalMillis % 1_000

        return "%02d:%02d.%03d".format(minutes, seconds, millis)
    }
}