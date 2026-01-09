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
 * @param type The type of the game.
 */
abstract class Game(
    val id: String = List(6) {
        ('a'..'z') + ('A'..'Z') + ('0'..'9')
    }.flatten().shuffled().take(6).joinToString(""),
    val type: GameType
) : Disposable, ForwardingAudience {

    /**
     * Scheduler dedicated to this game instance.
     */
    val scheduler = GamesFeature.scheduler.fork("game/$id")

    /**
     * The current active phase of the game, if any.
     */
    var currentPhase: GamePhase? = null; private set

    private val _participants = mutableSetOf<UUID>()

    /**
     * The set of entities currently participating in the game.
     */
    val participants: Set<Entity> get() = _participants.mapNotNull { it.asEntity() }.toSet()

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
    fun end() = GamesFeature.endGame(this)

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
        currentPhase?.onLeave(entity)

        if (entity is Player) entity.scoreboard = NexusPlugin.server.scoreboardManager.mainScoreboard
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
    get() = GamesFeature.activeGames.firstOrNull { it.participants.contains(this) }

/**
 * Formats the game prefix for messages, including the game type.
 */
val Game.prefix: Component
    get() = textOfChildren(
        type.friendlyName.decoration(BOLD, true),
        text(" → ", DARK_GRAY)
    )

fun Game.prefix(locale: Locale): Component {
    val formattedName = type.formattedName.invoke(locale)

    return textOfChildren(
        formattedName.decoration(BOLD, true),
        text(" → ", DARK_GRAY)
    )
}