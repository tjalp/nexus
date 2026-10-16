package net.tjalp.nexus.feature.parkour

import kotlinx.coroutines.withContext
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.scheduler.BukkitDispatcher
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.Chunk
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.spongepowered.configurate.reactive.Disposable
import java.util.*

class ParkourLeaderboardService(
    private val feature: ParkourFeature
): Disposable, Listener {
    private val entriesBySegmentKey = mutableMapOf<String, List<ParkourLeaderboardEntry>>()
    private val entityIdsByLeaderboard =
        mutableMapOf<String, MutableSet<UUID>>()

    init {
        register()
    }

    fun onChunkLoad(chunk: Chunk) {
        feature.definitions.definition.leaderboards
            .filter { it.chunkX == chunk.x }
            .filter { it.chunkZ == chunk.z }
            .filter { it.worldId == chunk.world.uid }
            .forEach { spawn(it) }
    }

    fun onChunkUnload(chunk: Chunk) {
        feature.definitions.definition.leaderboards
            .filter { it.chunkX == chunk.x }
            .filter { it.chunkZ == chunk.z }
            .filter { it.worldId == chunk.world.uid }
            .forEach { leaderboard ->
                entityIdsByLeaderboard.remove(leaderboard.key)
            }
    }

    fun refresh(leaderboard: ParkourLeaderboard) {
        remove(leaderboard.key)

        val location = leaderboard.location ?: return
        if (!location.chunk.isLoaded) return

        spawn(leaderboard)
    }

    fun remove(leaderboardId: String) {
        entityIdsByLeaderboard.remove(leaderboardId)
            ?.forEach { entityId ->
                NexusPlugin.server.getEntity(entityId)?.remove()
            }
    }

    override fun dispose() {
        unregister()
        entityIdsByLeaderboard.keys.toList().forEach(::remove)
        entityIdsByLeaderboard.clear()
    }

    private fun spawn(leaderboard: ParkourLeaderboard) {
        val location = leaderboard.location ?: return
        val entityIds = entityIdsByLeaderboard
            .getOrPut(leaderboard.key) { mutableSetOf() }

        // Spawn the one or more display entities here.
        // Configure their initial text from the leaderboard's current data.
        // entityIds += spawnedEntity.uniqueId
        // todo grab data from the leaderboard and spawn the display entities with that data (copilot chat)
    }

    private suspend fun refreshEntries(segmentKey: String) {
        val top = ParkourLeaderboardRepository.getTop(segmentKey, 10)

        entriesBySegmentKey[segmentKey] = top

        withContext(BukkitDispatcher) {

        }
    }

    @EventHandler
    fun on(event: ChunkLoadEvent) {
        this.onChunkLoad(event.chunk)
    }

    @EventHandler
    fun on(event: ChunkUnloadEvent) {
        this.onChunkUnload(event.chunk)
    }
}