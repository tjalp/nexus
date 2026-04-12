package net.tjalp.nexus.feature.parkour

import net.tjalp.nexus.NexusPlugin
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.util.UUID

/** Manages loading/saving of the single global parkour graph. */
class ParkourDefinitionsRepository(dataPath: Path) {
    private val annotatedMapperFactory = objectMapperFactory()

    private val filePath: Path = dataPath.resolve("parkour.yml")
    private val loader: YamlConfigurationLoader = YamlConfigurationLoader.builder()
        .path(filePath)
        .nodeStyle(NodeStyle.BLOCK)
        .indent(2)
        .defaultOptions { options ->
            options.serializers { builder ->
                builder.registerAnnotatedObjects(annotatedMapperFactory)
                builder.register(UUID::class.java, UUIDTypeSerializer)
                builder.register(NodeType::class.java, NodeTypeSerializer)
            }
        }
        .build()

    var definition: ParkourDefinition = ParkourDefinition()
        private set

    init {
        reload()
    }

    /** Reloads definition from disk. */
    fun reload() {
        try {
            val root = loader.load()
            definition = root.node("definition").get(ParkourDefinition::class.java) ?: ParkourDefinition()
            NexusPlugin.logger.info("[Parkour] Loaded ${definition.nodes.size} nodes and ${definition.segments.size} segments.")
        } catch (e: Exception) {
            NexusPlugin.logger.warning("[Parkour] Failed to load parkour.yml: ${e.message}")
            definition = ParkourDefinition()
        }
    }

    /** Persists definition to disk. */
    fun save() {
        try {
            val root = loader.load()
            root.node("definition").set(ParkourDefinition::class.java, definition)
            loader.save(root)
        } catch (e: Exception) {
            NexusPlugin.logger.warning("[Parkour] Failed to save parkour.yml: ${e.message}")
        }
    }

    /** Replaces in-memory definition and persists it. */
    fun update(definition: ParkourDefinition) {
        this.definition = definition
        save()
    }
}
