package net.tjalp.nexus.feature.parkour

import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.parkour.ParkourResultsTable
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
 * Manages active parkour run sessions, chunk-indexed triggers, and result persistence.
 */
@OptIn(ExperimentalTime::class)
class ParkourRuntimeService(private val feature: ParkourFeature) {

    /** Active sessions per player UUID. */
    private val sessions = mutableMapOf<UUID, RunSession>()

    /**
     * Chunk-indexed node triggers: maps "worldId:chunkX:chunkZ" to a list of
     * (parkour, node) pairs whose region overlaps that chunk.
     */
    private val chunkIndex = mutableMapOf<String, MutableList<Pair<ParkourDefinition, ParkourNode>>>()

    // -------------------------------------------------------------------------
    // Index management
    // -------------------------------------------------------------------------

    /** Rebuilds the chunk index from the current definitions. */
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

    /** Call after a node or parkour is added/changed to keep the index up to date. */
    fun reindexParkour(parkour: ParkourDefinition) {
        // Remove old entries for this parkour
        chunkIndex.values.forEach { list ->
            list.removeAll { (p, _) -> p.id == parkour.id }
        }
        parkour.nodes.forEach { indexNode(parkour, it) }
    }

    // -------------------------------------------------------------------------
    // Session management
    // -------------------------------------------------------------------------

    fun getSession(playerId: UUID): RunSession? = sessions[playerId]

    fun hasSession(playerId: UUID): Boolean = sessions.containsKey(playerId)

    /**
     * Starts a new run session for [player] at [node] in [parkour].
     * If the player has a pinned route starting at this entry node, it is armed.
     */
    fun startSession(player: Player, parkour: ParkourDefinition, node: ParkourNode) {
        val pinnedRoute = getPinnedRoute(player, node.id)
        val routeKey: String?
        val routeSeq: List<UUID>?
        if (pinnedRoute != null) {
            routeKey = pinnedRoute.routeKey
            routeSeq = pinnedRoute.nodeIds.map { UUID.fromString(it) }
        } else {
            routeKey = null
            routeSeq = null
        }

        val session = RunSession(
            playerId = player.uniqueId,
            parkourId = parkour.id,
            currentNodeId = node.id,
            runStartMs = System.currentTimeMillis(),
            lastCheckpointMs = System.currentTimeMillis(),
            lastEntrypointMs = System.currentTimeMillis(),
            path = mutableListOf(node.id),
            activeRouteKey = routeKey,
            activeRouteSequence = routeSeq,
            activeRouteIndex = 0
        )
        sessions[player.uniqueId] = session

        val msg = if (pinnedRoute != null) {
            text("Parkour started! Tracking route. Good luck!", NamedTextColor.GREEN)
        } else {
            text("Parkour started! No route pinned – freestyle tracking active.", NamedTextColor.GREEN)
        }
        player.sendMessage(msg)
    }

    /**
     * Stops and discards the current session for [player] without saving a result.
     */
    fun stopSession(player: Player) {
        sessions.remove(player.uniqueId)
        player.sendActionBar(Component.empty())
        player.sendMessage(text("Parkour session stopped.", NamedTextColor.YELLOW))
    }

    /**
     * Called when [player] enters the region of [node] in [parkour].
     * Handles session start, checkpoint recording, and route completion.
     */
    fun onNodeEntered(player: Player, parkour: ParkourDefinition, node: ParkourNode) {
        val session = sessions[player.uniqueId]

        if (session == null) {
            // Only ENTRY nodes can start a new session
            if (node.type == NodeType.ENTRY) {
                startSession(player, parkour, node)
            }
            return
        }

        // Must be the same parkour
        if (session.parkourId != parkour.id) return

        // Validate transition via graph edge (or same node re-entry for loops)
        val isValidTransition = node.id == session.currentNodeId ||
                parkour.hasEdge(session.currentNodeId, node.id)
        if (!isValidTransition) return

        // Don't re-trigger the same node immediately
        if (node.id == session.currentNodeId) return

        val now = System.currentTimeMillis()

        // Record the checkpoint/entry split
        session.path += node.id
        session.lastCheckpointMs = now
        if (node.type == NodeType.ENTRY) {
            session.lastEntrypointMs = now
        }

        // Advance pinned route tracker
        val routeNodeReached = session.isNextRouteNode(node.id)
        if (routeNodeReached) {
            session.advanceRoute()
        }

        session.currentNodeId = node.id

        // Auto-finish when:
        // - pinned route is active and complete at FINISH
        // - no pinned route is active and a FINISH node is reached
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

        // If not finishing, just show updated split info
        sendSplitActionBar(player, session)
    }

