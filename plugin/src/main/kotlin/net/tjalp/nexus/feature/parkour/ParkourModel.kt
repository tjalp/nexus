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
 * A directed edge between two nodes in a parkour graph.
 */
@ConfigSerializable
data class ParkourEdge(
    val fromNodeId: UUID = UUID(0, 0),
    val toNodeId: UUID = UUID(0, 0),
    val enabled: Boolean = true
)

/**
 * A full parkour definition: graph of nodes + edges.
 * Definitions are stored via Configurate (YAML) and use world UUIDs so they
 * survive world-name changes; they can be wiped independently from player data.
 */
@ConfigSerializable
data class ParkourDefinition(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val nodes: MutableList<ParkourNode> = mutableListOf(),
    val edges: MutableList<ParkourEdge> = mutableListOf()
) {
    fun nodeById(id: UUID): ParkourNode? = nodes.firstOrNull { it.id == id }
    fun nodeByName(name: String): ParkourNode? = nodes.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Returns all nodes reachable from [fromNodeId] via enabled edges. */
    fun successors(fromNodeId: UUID): List<ParkourNode> =
        edges.filter { it.enabled && it.fromNodeId == fromNodeId }
            .mapNotNull { nodeById(it.toNodeId) }

    /** Returns true if there is an enabled edge from [fromNodeId] to [toNodeId]. */
    fun hasEdge(fromNodeId: UUID, toNodeId: UUID): Boolean =
        edges.any { it.enabled && it.fromNodeId == fromNodeId && it.toNodeId == toNodeId }
}
