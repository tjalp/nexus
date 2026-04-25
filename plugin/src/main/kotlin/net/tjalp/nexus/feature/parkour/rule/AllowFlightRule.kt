package net.tjalp.nexus.feature.parkour.rule

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data object AllowFlightRule : ParkourSegmentRule {
    const val ID: String = "allow_flight"
    override val id: String = ID
}