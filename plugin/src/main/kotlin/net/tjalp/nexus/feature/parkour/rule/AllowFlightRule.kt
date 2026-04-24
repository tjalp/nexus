package net.tjalp.nexus.feature.parkour.rule

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data object AllowFlightRule : ParkourSegmentRule {

    override val id: String = "allow_flight"

    // todo allow serialization of rules.
}