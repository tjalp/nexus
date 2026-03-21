package net.tjalp.nexus.feature.waypoints

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.kyori.adventure.key.Key
import net.kyori.adventure.key.Key.key
import net.tjalp.nexus.NexusPlugin
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Represents a waypoint in the world.
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
class Waypoint(
    val id: String,
    private var worldId: Uuid,
    var target: WaypointTarget,
    private var colorRgb: Int,
    private var styleString: String,
    var transmitRange: Double = Double.MAX_VALUE,
    private var visibility: WaypointVisibility = WaypointVisibility.Global
) {

    /**
     * Represents a waypoint in the world.
     *
     * @param id The unique identifier of the waypoint.
     * @param location The location of the waypoint.
     * @param color The color of the waypoint.
     * @param style The style of the waypoint.
     * @param transmitRange The maximum visible range of the waypoint
     */
    constructor(
        id: String,
        location: Location,
        color: Color,
        style: Key,
        transmitRange: Double = Double.MAX_VALUE
    ) : this(
        id,
        location.world!!.uid.toKotlinUuid(),
        WaypointTarget.Block(location.blockX, location.blockY, location.blockZ),
        color.asRGB(),
        style.asString(),
        transmitRange,
    ) {
        require(location.world != null) { "Location world cannot be null" }
    }

    /**
     * The world the waypoint is located in.
     */
    val world: World?; get() = NexusPlugin.server.getWorld(worldId.toJavaUuid())

    /**
     * The UUID of the world this waypoint belongs to.
     */
    val worldUuid: UUID
        get() = worldId.toJavaUuid()

    /**
     * The color of the waypoint.
     */
    var color: Color
        set(value) {
            // TODO("Handle updating the entity color if spawned")
            colorRgb = value.asRGB()
        }
        get() = Color.fromRGB(colorRgb)

    /**
     * The style of the waypoint.
     */
    var style: Key
        set(value) {
            styleString = value.asString()
        }
        get() = key(styleString)

    /**
     * Whether this waypoint is visible to all players in the world.
     */
    var global: Boolean
        get() = visibility is WaypointVisibility.Global
        set(value) {
            visibility = if (value) WaypointVisibility.Global else WaypointVisibility.Players(emptySet())
        }

    /**
     * The explicit player audience for this waypoint when [global] is false.
     */
    val audience: Set<UUID>
        get() = when (val current = visibility) {
            WaypointVisibility.Global -> emptySet()
            is WaypointVisibility.Players -> current.playerIds.map { it.toJavaUuid() }.toSet()
        }

    /**
     * Makes this waypoint private and visible to the given players only.
     */
    fun showTo(players: Set<UUID>) {
        visibility = WaypointVisibility.Players(players.map { it.toKotlinUuid() }.toSet())
    }

    /**
     * Adds one player to this waypoint's private audience.
     */
    fun showTo(playerId: UUID) {
        val playerUuid = playerId.toKotlinUuid()
        visibility = when (val current = visibility) {
            WaypointVisibility.Global -> WaypointVisibility.Players(setOf(playerUuid))
            is WaypointVisibility.Players -> current.copy(playerIds = current.playerIds + playerUuid)
        }
    }

    /**
     * Removes one player from this waypoint's private audience.
     */
    fun hideFrom(playerId: UUID) {
        val playerUuid = playerId.toKotlinUuid()
        visibility = when (val current = visibility) {
            WaypointVisibility.Global -> current
            is WaypointVisibility.Players -> current.copy(playerIds = current.playerIds - playerUuid)
        }
    }

    /**
     * Checks if this waypoint should be shown to the given player.
     */
    fun isVisibleTo(player: Player): Boolean {
        if (player.world.uid != worldId.toJavaUuid()) return false

        val inRange = when (val target = target) {
            is WaypointTarget.Block -> {
                player.location.distanceSquared(
                    Location(
                        player.world,
                        target.x + 0.5,
                        target.y + 0.5,
                        target.z + 0.5
                    )
                ) <= transmitRange * transmitRange
            }
            is WaypointTarget.Chunk -> {
                player.location.distanceSquared(
                    Location(
                        player.world,
                        (target.chunkX * 16 + 8).toDouble(),
                        player.location.y,
                        (target.chunkZ * 16 + 8).toDouble()
                    )
                ) <= transmitRange * transmitRange
            }
            is WaypointTarget.Azimuth -> true
        }

        if (!inRange) return false

        return when (val current = visibility) {
            WaypointVisibility.Global -> true
            is WaypointVisibility.Players -> current.playerIds.contains(player.uniqueId.toKotlinUuid())
        }
    }
}

/**
 * Refreshes the waypoint.
 *
 * @see WaypointRenderer.refresh
 */
fun Waypoint.refresh() {
    NexusPlugin.waypoints?.renderer?.refresh(this)
}

/**
 * Save a waypoint in the specified world, by default the waypoint's world.
 *
 * @param world The world to save the waypoint in
 * @see WaypointsFeature.saveWaypoint
 */
fun Waypoint.save(world: World = this.world!!) {
    NexusPlugin.waypoints?.saveWaypoint(world, this)
}

@Serializable
sealed interface WaypointTarget {

    @Serializable
    @SerialName("block")
    data class Block(
        val x: Int,
        val y: Int,
        val z: Int
    ) : WaypointTarget

    @Serializable
    @SerialName("chunk")
    data class Chunk(
        val chunkX: Int,
        val chunkZ: Int
    ) : WaypointTarget

    @Serializable
    @SerialName("azimuth")
    data class Azimuth(
        val angle: Float
    ) : WaypointTarget
}

@Serializable
sealed interface WaypointVisibility {

    @Serializable
    @SerialName("global")
    data object Global : WaypointVisibility

    @Serializable
    @SerialName("players")
    @OptIn(ExperimentalUuidApi::class)
    data class Players(
        val playerIds: Set<Uuid>
    ) : WaypointVisibility
}

