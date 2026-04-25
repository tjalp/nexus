package net.tjalp.nexus.feature.parkour.rule

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class MaxMoveSpeedRule(
    val speed: Double = 0.2
) : ParkourSegmentRule {
    companion object {
        const val ID: String = "maximum_movement_speed"
    }

    override val id: String = ID
}
