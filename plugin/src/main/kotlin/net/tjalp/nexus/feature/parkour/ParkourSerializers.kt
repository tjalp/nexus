package net.tjalp.nexus.feature.parkour

import org.spongepowered.configurate.serialize.ScalarSerializer
import org.spongepowered.configurate.serialize.SerializationException
import java.lang.reflect.Type
import java.util.*
import java.util.function.Predicate

/**
 * Configurate serializer for [UUID]: stores as a plain string.
 */
object UUIDTypeSerializer : ScalarSerializer<UUID>(UUID::class.java) {
    override fun deserialize(type: Type, obj: Any): UUID {
        return try {
            UUID.fromString(obj.toString())
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Invalid UUID: $obj")
        }
    }

    override fun serialize(item: UUID, typeSupported: Predicate<Class<*>>): Any = item.toString()
}

/**
 * Configurate serializer for [NodeType]: stores as the enum name string.
 */
object NodeTypeSerializer : ScalarSerializer<NodeType>(NodeType::class.java) {
    override fun deserialize(type: Type, obj: Any): NodeType {
        return try {
            NodeType.valueOf(obj.toString().uppercase())
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Unknown node type: $obj")
        }
    }

    override fun serialize(item: NodeType, typeSupported: Predicate<Class<*>>): Any = item.name
}
