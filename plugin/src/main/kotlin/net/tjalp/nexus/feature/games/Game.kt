package net.tjalp.nexus.feature.games

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.textOfChildren
import net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.TextDecoration.BOLD
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.asEntity
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.spongepowered.configurate.reactive.Disposable
import java.util.*

/**
 * Represents a game instance with unique ID, type, phases, and participants.
 *
 * @param id Unique identifier for the game instance.
 * @param definition Persisted definition describing the game rules and implementation.
 */
abstract class Game(
    private val feature: GamesFeature,
    val id: String = List(6) {
        ('a'..'z') + ('A'..'Z') + ('0'..'9')
    }.flatten().shuffled().take(6).joinToString(""),
    definition: GameDefinition
) : Disposable, ForwardingAudience {

    private var currentDefinition = definition

    val definition: GameDefinition
        get() = currentDefinition

    /** Compatibility constructor for implementations using the original type-only API. */
    constructor(feature: GamesFeature, type: GameType) : this(
        feature = feature,
        definition = GameTemplateSeeds.all.firstOrNull { it.template.key == type.definitionKey }?.definition
            ?: GameDefinition(
                key = type.definitionKey,
                templateKey = type.definitionKey,
                name = type.name,
                minPlayers = 0,
                maxPlayers = Int.MAX_VALUE
            )
    )

    /**
     * Legacy view of the implementation type. New code should use [definition]
     * and its template key.
     */
    val type: GameType
        get() = GameType.entries.firstOrNull { it.definitionKey == definition.templateKey }
            ?: GameType.FROSTBALL_FRENZY

    /**
     * Scheduler dedicated to this game instance.
     */
    val scheduler = feature.scheduler.fork("game/$id")

    /**
     * The current active phase of the game, if any.
     */
    var currentPhase: GamePhase? = null; private set

    private val _participants = mutableSetOf<UUID>()
    private val _teamAssignments = mutableMapOf<UUID, String>()
    private val _scores = mutableMapOf<Pair<UUID, String>, Int>()

    /**
     * The set of entities currently participating in the game.
     */
    val participants: Set<Entity> get() = _participants.mapNotNull { it.asEntity() }.toSet()

    /** Returns the configured team for an entity, if one has been assigned. */
    fun teamOf(entity: Entity): GameTeam? =
        _teamAssignments[entity.uniqueId]?.let(definition::team)

    val teamScores: Map<GameTeam, Int>
        get() = definition.teams.associateWith { team ->
            _teamAssignments.filterValues { it.equals(team.key, ignoreCase = true) }
                .keys.sumOf { uuid -> _scores.filterKeys { it.first == uuid }.values.sum() }
        }

    /** Assigns an entity to a configured team without changing its lifecycle. */
    fun assignTeam(entity: Entity, teamKey: String) {
        require(definition.team(teamKey) != null) { "Unknown team '$teamKey' for game ${definition.key}" }
        require(_participants.contains(entity.uniqueId)) { "Entity is not participating in game $id" }
        _teamAssignments[entity.uniqueId] = teamKey
    }

    private fun assignAutomaticTeam(entity: Entity) {
        if (definition.teamAssignmentMode != TeamAssignmentMode.AUTOMATIC || definition.teams.isEmpty()) return
        val team = definition.teams.minBy { team ->
            _teamAssignments.values.count { it.equals(team.key, ignoreCase = true) }
        }
        assignTeam(entity, team.key)
    }

    /** Adds points for a named scoring rule and returns the new score. */
    fun addScore(entity: Entity, scoringKey: String, multiplier: Int = 1): Int {
        val rule = definition.scoringRule(scoringKey)
            ?: error("Unknown scoring rule '$scoringKey' for game ${definition.key}")
        val scoreKey = entity.uniqueId to rule.key
        val score = (_scores[scoreKey] ?: 0) + rule.points * multiplier
        _scores[scoreKey] = score
        return score
    }

    fun score(entity: Entity, scoringKey: String): Int =
        _scores[entity.uniqueId to scoringKey] ?: 0

    /** Updates this game's definition and persists it through the owning feature. */
    fun updateDefinition(definition: GameDefinition) {
        feature.updateDefinition(definition)
        currentDefinition = definition
    }

    /**
     * The next phase to transition to when advancing the game.
     */
    abstract val nextPhase: GamePhase

    /**
     * The settings specific to this game instance.
     */
    abstract val settings: GameSettings

    /**
     * The scoreboard used for this game instance.
     */
    val scoreboard = NexusPlugin.server.scoreboardManager.newScoreboard

    private val listener: GameListener = GameListener(this).apply { register() }

    /**
     * Enters the specified game phase, handling loading, disposal of the previous phase, and starting the new phase.
     *
     * @param phase The game phase to enter.
     * @throws IllegalArgumentException if attempting to load the same phase as the current one.
     * @throws RuntimeException if loading or starting the new phase fails.
     */
    suspend fun enterPhase(phase: GamePhase) {
        require(phase != currentPhase) { "Cannot load the same phase twice: ${phase::class.simpleName}" }

        val previousPhase = currentPhase

        try {
            phase.load(previousPhase)
            participants.forEach { previousPhase?.onLeave(it) }
            previousPhase?.dispose()
            currentPhase = phase
            phase.start(previousPhase)

            runJoinsConcurrently(phase, participants)
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to load game phase ${phase::class.simpleName} for game $id of type ${type.name}",
                e
            )
        }
    }

    /**
     * Advances the game to the next phase as defined by [nextPhase].
     */
    suspend fun enterNextPhase() {
        enterPhase(nextPhase)
    }

    /**
     * Ends the game, removing it from active games and disposing of its resources.
     *
     * @see GamesFeature.endGame
     */
    fun end() = feature.endGame(this)

    /**
     * Allows an entity to join the game, if it is not already in another game and the current phase allows it.
     *
     * @param entity The entity attempting to join.
     * @return The result of the join attempt.
     */
    open suspend fun join(entity: Entity): JoinResult {
        val phase = currentPhase

        if (entity.currentGame != null) {
            return JoinResult.Failure(JoinFailureReason.ALREADY_IN_GAME, "Entity is already in a game")
        }

        val result = phase?.canJoin(entity) ?: JoinResult.Success

        if (result !is JoinResult.Success) return result

        _participants.add(entity.uniqueId)
        assignAutomaticTeam(entity)

        if (entity is Player) entity.scoreboard = scoreboard

        phase?.onJoin(entity)

        return JoinResult.Success
    }

    private suspend fun runJoinsConcurrently(phase: GamePhase, entities: Set<Entity>) = coroutineScope {
        entities.map { entity ->
            async {
                val result = phase.canJoin(entity)

                if (result is JoinResult.Failure) {
                    // leave on failure to join and send message
                    leave(entity)

                    entity.sendMessage(
                        text("You were kicked out of the game, because: ${result.message ?: "Unknown reason (${result.reason})"}", RED)
                    )

                    return@async result
                }

                phase.onJoin(entity)

                return@async result
            }
        }.awaitAll()
    }

    /**
     * Handles a player leaving the game, notifying the current phase.
     *
     * @param entity The player leaving the game.
     */
    open fun leave(entity: Entity) {
        if (_participants.none { it == entity.uniqueId }) return

        _participants.remove(entity.uniqueId)
        _teamAssignments.remove(entity.uniqueId)
        currentPhase?.onLeave(entity)

        if (entity is Player) entity.scoreboard = NexusPlugin.server.scoreboardManager.mainScoreboard
    }

    /** Adds or removes points directly, intended for host/admin controls. */
    // Recommended future syntax: /game score <id> <target> <points> [rule]
    fun applyPoints(entity: Entity, points: Int, scoringKey: String = "manual"): Int {
        require(definition.scoringRule(scoringKey) != null) {
            "Unknown scoring rule '$scoringKey' for game ${definition.key}"
        }
        val rule = definition.scoringRule(scoringKey)!!
        val scoreKey = entity.uniqueId to rule.key
        val score = (_scores[scoreKey] ?: 0) + points
        _scores[scoreKey] = score
        return score
    }

    fun hasWinner(): Boolean = teamScores.values.any { it >= definition.pointsToWin } ||
        _scores.values.any { it >= definition.pointsToWin }

    /** Enters a configured phase by key, intended for host/admin controls. */
    // Recommended future syntax: /game phase <id> <phase>
    suspend fun enterConfiguredPhase(phaseKey: String) {
        val phase = definition.phases.firstOrNull { it.key.equals(phaseKey, ignoreCase = true) }
            ?: error("Unknown phase '$phaseKey' for game ${definition.key}")
        enterPhase(GamePhaseRegistry.create(this, phase.implementationKey))
    }

    override fun dispose() {
        participants.forEach { leave(it) }
        currentPhase?.dispose()
        listener.unregister()
        scheduler.dispose()
    }

    override fun audiences(): Iterable<Audience> = participants
}

/**
 * Retrieves the current game an entity is participating in, if any.
 */
val Entity.currentGame: Game?
    get() = NexusPlugin.games?.activeGames?.firstOrNull { it.participants.contains(this) }

/**
 * Formats the game prefix for messages, including the game type.
 */
val Game.prefix: Component
    get() = textOfChildren(
        gameDisplayName().decoration(BOLD, true),
        text(" → ", DARK_GRAY)
    )

fun Game.prefix(locale: Locale): Component {
    val formattedName = if (type.definitionKey == definition.templateKey) {
        type.formattedName.invoke(locale)
    } else {
        text(definition.name)
    }

    return textOfChildren(
        formattedName.decoration(BOLD, true),
        text(" → ", DARK_GRAY)
    )
}

private fun Game.gameDisplayName(): Component =
    if (type.definitionKey == definition.templateKey) type.friendlyName else text(definition.name)