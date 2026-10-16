package net.tjalp.nexus.feature.parkour

import net.kyori.adventure.text.Component
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.parkour.rule.ParkourSegmentRule
import net.tjalp.nexus.util.miniMessage
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.*
import kotlin.math.floor

/**
 * Types of parkour nodes.
 */
enum class NodeType {
    /** Starting point; also acts as a checkpoint. */
    ENTRY,

    /** Intermediate checkpoint (may not also be an entry point). */
    CHECKPOINT,

    /** Finish node; can complete active freestyle runs when reached. */
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
    val key: String,
    val name: String = key,
    val type: NodeType = NodeType.CHECKPOINT,
    val region: ParkourRegion = ParkourRegion()
) {
    /** Convenience: the world UUID of this node (from its region). */
    val worldId: UUID get() = region.worldId

    /**
     * Convenience: the display name of this node, parsed from its [name] using MiniMessage.
     */
    val displayName: Component = miniMessage.deserialize(name)
}

/**
 * A named, directed segment between two nodes.
 * Segments are the primary building block for timing and route composition.
 */
@ConfigSerializable
data class ParkourSegment(
    val key: String,
    val name: String = key,
    val fromNodeKey: String,
    val toNodeKey: String,
    val iconModel: String? = null,
    val enabled: Boolean = true,
    val rules: List<ParkourSegmentRule> = emptyList()
) {

    /**
     * Convenience: the display name of this segment, parsed from its [name] using MiniMessage.
     */
    val displayName: Component = miniMessage.deserialize(name)

    @Suppress("UnstableApiUsage")
    val icon: ItemStack = iconModel?.let {
        ItemType.CLOCK.createItemStack { meta -> meta.itemModel = NamespacedKey.fromString(iconModel) }
    } ?: ItemType.CLOCK.createItemStack()
}

/**
 * A leaderboard location for a specific parkour segment.
 * The leaderboard is displayed at the given coordinates in the world identified by the world id.
 */
@ConfigSerializable
data class ParkourLeaderboard(
    val key: String,
    val worldId: UUID,
    val segmentKey: String,
    val x: Double,
    val y: Double,
    val z: Double
) {

    val location: Location?
        get() = Location(
            NexusPlugin.server.getWorld(worldId) ?: return null,
            x,
            y,
            z
        )

    val chunkX: Int get() = floor(x / 16.0).toInt()
    val chunkZ: Int get() = floor(z / 16.0).toInt()
}

/**
 * Full parkour graph: nodes + directed segments.
 */
@ConfigSerializable
data class ParkourDefinition(
    val nodes: MutableList<ParkourNode> = mutableListOf(),
    val segments: MutableList<ParkourSegment> = mutableListOf(),
    val leaderboards: MutableList<ParkourLeaderboard> = mutableListOf()
) {

    fun nodeByKey(key: String): ParkourNode? = nodes.firstOrNull { it.key.equals(key, ignoreCase = true) }
    fun nodeByName(name: String): ParkourNode? = nodes.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun segmentByKey(key: String): ParkourSegment? = segments.firstOrNull { it.key.equals(key, ignoreCase = true) }
    fun segmentByName(name: String): ParkourSegment? = segments.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Returns all nodes reachable from [fromNodeKey] via enabled segments. */
    fun successors(fromNodeKey: String): List<ParkourNode> =
        segments.filter { it.enabled && it.fromNodeKey.equals(fromNodeKey, ignoreCase = true) }
            .mapNotNull { nodeByKey(it.toNodeKey) }

    /** Returns true if there is an enabled segment from [fromNodeKey] to [toNodeKey]. */
    fun hasSegment(fromNodeKey: String, toNodeKey: String): Boolean =
        segments.any {
            it.enabled && it.fromNodeKey.equals(fromNodeKey, ignoreCase = true) && it.toNodeKey.equals(
                toNodeKey,
                ignoreCase = true
            )
        }

    fun findSegment(fromNodeKey: String, toNodeKey: String): ParkourSegment? =
        segments.firstOrNull {
            it.enabled && it.fromNodeKey.equals(
                fromNodeKey,
                ignoreCase = true
            ) && it.toNodeKey.equals(toNodeKey, ignoreCase = true)
        }

    fun removeNode(nodeKey: String) {
        nodes.removeIf { it.key.equals(nodeKey, ignoreCase = true) }
        segments.filter {
            it.fromNodeKey.equals(nodeKey, ignoreCase = true) || it.toNodeKey.equals(
                nodeKey,
                ignoreCase = true
            )
        }.forEach { removeSegment(it.key) }
    }

    fun removeSegment(segmentKey: String) {
        segments.removeIf { it.key.equals(segmentKey, ignoreCase = true) }
        leaderboards.removeIf { it.segmentKey.equals(segmentKey, ignoreCase = true) }
    }
}


