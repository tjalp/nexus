package net.tjalp.nexus.feature.games

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.asPlayer
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
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
) : Disposable {

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
     * The set of players currently participating in the game.
     */
    val participants: Set<Player> get() = _participants.mapNotNull { it.asPlayer() }.toSet()

    /**
     * The next phase to transition to when advancing the game.
     */
    abstract val nextPhase: GamePhase

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
     * Allows a player to join the game, if they are not already in another game and the current phase allows it.
     *
     * @param player The player attempting to join.
     * @return The result of the join attempt.
     */
    open suspend fun join(player: Player): JoinResult {
        if (player.currentGame != null) {
            return JoinResult.Failure(JoinFailureReason.ALREADY_IN_GAME, "Player is already in a game")
        }

        val success = currentPhase?.onJoin(player) ?: return JoinResult.Failure(
            JoinFailureReason.WRONG_PHASE,
            "No active phase to join"
        )

        if (success !is JoinResult.Success) return success

        _participants.add(player.uniqueId)
        player.scoreboard = scoreboard

        return JoinResult.Success
    }

    private suspend fun runJoinsConcurrently(phase: GamePhase, players: Set<Player>) = coroutineScope {
        players.map { player ->
            async {
                val result = phase.onJoin(player)

                if (result !is JoinResult.Failure) return@async result

                // leave on failure to join and send message
                leave(player)

                player.sendMessage(
                    text("You were kicked out of the game, because: ${result.message ?: "Unknown reason (${result.reason})"}", RED)
                )

                return@async result
            }
        }.awaitAll()
    }

    /**
     * Handles a player leaving the game, notifying the current phase.
     *
     * @param player The player leaving the game.
     */
    open fun leave(player: Player) {
        currentPhase?.onLeave(player)

        _participants.remove(player.uniqueId)
        player.scoreboard = NexusPlugin.server.scoreboardManager.mainScoreboard
    }

    override fun dispose() {
        participants.forEach { leave(it) }
        currentPhase?.dispose()
        listener.unregister()
        scheduler.dispose()
    }
}

/**
 * Retrieves the current game a player is participating in, if any.
 */
val Player.currentGame: Game?
    get() = GamesFeature.activeGames.firstOrNull { it.participants.contains(this) }