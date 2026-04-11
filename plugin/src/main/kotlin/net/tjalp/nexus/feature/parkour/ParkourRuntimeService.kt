package net.tjalp.nexus.feature.parkour

import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.parkour.ParkourSegmentResultsTable
import net.tjalp.nexus.profile.attachment.ParkourAttachment
import net.tjalp.nexus.profile.attachment.ParkourAttachmentTable
import net.tjalp.nexus.profile.attachment.PinnedRoute
import net.tjalp.nexus.util.profile
import org.bukkit.entity.Player
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.security.MessageDigest
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Manages active parkour run sessions, chunk-indexed triggers, segment timing, and result persistence.
 */
@OptIn(ExperimentalTime::class)
class ParkourRuntimeService(private val feature: ParkourFeature) {

    private val sessions = mutableMapOf<UUID, RunSession>()
    private val chunkIndex = mutableMapOf<String, MutableList<Pair<ParkourDefinition, ParkourNode>>>()

    fun rebuildIndex() {
        chunkIndex.clear()
        feature.definitions.allNodes().forEach { (parkour, node) ->
            indexNode(parkour, node)
        }
    }

    private fun indexNode(parkour: ParkourDefinition, node: ParkourNode) {
        val r = node.region
        val minCX = r.minX shr 4
        val maxCX = r.maxX shr 4
        val minCZ = r.minZ shr 4
        val maxCZ = r.maxZ shr 4
        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                val key = "${r.worldId}:$cx:$cz"
                chunkIndex.getOrPut(key) { mutableListOf() } += parkour to node
            }
        }
    }

    fun reindexParkour(parkour: ParkourDefinition) {
        chunkIndex.values.forEach { list ->
            list.removeAll { (p, _) -> p.id == parkour.id }
        }
        parkour.nodes.forEach { indexNode(parkour, it) }
    }

    fun getSession(playerId: UUID): RunSession? = sessions[playerId]
    fun hasSession(playerId: UUID): Boolean = sessions.containsKey(playerId)

    /**
     * Starts a new session at [entryNode]. If [selectedRoute] is provided it is tracked,
     * otherwise a pinned route for this entry node is used when available.
     */
    fun startSession(
        player: Player,
        parkour: ParkourDefinition,
        entryNode: ParkourNode,
        selectedRoute: ParkourRoute? = null
    ) {
        val route = selectedRoute ?: resolvePinnedRoute(player, parkour, entryNode.id)

        val session = RunSession(
            playerId = player.uniqueId,
            parkourId = parkour.id,
            currentNodeId = entryNode.id,
            runStartMs = System.currentTimeMillis(),
            currentSegmentStartMs = System.currentTimeMillis(),
            lastCheckpointMs = System.currentTimeMillis(),
            lastEntrypointMs = System.currentTimeMillis(),
            path = mutableListOf(entryNode.id),
            segmentTimings = mutableListOf(),
            activeRouteKey = route?.let { computeRouteKey(parkour.id, it.segmentIds) },
            activeRouteName = route?.name,
            activeRouteSegmentIds = route?.segmentIds?.toList(),
            activeRouteIndex = -1
        )
        sessions[player.uniqueId] = session

        val msg = if (route != null) {
            text("Parkour started! Tracking route '${route.name}'.", NamedTextColor.GREEN)
        } else {
            text("Parkour started! No route selected – freestyle mode.", NamedTextColor.GREEN)
        }
        player.sendMessage(msg)
    }

    fun stopSession(player: Player) {
        sessions.remove(player.uniqueId)
        player.sendActionBar(Component.empty())
        player.sendMessage(text("Parkour session stopped.", NamedTextColor.YELLOW))
    }

    fun onNodeEntered(player: Player, parkour: ParkourDefinition, node: ParkourNode) {
        val session = sessions[player.uniqueId]
        if (session == null) {
            if (node.type == NodeType.ENTRY) {
                startSession(player, parkour, node)
            }
            return
        }

        if (session.parkourId != parkour.id) return
        if (node.id == session.currentNodeId) return

        val segment = parkour.findSegment(session.currentNodeId, node.id) ?: return
        val now = System.currentTimeMillis()

        session.path += node.id
        session.lastCheckpointMs = now
        if (node.type == NodeType.ENTRY) {
            session.lastEntrypointMs = now
        }

        if (session.hasActiveRoute) {
            if (!session.isNextRouteSegment(segment.id)) return
            session.advanceRoute(segment.id)
        } else {
            session.segmentTimings += SegmentTiming(segment.id, now - session.currentSegmentStartMs)
            session.currentSegmentStartMs = now
        }

        session.currentNodeId = node.id

        if (node.type == NodeType.FINISH) {
            if (session.hasActiveRoute) {
                if (session.isRouteComplete) {
                    finishSession(player, session, parkour)
                    return
                }
            } else {
                finishSession(player, session, parkour)
                return
            }
        }

        sendSplitActionBar(player, session)
    }

    private fun finishSession(player: Player, session: RunSession, parkour: ParkourDefinition) {
        sessions.remove(player.uniqueId)
        val finishedAt = System.currentTimeMillis()
        val totalDurationMs = session.segmentTimings.sumOf { it.durationMs }

        val profileId = try {
            player.profile().id
        } catch (e: IllegalStateException) {
            NexusPlugin.logger.warning("[Parkour] No profile for ${player.name}, result not saved.")
            player.sendActionBar(Component.empty())
            player.sendMessage(
                text("Parkour finished! Time: ${formatDuration(totalDurationMs)}", NamedTextColor.GOLD)
            )
            return
        }

        val routeKey = session.activeRouteKey ?: computeRouteKey(
            parkour.id,
            session.segmentTimings.map { it.segmentId }
        )

        feature.scheduler.launch {
            try {
                transaction(NexusPlugin.database) {
                    session.segmentTimings.forEachIndexed { idx, timing ->
                        val segment = parkour.segmentById(timing.segmentId)
                        ParkourSegmentResultsTable.insert {
                            it[ParkourSegmentResultsTable.profileId] = profileId
                            it[ParkourSegmentResultsTable.parkourId] = parkour.id
                            it[ParkourSegmentResultsTable.routeKey] = routeKey
                            it[ParkourSegmentResultsTable.routeName] = session.activeRouteName
                            it[ParkourSegmentResultsTable.segmentId] = timing.segmentId
                            it[ParkourSegmentResultsTable.segmentName] = segment?.name ?: timing.segmentId.toString()
                            it[ParkourSegmentResultsTable.segmentOrder] = idx
                            it[ParkourSegmentResultsTable.durationMs] = timing.durationMs
                            it[ParkourSegmentResultsTable.startedAt] = Instant.fromEpochMilliseconds(session.runStartMs)
                            it[ParkourSegmentResultsTable.finishedAt] = Instant.fromEpochMilliseconds(finishedAt)
                        }
                    }
                }
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Parkour] Failed to save segment run results: ${e.message}")
            }
        }

        player.sendActionBar(Component.empty())
        player.sendMessage(
            text("Parkour finished! Time: ${formatDuration(totalDurationMs)}", NamedTextColor.GOLD)
        )
    }

    fun getNodesAt(worldId: UUID, x: Int, y: Int, z: Int): List<Pair<ParkourDefinition, ParkourNode>> {
        val cx = x shr 4
        val cz = z shr 4
        val key = "$worldId:$cx:$cz"
        val candidates = chunkIndex[key] ?: return emptyList()
        return candidates.filter { (_, node) -> node.region.contains(x, y, z) }
    }

    private fun resolvePinnedRoute(player: Player, parkour: ParkourDefinition, entryNodeId: UUID): ParkourRoute? {
        val pinnedRoute = getPinnedRoute(player, entryNodeId) ?: return null
        val segmentIds = pinnedRoute.segmentIds.mapNotNull {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        if (segmentIds.isEmpty()) return null

        return ParkourRoute(
            name = pinnedRoute.routeName ?: "Player route",
            segmentIds = segmentIds.toMutableList(),
            predefined = false
        )
    }

    private fun getPinnedRoute(player: Player, nodeId: UUID): PinnedRoute? {
        return try {
            val attachment = player.profile().attachmentOf<ParkourAttachment>() ?: return null
            attachment.pinnedRoutes[nodeId.toString()]
        } catch (_: Exception) {
            null
        }
    }

    fun pinRoute(
        player: Player,
        parkourId: UUID,
        routeName: String?,
        entryNodeId: UUID,
        segmentIds: List<UUID>
    ): String {
        val routeKey = computeRouteKey(parkourId, segmentIds)
        val segmentIdStrings = segmentIds.map { it.toString() }
        val profileId = player.profile().id

        feature.scheduler.launch {
            try {
                transaction(NexusPlugin.database) {
                    ParkourAttachmentTable.upsert {
                        it[ParkourAttachmentTable.profileId] = profileId
                        it[ParkourAttachmentTable.entryNodeId] = entryNodeId
                        it[ParkourAttachmentTable.routeKey] = routeKey
                        it[ParkourAttachmentTable.routeSequence] = Json.encodeToString(
                            ListSerializer(String.serializer()),
                            segmentIdStrings
                        )
                    }
                }
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Parkour] Failed to pin route: ${e.message}")
            }
        }

        return routeKey
    }

    fun unpinRoute(player: Player, entryNodeId: UUID) {
        val profileId = player.profile().id
        feature.scheduler.launch {
            try {
                transaction(NexusPlugin.database) {
                    ParkourAttachmentTable.deleteWhere {
                        (ParkourAttachmentTable.profileId eq profileId) and
                                (ParkourAttachmentTable.entryNodeId eq entryNodeId)
                    }
                }
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Parkour] Failed to unpin route: ${e.message}")
            }
        }
    }

    fun sendSplitActionBar(player: Player, session: RunSession) {
        val total = formatDuration(session.elapsedMs)
        val checkpoint = formatDuration(session.checkpointSplitMs)
        val entry = formatDuration(session.entrySplitMs)
        val currentSegment = formatDuration(System.currentTimeMillis() - session.currentSegmentStartMs)
        val bar = text("Run: $total  |  Segment: $currentSegment  |  Checkpoint: $checkpoint  |  Entry: $entry", NamedTextColor.AQUA)
        player.sendActionBar(bar)
    }

    fun tickActionBars() {
        sessions.forEach { (playerId, session) ->
            val player = NexusPlugin.server.getPlayer(playerId) ?: return@forEach
            sendSplitActionBar(player, session)
        }
    }

    fun computeRouteKey(parkourId: UUID, segmentIds: List<UUID>): String {
        val input = parkourId.toString() + segmentIds.joinToString(",")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    fun routeDurationMs(session: RunSession): Long = session.segmentTimings.sumOf { it.durationMs }

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000 / 100
        return "%02d:%02d.%d".format(minutes, seconds, millis)
    }

    fun clearPlayerSession(playerId: UUID) {
        sessions.remove(playerId)
    }
}
