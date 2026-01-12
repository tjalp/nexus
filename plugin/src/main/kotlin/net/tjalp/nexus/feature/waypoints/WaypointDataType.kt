package net.tjalp.nexus.feature.waypoints

import kotlinx.serialization.json.Json
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType

object WaypointDataType : PersistentDataType<ByteArray, Waypoint> {

    override fun getPrimitiveType(): Class<ByteArray> = ByteArray::class.java

    override fun getComplexType(): Class<Waypoint> = Waypoint::class.java

    override fun toPrimitive(
        complex: Waypoint,
        context: PersistentDataAdapterContext
    ): ByteArray = Json.encodeToString(complex).toByteArray()

    override fun fromPrimitive(
        primitive: ByteArray,
        context: PersistentDataAdapterContext
    ): Waypoint = Json.decodeFromString(primitive.decodeToString())
}