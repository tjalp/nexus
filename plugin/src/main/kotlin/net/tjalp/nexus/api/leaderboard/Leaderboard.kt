package net.tjalp.nexus.api.leaderboard

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kyori.adventure.text.ComponentLike
import net.tjalp.nexus.scheduler.Scheduler
import org.bukkit.Location
import org.spongepowered.configurate.reactive.Disposable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Leaderboard(
    val location: Location,
    val title: ComponentLike,
    val source: LeaderboardScoreSource,
    val scheduler: Scheduler,
    val size: Int = 10,
    val description: ComponentLike? = null,
    val refreshInterval: Duration = 30.seconds
) : Disposable {

    private val job: Job
    private val scoreCache = mutableListOf<LeaderboardScore>()
    private val hologram = LeaderboardHologram(this)

    init {
        job = scheduler.launch {
            while (isActive) {
                refresh()
                delay(refreshInterval)
            }
        }

        hologram.spawn()
    }

    suspend fun refresh() {
        val scores = source.getScores(size)

        scoreCache.clear()
        scoreCache.addAll(scores)

        hologram.render()
    }

    fun getScore(index: Int): LeaderboardScore? {
        return scoreCache.getOrNull(index)
    }

    override fun dispose() {
        hologram.dispose()
        job.cancel()
    }
}