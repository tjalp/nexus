package net.tjalp.nexus.feature.parkour

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*

/**
 * Types of parkour nodes.
 */
enum class NodeType {
    /** Starting point; also acts as a checkpoint. */
    ENTRY,
    /** Intermediate checkpoint (may or may not also be an entry point). */
    CHECKPOINT,
    /** Finish node; can complete runs when reached (pinned-route and freestyle logic is runtime-controlled). */
    FINISH
}

/**
 * Axis-aligned cuboid region used as a node trigger.
 * All coordinates are block coordinates in the world identified by [worldId].
 */
@ConfigSerializable
data class ParkourRegion(
    val worldId: UUID = UUID(0, 0),
    val minX: Int = 0,
    val minY: Int = 0,
    val minZ: Int = 0,
    val maxX: Int = 0,
    val maxY: Int = 0,
    val maxZ: Int = 0
) {
    /** Returns true if the given block position (integers) is within this region. */
    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in minX..maxX && y in minY..maxY && z in minZ..maxZ
}

/**
 * A single node in a parkour graph.
 */
@ConfigSerializable
data class ParkourNode(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val type: NodeType = NodeType.CHECKPOINT,
    val region: ParkourRegion = ParkourRegion()
) {
    /** Convenience: the world UUID of this node (from its region). */
    val worldId: UUID get() = region.worldId
}

/**
 * A named, directed segment between two nodes.
 * Segments are the primary building block for timing and route composition.
 */
@ConfigSerializable
data class ParkourSegment(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val fromNodeId: UUID = UUID(0, 0),
    val toNodeId: UUID = UUID(0, 0),
    val enabled: Boolean = true
)

/**
 * A named route composed of ordered segment IDs.
 * Predefined routes are shared map content while player-specific routes are pinned via attachments.
 */
@ConfigSerializable
data class ParkourRoute(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val segmentIds: MutableList<UUID> = mutableListOf(),
    val predefined: Boolean = true
)

/**
 * A full parkour definition: graph of nodes + segments.
 * Definitions are stored via Configurate (YAML) and use world UUIDs so they
 * survive world-name changes; they can be wiped independently from player data.
 */
@ConfigSerializable
data class ParkourDefinition(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val nodes: MutableList<ParkourNode> = mutableListOf(),
    val segments: MutableList<ParkourSegment> = mutableListOf(),
    val routes: MutableList<ParkourRoute> = mutableListOf()
) {
    fun nodeById(id: UUID): ParkourNode? = nodes.firstOrNull { it.id == id }
    fun nodeByName(name: String): ParkourNode? = nodes.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun segmentById(id: UUID): ParkourSegment? = segments.firstOrNull { it.id == id }
    fun segmentByName(name: String): ParkourSegment? = segments.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun routeByName(name: String): ParkourRoute? = routes.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Returns all nodes reachable from [fromNodeId] via enabled segments. */
    fun successors(fromNodeId: UUID): List<ParkourNode> =
        segments.filter { it.enabled && it.fromNodeId == fromNodeId }
            .mapNotNull { nodeById(it.toNodeId) }

    /** Returns true if there is an enabled segment from [fromNodeId] to [toNodeId]. */
    fun hasSegment(fromNodeId: UUID, toNodeId: UUID): Boolean =
        segments.any { it.enabled && it.fromNodeId == fromNodeId && it.toNodeId == toNodeId }

    fun findSegment(fromNodeId: UUID, toNodeId: UUID): ParkourSegment? =
        segments.firstOrNull { it.enabled && it.fromNodeId == fromNodeId && it.toNodeId == toNodeId }

    fun removeNode(nodeId: UUID) {
        nodes.removeIf { it.id == nodeId }
        segments.removeIf { it.fromNodeId == nodeId || it.toNodeId == nodeId }
        pruneRoutes()
    }

    fun removeSegment(segmentId: UUID) {
        segments.removeIf { it.id == segmentId }
        pruneRoutes()
    }

    fun pruneRoutes() {
        val validSegmentIds = segments.map { it.id }.toSet()
        routes.forEach { route ->
            route.segmentIds.removeIf { it !in validSegmentIds }
        }
        routes.removeIf { it.segmentIds.isEmpty() }
    }
}
