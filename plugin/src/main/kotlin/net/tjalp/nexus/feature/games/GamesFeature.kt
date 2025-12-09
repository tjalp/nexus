package net.tjalp.nexus.feature.games

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.games.snowballfight.SnowballFightGame

object GamesFeature : Feature("games") {

    private val _activeGames = mutableListOf<Game>()

    /**
     * A list of currently active games, not necessarily running.
     */
    val activeGames: List<Game>
        get() = _activeGames

    /**
     * Creates a new game instance based on the provided [GameType].
     *
     * @param type The type of game to create.
     * @return A new instance of the specified game type.
     */
    fun createGame(type: GameType): Game {
        val game = when (type) {
            GameType.SNOWBALL_FIGHT -> SnowballFightGame()
        }

        _activeGames.add(game)

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

    override fun disable() {
        _activeGames.toList().forEach { endGame(it) }
        super.disable()
    }
}