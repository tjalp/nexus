package net.tjalp.nexus.feature.parkour.rule

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
sealed interface ParkourSegmentRule {

    val id: String
}