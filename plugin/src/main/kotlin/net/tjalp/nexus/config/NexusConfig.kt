package net.tjalp.nexus.config

import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.set
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path

@ConfigSerializable
data class NexusConfig(
    val database: DatabaseConfig,
    val features: FeaturesConfig
) {

    companion object {

        fun loader(dataPath: Path): YamlConfigurationLoader = YamlConfigurationLoader.builder()
            .path(dataPath.resolve("config.yml"))
            .nodeStyle(NodeStyle.BLOCK)
            .indent(2)
            .defaultOptions { options ->
                options.serializers { builder ->
                    builder.registerAnnotatedObjects(objectMapperFactory())
                }
            }
            .build()

        /**
         * Reloads the configuration from disk.
         *
         * @param dataPath The path to the plugin's data folder.
         * @return The reloaded configuration.
         */
        fun reload(dataPath: Path): NexusConfig {
            val loader = loader(dataPath)
            val rootNode = loader.load()
            val configuration = rootNode.get<NexusConfig>() ?: error("Failed to load configuration")

            rootNode.set(NexusConfig::class, configuration)
            loader.save(rootNode)

            return configuration
        }
    }
}

@ConfigSerializable
data class DatabaseConfig(
    val url: String = "jdbc:postgresql://localhost:5432/postgres",
    val driver: String = "org.postgresql.Driver",
    val user: String = "postgres",
    val password: String = "postgres",
)

@ConfigSerializable
data class FeaturesConfig(
    val chat: ChatConfig,
    val disguises: DisguisesConfig,
    val gamerules: GameRulesConfig,
    val games: GamesConfig,
    val notices: NoticesConfig,
    @Setting("physical_spectator") val physicalSpectator: PhysicalSpectatorConfig,
    val punishments: PunishmentsConfig,
    val seasons: SeasonsConfig,
    @Setting("teleport_requests") val teleportRequests: TeleportRequestsConfig,
    val waypoints: WaypointsConfig
)

@ConfigSerializable
data class ChatConfig(
    val enable: Boolean = true,
    val format: String = "<<name>> <message>",
    val joinMessage: String = "<message>",
    val quitMessage: String = "<message>",
    val deathMessage: String = "<message>"
)

@ConfigSerializable
data class DisguisesConfig(
    val enable: Boolean = true
)

@ConfigSerializable
data class GameRulesConfig(
    val enable: Boolean = true,
    val creeperGrieving: Boolean = true,
    val cropTrampling: Boolean = true,
    val endermanGrieving: Boolean = true,
    val ghastGrieving: Boolean = true,
)

@ConfigSerializable
data class GamesConfig(
    val enable: Boolean = true
)

@ConfigSerializable
data class NoticesConfig(
    val enable: Boolean = true,
    val recommendations: RecommendationsConfig,
)

@ConfigSerializable
data class RecommendationsConfig(
    val enable: Boolean = true,
    val showOnJoin: Boolean = true,
    val settings: List<SettingRecommendation> = emptyList(),
    val mods: List<ModRecommendation> = listOf(
        ModRecommendation(
            name = "Example",
            description = "Example description",
            link = "https://example.com"
        )
    )
)

@ConfigSerializable
data class SettingRecommendation(
    val name: String,
    val description: String,
    val value: String,
    val settingPath: String
)

@ConfigSerializable
data class ModRecommendation(
    val name: String,
    val description: String,
    val link: String
)

@ConfigSerializable
data class PhysicalSpectatorConfig(
    val enable: Boolean = true
)

@ConfigSerializable
data class PunishmentsConfig(
    val enable: Boolean = true,
)

@ConfigSerializable
data class SeasonsConfig(
    val enable: Boolean = true,
    val shouldTick: Boolean = true,
    val winter: SeasonWinterConfig
)

@ConfigSerializable
data class SeasonWinterConfig(
    val allowSnowFormation: Boolean = true,
    val allowIceFormation: Boolean = true,
    val iceFormationRequiresSurroundedByWater: Boolean = false,
    val foliageColor: String = "858780",
    val dryFoliageColor: String = "858780",
    val grassColor: String = "858780",
    val waterColor: String = "3d57d6",
    val waterFogColor: String = "050533"
)

@ConfigSerializable
data class TeleportRequestsConfig(
    val enable: Boolean = true,
    val requestTimeoutSeconds: Long = 90
)

@ConfigSerializable
data class WaypointsConfig(
    val enable: Boolean = true
)