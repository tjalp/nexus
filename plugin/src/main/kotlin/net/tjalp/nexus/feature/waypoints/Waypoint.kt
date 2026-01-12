package net.tjalp.nexus.feature.waypoints

import kotlinx.serialization.Serializable
import net.kyori.adventure.key.Key
import net.kyori.adventure.key.Key.key
import net.tjalp.nexus.NexusPlugin
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
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
) {

    /**
     * Represents a waypoint in the world.
     *
     * @param id The unique identifier of the waypoint.
     * @param location The location of the waypoint.
     * @param color The color of the waypoint.
     * @param style The style of the waypoint.
     */
    constructor(id: String, location: Location, color: Color, style: Key) : this(
        id,
        location.world!!.uid.toKotlinUuid(),
        location.x,
        location.y,
        location.z,
        color.asRGB(),
        style.asString()
    ) {
        require(location.world != null) { "Location world cannot be null" }
    }

    /**
     * The entity representing the waypoint in the world.
     */
    var entity: LivingEntity? = null; private set

    /**
     * The world the waypoint is located in.
     */
    val world: World?; get() = NexusPlugin.server.getWorld(worldId.toJavaUuid())

    /**
     * The location of the waypoint.
     * Setting this will teleport the entity if it is spawned.
     * Warning: The world WILL be null if the entity is not spawned.
     */
    var location: Location
        set(value) {
            // remove chunk ticket from old location
            world?.getChunkAt(location)?.removePluginChunkTicket(NexusPlugin)

            // teleport entity if spawned
            entity?.teleport(value)
            x = value.x
            y = value.y
            z = value.z
            worldId = value.world.uid.toKotlinUuid()
            world?.getChunkAt(value)?.addPluginChunkTicket(NexusPlugin)
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
            // TODO("Handle updating the entity style if spawned")
            styleString = value.asString()
        }
        get() = key(styleString)

    /**
     * Spawns the waypoint entity in the world.
     */
    fun spawn(world: World) {
        require(entity == null || entity?.isValid == false) { "Waypoint entity already spawned" }

        entity = world.spawn(location, ArmorStand::class.java) {
            it.isPersistent = false
//            it.isMarker = true
            it.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE)?.baseValue = Double.MAX_VALUE
        }
    }

    /**
     * Despawns the waypoint entity from the world.
     */
    fun despawn() {
        entity?.remove()
        entity = null
    }
}