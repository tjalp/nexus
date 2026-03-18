package net.tjalp.nexus.feature.waypoints

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
 *
 * @param id The unique identifier of the waypoint.
 * @param worldId The unique identifier of the world the waypoint is located in.
 * @param x The x-coordinate of the waypoint.
 * @param y The y-coordinate of the waypoint.
 * @param z The z-coordinate of the waypoint.
 * @param colorRgb The color of the waypoint in RGB format.
 * @param styleString The style of the waypoint as a string.
 * @param transmitRange The maximum visible range of the waypoint
 * @param isGlobal Whether this waypoint is visible to all players in the world.
 * @param visibleTo The explicit player audience for this waypoint when [isGlobal] is false
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
class Waypoint(
    val id: String,
    private var worldId: Uuid,
    private var x: Double,
    private var y: Double,
    private var z: Double,
    private var colorRgb: Int,
    private var styleString: String,
    var transmitRange: Double = Double.MAX_VALUE,
    private var isGlobal: Boolean = true,
    private var visibleTo: Set<Uuid> = emptySet()
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
    constructor(id: String, location: Location, color: Color, style: Key, transmitRange: Double = Double.MAX_VALUE) : this(
        id,
        location.world!!.uid.toKotlinUuid(),
        location.x,
        location.y,
        location.z,
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
     * The location of the waypoint.
     * Setting this will teleport the entity if it is spawned.
     * Warning: The world WILL be null if the entity is not spawned.
     */
    var location: Location
        set(value) {
            val world = requireNotNull(value.world) { "Location world cannot be null" }
            x = value.x
            y = value.y
            z = value.z
            worldId = world.uid.toKotlinUuid()
        }
        get() = Location(world, x, y, z)

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
        get() = isGlobal
        set(value) {
            isGlobal = value
            if (value) visibleTo = emptySet()
        }

    /**
     * The explicit player audience for this waypoint when [global] is false.
     */
    val audience: Set<UUID>
        get() = visibleTo.map { it.toJavaUuid() }.toSet()

    /**
     * Makes this waypoint private and visible to the given players only.
     */
    fun showTo(players: Set<UUID>) {
        isGlobal = false
        visibleTo = players.map { it.toKotlinUuid() }.toSet()
    }

    /**
     * Adds one player to this waypoint's private audience.
     */
    fun showTo(playerId: UUID) {
        isGlobal = false
        visibleTo = visibleTo + playerId.toKotlinUuid()
    }

    /**
     * Removes one player from this waypoint's private audience.
     */
    fun hideFrom(playerId: UUID) {
        visibleTo = visibleTo - playerId.toKotlinUuid()
    }

    /**
     * Checks if this waypoint should be shown to the given player.
     */
    fun isVisibleTo(player: Player): Boolean {
        if (player.world.uid != worldId.toJavaUuid()) return false
        return isGlobal || visibleTo.contains(player.uniqueId.toKotlinUuid())
    }
}