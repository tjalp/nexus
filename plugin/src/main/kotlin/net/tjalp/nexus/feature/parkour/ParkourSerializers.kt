package net.tjalp.nexus.feature.parkour

import net.tjalp.nexus.feature.parkour.rule.AllowFlightRule
import net.tjalp.nexus.feature.parkour.rule.AllowTeleportationRule
import net.tjalp.nexus.feature.parkour.rule.MaxMoveSpeedRule
import net.tjalp.nexus.feature.parkour.rule.ParkourSegmentRule
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.ScalarSerializer
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
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

/** Polymorphic serializer for [ParkourSegmentRule] using a `type` discriminator. */
object ParkourSegmentRuleSerializer : TypeSerializer<ParkourSegmentRule> {

    override fun deserialize(type: Type?, node: ConfigurationNode?): ParkourSegmentRule {
        val currentNode = node ?: throw SerializationException("Rule node cannot be null")
        val ruleType = currentNode.node("type").getString()?.trim()?.lowercase()
            ?: throw SerializationException("Missing rule discriminator 'type'")

        return when (ruleType) {
            AllowTeleportationRule.ID -> AllowTeleportationRule
            AllowFlightRule.ID -> AllowFlightRule
            MaxMoveSpeedRule.ID -> {
                val speed = currentNode.node("speed").getDouble()
                if (speed <= 0.0) {
                    throw SerializationException("Rule '${MaxMoveSpeedRule.ID}' requires 'speed' > 0")
                }
                MaxMoveSpeedRule(speed)
            }

            else -> throw SerializationException("Unknown parkour segment rule type: '$ruleType'")
        }
    }

    override fun serialize(type: Type?, obj: ParkourSegmentRule?, node: ConfigurationNode?) {
        val currentNode = node ?: throw SerializationException("Rule node cannot be null")
        currentNode.raw(null)

        if (obj == null) return

        when (obj) {
            AllowTeleportationRule -> {
                currentNode.node("type").set(AllowTeleportationRule.ID)
            }

            AllowFlightRule -> {
                currentNode.node("type").set(AllowFlightRule.ID)
            }

            is MaxMoveSpeedRule -> {
                currentNode.node("type").set(MaxMoveSpeedRule.ID)
                currentNode.node("speed").set(obj.speed)
            }
        }
    }
}