    /**
     * Persists the completed run result and removes the session.
     */
    private fun finishSession(player: Player, session: RunSession, parkour: ParkourDefinition) {
        sessions.remove(player.uniqueId)
        val finishedAt = System.currentTimeMillis()
        val durationMs = finishedAt - session.runStartMs

        val routeKey = session.activeRouteKey
        val routeSequenceJson = Json.encodeToString(
            session.activeRouteSequence?.map { it.toString() } ?: session.path.map { it.toString() }
        )

        if (routeKey != null) {
            val profileId = try {
                player.profile().id
            } catch (e: IllegalStateException) {
                NexusPlugin.logger.warning("[Parkour] No profile for ${player.name}, result not saved.")
                player.sendActionBar(Component.empty())
                player.sendMessage(
                    text("Parkour finished! Time: ${formatDuration(durationMs)}", NamedTextColor.GOLD)
                )
                return
            }

            feature.scheduler.launch {
                try {
                    transaction(NexusPlugin.database) {
                        ParkourResultsTable.insert {
                            it[ParkourResultsTable.profileId] = profileId
                            it[ParkourResultsTable.parkourId] = parkour.id
                            it[ParkourResultsTable.routeKey] = routeKey
                            it[ParkourResultsTable.routeSequence] = routeSequenceJson
                            it[ParkourResultsTable.startedAt] = Instant.fromEpochMilliseconds(session.runStartMs)
                            it[ParkourResultsTable.finishedAt] = Instant.fromEpochMilliseconds(finishedAt)
                            it[ParkourResultsTable.durationMs] = durationMs
                        }
                    }
                } catch (e: Exception) {
                    NexusPlugin.logger.warning("[Parkour] Failed to save run result: ${e.message}")
                }
            }
        }

        player.sendActionBar(Component.empty())
        player.sendMessage(
            text("Parkour finished! Time: ${formatDuration(durationMs)}", NamedTextColor.GOLD)
        )
    }

    // -------------------------------------------------------------------------
    // Trigger detection
    // -------------------------------------------------------------------------

    /**
     * Returns all (parkour, node) pairs whose region contains (worldId, x, y, z).
     * Uses the chunk index for efficiency.
     */
    fun getNodesAt(worldId: UUID, x: Int, y: Int, z: Int): List<Pair<ParkourDefinition, ParkourNode>> {
        val cx = x shr 4
        val cz = z shr 4
        val key = "$worldId:$cx:$cz"
        val candidates = chunkIndex[key] ?: return emptyList()
        return candidates.filter { (_, node) -> node.region.contains(x, y, z) }
    }

    // -------------------------------------------------------------------------
    // Pinned routes
    // -------------------------------------------------------------------------

    private fun getPinnedRoute(player: Player, nodeId: UUID): PinnedRoute? {
        return try {
            val attachment = player.profile().attachmentOf<ParkourAttachment>() ?: return null
            attachment.pinnedRoutes[nodeId.toString()]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Pins a route for [player] starting at [entryNodeId] with the given sequence [nodeIds].
     * The [routeKey] is computed deterministically from parkourId + nodeIds.
     */
    fun pinRoute(player: Player, parkourId: UUID, entryNodeId: UUID, nodeIds: List<UUID>): String {
        val routeKey = computeRouteKey(parkourId, nodeIds)
        val nodeIdStrings = nodeIds.map { it.toString() }

        val profileId = player.profile().id

        feature.scheduler.launch {
            try {
                transaction(NexusPlugin.database) {
                    ParkourAttachmentTable.upsert {
                        it[ParkourAttachmentTable.profileId] = profileId
                        it[ParkourAttachmentTable.entryNodeId] = entryNodeId
                        it[ParkourAttachmentTable.routeKey] = routeKey
                        it[ParkourAttachmentTable.routeSequence] = Json.encodeToString(nodeIdStrings)
                    }
                }
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Parkour] Failed to pin route: ${e.message}")
            }
        }

        return routeKey
    }

    /** Unpins the route for [player] at [entryNodeId]. */
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

    // -------------------------------------------------------------------------
    // Action bar display
    // -------------------------------------------------------------------------

    fun sendSplitActionBar(player: Player, session: RunSession) {
        val total = formatDuration(session.elapsedMs)
        val checkpoint = formatDuration(session.checkpointSplitMs)
        val entry = formatDuration(session.entrySplitMs)
        val bar = text("Run: $total  |  Checkpoint: $checkpoint  |  Entry: $entry", NamedTextColor.AQUA)
        player.sendActionBar(bar)
    }

    /** Sends the live actionbar to all players with active sessions. */
    fun tickActionBars() {
        sessions.forEach { (playerId, session) ->
            val player = NexusPlugin.server.getPlayer(playerId) ?: return@forEach
            sendSplitActionBar(player, session)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Computes a deterministic route key from [parkourId] and the ordered [nodeIds].
     * Uses SHA-256 truncated to 16 hex chars.
     */
    fun computeRouteKey(parkourId: UUID, nodeIds: List<UUID>): String {
        val input = parkourId.toString() + nodeIds.joinToString(",")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

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
