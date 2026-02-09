package net.tjalp.nexus.feature.games

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.FeatureKeys.GAMES
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Entity
import org.bukkit.event.Listener
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class GamesFeature : Feature(GAMES) {

    private val games = mutableListOf<Game>()
    private val participants = mutableMapOf<UUID, Game>()
    private val idCounter = AtomicInteger(1)

    private var listener: Listener? = null

    val activeGames: List<Game>
        get() = games.toList()

    override fun onEnable() {
        listener = GameListener(this).also { it.register() }
    }

    override fun onDisposed() {
        games.toList().forEach { it.dispose() }
        games.clear()
        participants.clear()
        listener?.unregister()
    }

    fun createGame(type: GameType): Game {
        val id = "game-${idCounter.getAndIncrement()}"
        val game = type.createGame(this, id)
        games += game
        game.start()
        return game
    }

    fun endGame(game: Game) {
        if (!games.remove(game)) return
        game.dispose()
    }

    fun getGameFor(entity: Entity): Game? = participants[entity.uniqueId]

    internal fun registerParticipant(entity: Entity, game: Game): Boolean {
        if (participants.containsKey(entity.uniqueId)) return false
        participants[entity.uniqueId] = game
        return true
    }

    internal fun unregisterParticipant(entity: Entity) {
        participants.remove(entity.uniqueId)
    }
}
