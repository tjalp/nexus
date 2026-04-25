package net.tjalp.nexus.feature.parkour.rule

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data object AllowTeleportationRule : ParkourSegmentRule {
    const val ID: String = "allow_teleportation"
    override val id: String = ID
}