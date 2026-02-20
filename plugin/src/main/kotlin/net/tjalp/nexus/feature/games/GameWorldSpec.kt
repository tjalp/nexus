package net.tjalp.nexus.feature.games

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator

sealed class GameWorldSpec {
    abstract val isTemporary: Boolean

    abstract fun resolveWorld(gameId: String): World

    data class Existing(
        val worldName: String,
        val environment: World.Environment? = null
    ) : GameWorldSpec() {
        override val isTemporary: Boolean = false

        override fun resolveWorld(gameId: String): World {
            val existingWorld = Bukkit.getWorld(worldName)
            if (existingWorld != null) return existingWorld

            val creator = WorldCreator(worldName)
            environment?.let { creator.environment(it) }
            val createdWorld = Bukkit.createWorld(creator)
            if (createdWorld != null) return createdWorld

            val worldFolder = Bukkit.getWorldContainer().resolve(worldName)
            val message = if (worldFolder.exists()) {
                "Failed to load existing world '$worldName'"
            } else {
                "Failed to create world '$worldName'"
            }

            error(message)
        }
    }

    data class Temporary(
        val worldName: String? = null,
        val environment: World.Environment = World.Environment.NORMAL,
        val seed: Long? = null
    ) : GameWorldSpec() {
        override val isTemporary: Boolean = true

        override fun resolveWorld(gameId: String): World {
            val resolvedName = normalizeTemporaryName(worldName ?: gameId)
            val creator = WorldCreator(resolvedName).environment(environment)
            seed?.let { creator.seed(it) }

            return Bukkit.createWorld(creator)
                ?: error("Failed to create temporary world '$resolvedName'")
        }
    }

    companion object {
        const val TEMP_WORLD_PREFIX = "game-"

        internal fun normalizeTemporaryName(name: String): String {
            return if (name.startsWith(TEMP_WORLD_PREFIX)) name else "$TEMP_WORLD_PREFIX$name"
        }

        fun mainWorld(): GameWorldSpec = Existing("world", environment = World.Environment.NORMAL)

        fun nether(): GameWorldSpec = Existing("world_nether", environment = World.Environment.NETHER)

        fun theEnd(): GameWorldSpec = Existing("world_the_end", environment = World.Environment.THE_END)

        fun temporary(
            worldName: String? = null,
            environment: World.Environment = World.Environment.NORMAL,
            seed: Long? = null
        ): GameWorldSpec = Temporary(worldName = worldName, environment = environment, seed = seed)
    }
}
