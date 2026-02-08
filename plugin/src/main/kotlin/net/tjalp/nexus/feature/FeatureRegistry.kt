package net.tjalp.nexus.feature

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.config.FeaturesConfig
import net.tjalp.nexus.feature.FeatureKeys.CHAT
import net.tjalp.nexus.feature.FeatureKeys.DISGUISES
import net.tjalp.nexus.feature.FeatureKeys.GAMERULES
import net.tjalp.nexus.feature.FeatureKeys.GAMES
import net.tjalp.nexus.feature.FeatureKeys.NOTICES
import net.tjalp.nexus.feature.FeatureKeys.PHYSICAL_SPECTATOR
import net.tjalp.nexus.feature.FeatureKeys.PUNISHMENTS
import net.tjalp.nexus.feature.FeatureKeys.SEASONS
import net.tjalp.nexus.feature.FeatureKeys.TELEPORT_REQUESTS
import net.tjalp.nexus.feature.FeatureKeys.WAYPOINTS
import net.tjalp.nexus.feature.chat.ChatFeature
import net.tjalp.nexus.feature.disguises.DisguiseFeature
import net.tjalp.nexus.feature.gamerules.GameRulesFeature
import net.tjalp.nexus.feature.games.GamesFeature
import net.tjalp.nexus.feature.notices.NoticesFeature
import net.tjalp.nexus.feature.physicalspectator.PhysicalSpectatorFeature
import net.tjalp.nexus.feature.punishments.PunishmentsFeature
import net.tjalp.nexus.feature.seasons.SeasonsFeature
import net.tjalp.nexus.feature.teleportrequests.TeleportRequestsFeature
import net.tjalp.nexus.feature.waypoints.WaypointsFeature
import kotlin.reflect.KClass

data class FeatureDefinition(
    val id: String,
    val featureClass: KClass<out Feature>,
    val create: () -> Feature,
    val shouldEnable: (FeaturesConfig) -> Boolean
)

object FeatureRegistry {
    val definitions = listOf(
        FeatureDefinition(
            CHAT,
            ChatFeature::class,
            { ChatFeature() },
            { it.chat.enable }
        ),
        FeatureDefinition(
            DISGUISES,
            DisguiseFeature::class,
            { DisguiseFeature() },
            { it.disguises.enable }
        ),
        FeatureDefinition(
            GAMERULES,
            GameRulesFeature::class,
            { GameRulesFeature() },
            { it.gamerules.enable }
        ),
        FeatureDefinition(
            GAMES,
            GamesFeature::class,
            { GamesFeature() },
            { it.games.enable }
        ),
        FeatureDefinition(
            NOTICES,
            NoticesFeature::class,
            { NoticesFeature() },
            { it.notices.enable }
        ),
        FeatureDefinition(
            PHYSICAL_SPECTATOR,
            PhysicalSpectatorFeature::class,
            { PhysicalSpectatorFeature() },
            { it.physicalSpectator.enable }
        ),
        FeatureDefinition(
            PUNISHMENTS,
            PunishmentsFeature::class,
            { PunishmentsFeature() },
            { it.punishments.enable }
        ),
        FeatureDefinition(
            SEASONS,
            SeasonsFeature::class,
            { SeasonsFeature() },
            { it.seasons.enable }
        ),
        FeatureDefinition(
            TELEPORT_REQUESTS,
            TeleportRequestsFeature::class,
            { TeleportRequestsFeature() },
            { it.teleportRequests.enable }
        ),
        FeatureDefinition(
            WAYPOINTS,
            WaypointsFeature::class,
            { WaypointsFeature() },
            { it.waypoints.enable }
        )
    )
}

/**
 * Get the feature instance for this definition, if it exists
 */
val FeatureDefinition.asFeature: Feature? get() = NexusPlugin.features.getFeature(this.featureClass)