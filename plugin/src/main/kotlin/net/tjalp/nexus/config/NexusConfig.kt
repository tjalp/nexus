package net.tjalp.nexus.config

import net.tjalp.nexus.NexusPlugin
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader

@ConfigSerializable
data class NexusConfig(
    val database: DatabaseConfig,
    val modules: ModulesConfig
) {

    companion object {

        val LOADER: YamlConfigurationLoader = YamlConfigurationLoader.builder()
            .path(NexusPlugin.dataPath.resolve("config.yml"))
            .nodeStyle(NodeStyle.BLOCK)
            .indent(2)
            .defaultOptions { options ->
                options.serializers { builder ->
                    builder.registerAnnotatedObjects(objectMapperFactory())
                }
            }
            .build()
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
data class ModulesConfig(
    val chat: ChatConfig,
    val disguises: DisguisesConfig,
    val gamerules: GameRulesConfig,
    val games: GamesConfig,
    val seasons: SeasonsConfig,
    @Setting("teleport_requests") val teleportRequests: TeleportRequestsConfig
)

@ConfigSerializable
data class ChatConfig(
    val enable: Boolean = true,
    val format: String = "<<name>> <message>"
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