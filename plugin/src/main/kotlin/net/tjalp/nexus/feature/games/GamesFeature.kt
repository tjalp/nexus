package net.tjalp.nexus.feature.games

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.FeatureKeys.GAMES
import net.tjalp.nexus.feature.games.frostball_frenzy.FrostballFrenzyGame
import net.tjalp.nexus.feature.games.minigame.MinigameTemplates
import java.util.*

class GamesFeature : Feature(GAMES) {

    private val _activeGames = mutableListOf<Game>()

    init {
        FrostballFrenzyGame.registerTemplate()
    }

    /**
     * A list of currently active games, not necessarily running.
     */
    val activeGames: List<Game>
        get() = _activeGames.toList()

    /**
     * Creates a new game instance based on the provided [GameType].
     *
     * @param type The type of game to create.
     * @return A new instance of the specified game type.
     */
    fun createGame(type: GameType): Game = createGame(type, host = null)

    fun createGame(type: GameType, host: UUID?): Game {
        val game = when (type) {
            GameType.FROSTBALL_FRENZY -> FrostballFrenzyGame(this, host)
        }

        _activeGames.add(game)

        return game
    }

    fun createGameFromTemplate(templateId: String, host: UUID? = null): Game? {
        val template = MinigameTemplates.byId(templateId) ?: return null
        val game = createGame(template.type, host)

        return game
    }

    /**
     * Ends the specified [game], removing it from the list of running games and disposing of its resources.
     *
     * @param game The game instance to end.
     */
    fun endGame(game: Game) {
        _activeGames.remove(game)
        game.dispose()
    }

    override fun onDisposed() {
        _activeGames.toList().forEach { endGame(it) }
    }
}
