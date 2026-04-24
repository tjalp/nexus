package net.tjalp.nexus.feature.parkour.rule

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data object AllowTeleportationRule : ParkourSegmentRule {

    override val id: String = "allow_teleportation"
}