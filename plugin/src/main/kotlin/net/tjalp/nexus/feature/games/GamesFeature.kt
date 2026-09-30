package net.tjalp.nexus.feature.games

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.GAMES
import net.tjalp.nexus.feature.games.frostball_frenzy.FrostballFrenzyFightPhase
import net.tjalp.nexus.feature.games.frostball_frenzy.FrostballFrenzyGame
import net.tjalp.nexus.feature.games.frostball_frenzy.FrostballFrenzyWaitingPhase

class GamesFeature : Feature(GAMES) {

    private val _activeGames = mutableListOf<Game>()
    private val _definitions = linkedMapOf<String, GameDefinition>()
    private lateinit var definitionsRepository: GameDefinitionsRepository

    /** Definitions available for creating new games. */
    val definitions: List<GameDefinition>
        get() = _definitions.values.toList()

    /**
     * A list of currently active games, not necessarily running.
     */
    val activeGames: List<Game>
        get() = _activeGames.toList()

    override fun onEnable() {
        registerBuiltins()
        definitionsRepository = GameDefinitionsRepository(NexusPlugin.database)
        definitionsRepository.loadOrSeed().forEach { definition ->
            _definitions[definition.key] = definition
        }
    }

    private fun registerBuiltins() {
        GameTemplateSeeds.all.forEach { GameTemplateRegistry.register(it.template) }
        GameFactoryRegistry.register("frostball_frenzy") { feature, definition ->
            FrostballFrenzyGame(feature, definition)
        }
        GamePhaseRegistry.register("frostball_waiting") { game -> FrostballFrenzyWaitingPhase(game) }
        GamePhaseRegistry.register("frostball_fight") { game ->
            FrostballFrenzyFightPhase(game as FrostballFrenzyGame)
        }
    }

    /** Creates a game from a persisted definition. */
    // Recommended future syntax: /game create <template> [name]
    fun createGame(definition: GameDefinition): Game {
        require(_definitions[definition.key] == definition) {
            "Game definition '${definition.key}' is not registered"
        }
        val game = GameFactoryRegistry.create(this, definition)

        _activeGames.add(game)

        return game
    }

    /** Creates a game from the legacy implementation type. */
    fun createGame(type: GameType): Game {
        val definition = _definitions[type.definitionKey]
            ?: error("No game definition is registered for '${type.definitionKey}'")
        return createGame(definition)
    }

    fun definition(key: String): GameDefinition? = _definitions[key]

    /** Returns a copy suitable for editing without changing the stored definition. */
    fun copyDefinition(key: String): GameDefinition =
        definition(key)?.copy(
            teams = definition(key)!!.teams.toList(),
            scoring = definition(key)!!.scoring.toList(),
            phases = definition(key)!!.phases.toList()
        ) ?: error("No game definition is registered for '$key'")

    // Recommended future syntax: /game team <id> assign <target> <team>

    /** Replaces a definition in memory and persists it for subsequent starts. */
    fun updateDefinition(definition: GameDefinition) {
        require(GameTemplateRegistry[definition.templateKey] != null) {
            "Game template '${definition.templateKey}' is not registered"
        }
        definitionsRepository.save(definition)
        _definitions[definition.key] = definition
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