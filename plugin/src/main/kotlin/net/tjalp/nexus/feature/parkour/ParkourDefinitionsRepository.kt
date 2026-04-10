package net.tjalp.nexus.feature.parkour

import net.tjalp.nexus.NexusPlugin
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.util.*

/**
 * Manages loading and saving of [ParkourDefinition]s using Sponge Configurate (YAML).
 * Definitions are stored in `<dataPath>/parkours.yml` and use world UUIDs,
 * so they survive world resets or name changes and can be wiped independently
 * of player data stored in the database.
 */
class ParkourDefinitionsRepository(dataPath: Path) {

    private val filePath: Path = dataPath.resolve("parkours.yml")
    private val loader: YamlConfigurationLoader = YamlConfigurationLoader.builder()
        .path(filePath)
        .nodeStyle(NodeStyle.BLOCK)
        .indent(2)
        .defaultOptions { options ->
            options.serializers { builder ->
                builder.registerAnnotatedObjects(objectMapperFactory())
                builder.register(UUID::class.java, UUIDTypeSerializer)
                builder.register(NodeType::class.java, NodeTypeSerializer)
            }
        }
        .build()

    private val _parkours: MutableMap<UUID, ParkourDefinition> = mutableMapOf()

    /** All loaded parkour definitions, keyed by their ID. */
    val parkours: Map<UUID, ParkourDefinition> get() = _parkours

    init {
        reload()
    }

    /** Reloads all definitions from disk. */
    fun reload() {
        _parkours.clear()
        try {
            val root = loader.load()
            val list = root.node("parkours").get<List<ParkourDefinition>>() ?: emptyList()
            list.forEach { _parkours[it.id] = it }
            NexusPlugin.logger.info("[Parkour] Loaded ${_parkours.size} parkour definition(s).")
        } catch (e: Exception) {
            NexusPlugin.logger.warning("[Parkour] Failed to load parkours.yml: ${e.message}")
        }
    }

    /** Persists all definitions to disk. */
    fun save() {
        try {
            val root = loader.load()
            root.node("parkours").setList(ParkourDefinition::class.java, _parkours.values.toList())
            loader.save(root)
        } catch (e: Exception) {
            NexusPlugin.logger.warning("[Parkour] Failed to save parkours.yml: ${e.message}")
        }
    }

    /** Returns the definition with the given [id], or null. */
    fun get(id: UUID): ParkourDefinition? = _parkours[id]

    /** Returns the definition whose name matches [name] (case-insensitive), or null. */
    fun getByName(name: String): ParkourDefinition? =
        _parkours.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Saves or replaces a definition and persists to disk. */
    fun upsert(parkour: ParkourDefinition) {
        _parkours[parkour.id] = parkour
        save()
    }

    /** Removes a definition by ID and persists to disk. */
    fun remove(id: UUID): Boolean {
        val removed = _parkours.remove(id) != null
        if (removed) save()
        return removed
    }

    /** All nodes across all definitions, with a reference to their parent parkour. */
    fun allNodes(): List<Pair<ParkourDefinition, ParkourNode>> =
        _parkours.values.flatMap { parkour -> parkour.nodes.map { node -> parkour to node } }
}
