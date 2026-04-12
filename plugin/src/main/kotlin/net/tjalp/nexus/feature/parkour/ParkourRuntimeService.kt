package net.tjalp.nexus.feature.parkour

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.empty
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.title.Title.Times.times
import net.kyori.adventure.title.Title.title
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.parkour.ParkourSegmentResultsTable
import net.tjalp.nexus.util.asEventMessage
import net.tjalp.nexus.util.profile
import net.tjalp.nexus.util.sendActionBarTo
import net.tjalp.nexus.util.sendTo
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaDuration

/** Manages active parkour sessions, node entry handling, and segment result persistence. */
@OptIn(ExperimentalTime::class)
class ParkourRuntimeService(private val feature: ParkourFeature) {

    private val sessions = mutableMapOf<UUID, RunSession>()
    private val chunkIndex = mutableMapOf<String, MutableList<ParkourNode>>()

    fun rebuildIndex() {
        chunkIndex.clear()
        feature.definitions.definition.nodes.forEach(::indexNode)
    }

    fun reindex() = rebuildIndex()

    private fun indexNode(node: ParkourNode) {
        val r = node.region
        val minCX = r.minX shr 4
        val maxCX = r.maxX shr 4
        val minCZ = r.minZ shr 4
        val maxCZ = r.maxZ shr 4
        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                val key = "${r.worldId}:$cx:$cz"
                chunkIndex.getOrPut(key) { mutableListOf() } += node
            }
        }
    }

    fun getSession(playerId: UUID): RunSession? = sessions[playerId]
    fun hasSession(playerId: UUID): Boolean = sessions.containsKey(playerId)

    fun startSession(player: Player, entryNode: ParkourNode) {
        val now = System.currentTimeMillis()
        val session = RunSession(
            playerId = player.uniqueId,
            currentNodeId = entryNode.id,
            runStartMs = now,
            currentSegmentStartMs = now,
            lastCheckpointMs = now,
            lastEntrypointMs = now,
            path = mutableListOf(entryNode.id),
            segmentTimings = mutableListOf()
        )
        sessions[player.uniqueId] = session

        text("Parkour started").asEventMessage().sendTo(player)
    }

    fun stopSession(player: Player) {
        sessions.remove(player.uniqueId)
        player.sendActionBar(Component.empty())

        text("Parkour session stopped", RED).asEventMessage().sendTo(player)
    }

    fun onNodeEntered(player: Player, node: ParkourNode) {
        val definition = feature.definitions.definition
        val session = sessions[player.uniqueId]
        if (session == null) {
            if (node.type == NodeType.ENTRY) startSession(player, node)
            return
        }

        if (node.id == session.currentNodeId) return
        val segment = definition.findSegment(session.currentNodeId, node.id) ?: return

        val now = System.currentTimeMillis()
        val duration = now - session.currentSegmentStartMs
        val timing = SegmentTiming(segmentId = segment.id, durationMs = duration)

        session.path += node.id
        session.segmentTimings += timing
        session.currentSegmentStartMs = now
        session.currentNodeId = node.id
        session.lastCheckpointMs = now
        if (node.type == NodeType.ENTRY) session.lastEntrypointMs = now

        handleSegmentSplit(player, timing, now)

        if (node.type == NodeType.FINISH) {
            finishSession(player, session)
            return
        }

        sendSplitActionBar(player, session)
    }

    private fun handleSegmentSplit(player: Player, timing: SegmentTiming, finishedAtMs: Long) {
        val profileId = runCatching { player.profile().id }.getOrNull()
        if (profileId == null) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.5f)

            text("Segment split: ⌚ ${formatDuration(timing.durationMs)}").asEventMessage()
                .sendTo(player)
            return
        }

        feature.scheduler.launch {
            try {
                val startedAtMs = finishedAtMs - timing.durationMs
                val outcome = transaction(NexusPlugin.database) {
                    val priorForPlayer = ParkourSegmentResultsTable
                        .selectAll()
                        .where {
                            (ParkourSegmentResultsTable.profileId eq profileId) and
                                    (ParkourSegmentResultsTable.segmentId eq timing.segmentId)
                        }.minOfOrNull { it[ParkourSegmentResultsTable.durationMs] }

                    val priorGlobal = ParkourSegmentResultsTable
                        .selectAll()
                        .where { ParkourSegmentResultsTable.segmentId eq timing.segmentId }
                        .minOfOrNull { it[ParkourSegmentResultsTable.durationMs] }

                    ParkourSegmentResultsTable.insert {
                        it[ParkourSegmentResultsTable.profileId] = profileId
                        it[ParkourSegmentResultsTable.segmentId] = timing.segmentId
                        it[ParkourSegmentResultsTable.durationMs] = timing.durationMs
                        it[ParkourSegmentResultsTable.startedAt] = Instant.fromEpochMilliseconds(startedAtMs)
                        it[ParkourSegmentResultsTable.finishedAt] = Instant.fromEpochMilliseconds(finishedAtMs)
                    }

                    val isPersonalBest = priorForPlayer == null || timing.durationMs < priorForPlayer
                    val isGlobalBest = priorGlobal == null || timing.durationMs < priorGlobal
                    SplitOutcome(isPersonalBest, isGlobalBest)
                }

                title(
                    empty(),
                    text("⌚ ${formatDuration(timing.durationMs)}", PRIMARY_COLOR),
                    times(Duration.ZERO, 1.seconds.toJavaDuration(), 1.seconds.toJavaDuration())
                ).sendTo(player)

                when {
                    outcome.isGlobalBest -> {
                        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f)
                        text("New global segment record! ⌚ ${formatDuration(timing.durationMs)}")
                            .asEventMessage()
                            .sendTo(player)
                    }

                    outcome.isPersonalBest -> {
                        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f)
                        text("New segment PB: ⌚ ${formatDuration(timing.durationMs)}").asEventMessage()
                            .sendTo(player)
                    }

                    else -> {
                        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.5f)
                    }
                }
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Parkour] Failed to save segment result: ${e.message}")
            }
        }
    }

    private fun finishSession(player: Player, session: RunSession) {
        sessions.remove(player.uniqueId)
        player.sendActionBar(Component.empty())
        val totalDurationMs = session.segmentTimings.sumOf { it.durationMs }
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f)
        text("Parkour finished! Time: ⌚ ${formatDuration(totalDurationMs)}").asEventMessage()
            .sendTo(player)
    }

    fun getNodesAt(worldId: UUID, x: Int, y: Int, z: Int): List<ParkourNode> {
        val cx = x shr 4
        val cz = z shr 4
        val key = "$worldId:$cx:$cz"
        val candidates = chunkIndex[key] ?: return emptyList()
        return candidates.filter { node -> node.region.contains(x, y, z) }
    }

    fun sendSplitActionBar(player: Player, session: RunSession) {
        val total = formatDuration(session.elapsedMs)
        val checkpoint = formatDuration(session.checkpointSplitMs)
        val entry = formatDuration(session.entrySplitMs)
        val currentSegment = formatDuration(System.currentTimeMillis() - session.currentSegmentStartMs)

        text(
            "Run: ⌚ $total  |  Segment: ⌚ $currentSegment  |  Checkpoint: ⌚ $checkpoint  |  Entry: ⌚ $entry",
            PRIMARY_COLOR
        ).sendActionBarTo(player)
    }

    fun tickActionBars() {
        sessions.forEach { (playerId, session) ->
            val player = NexusPlugin.server.getPlayer(playerId) ?: return@forEach
            sendSplitActionBar(player, session)
        }
    }

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1_000
        // Single decimal precision (tenths of a second) for compact split displays.
        val millis = (ms % 1_000) / 100
        return "%02d:%02d.%d".format(minutes, seconds, millis)
    }

    fun clearPlayerSession(playerId: UUID) {
        sessions.remove(playerId)
    }
}

private data class SplitOutcome(
    val isPersonalBest: Boolean,
    val isGlobalBest: Boolean
)
