package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.textOfChildren
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.GameArgument
import net.tjalp.nexus.feature.games.*
import net.tjalp.nexus.feature.games.phase.TimerPhase
import kotlin.time.Duration.Companion.seconds

object GameCommand {

    fun create(): LiteralCommandNode<CommandSourceStack> {
        val createNode = literal("create")

        for (type in GameType.entries) {
            createNode
                .then(literal(type.name.lowercase())
                    .executes { context ->
                        val game = GamesFeature.createGame(type)

                        context.source.sender.sendMessage(textOfChildren(
                            text("Created a new "),
                            type.friendlyName,
                            text(" game with ID ${game.id}")
                        ))

                        return@executes Command.SINGLE_SUCCESS
                    })
        }

        return literal("game")
            .requires { GamesFeature.isEnabled && it.sender.hasPermission("nexus.command.game") }
            .then(createNode)
            .then(literal("next")
                .then(argument("id", GameArgument)
                    .executes { context ->
                        val game = context.getArgument("id", Game::class.java)

                        NexusPlugin.scheduler.launch {
                            game.enterNextPhase()
                            context.source.sender.sendMessage(text("Entered next phase for game with ID ${game.id}"))
                        }

                        return@executes Command.SINGLE_SUCCESS
                    }))
            .then(literal("end")
                .then(argument("id", GameArgument)
                    .executes { context ->
                        val game = context.getArgument("id", Game::class.java)

                        GamesFeature.endGame(game)
                        context.source.sender.sendMessage(text("Ended game with ID ${game.id}"))

                        return@executes Command.SINGLE_SUCCESS
                    }))
            .then(literal("join")
                .then(argument("id", GameArgument)
                    .then(argument("targets", ArgumentTypes.entities())
                        .executes { context ->
                            val game = context.getArgument("id", Game::class.java)
                            val resolver = context.getArgument("targets", EntitySelectorArgumentResolver::class.java)
                            val entities = resolver.resolve(context.source)

                            game.scheduler.launch {
                                val joined = entities.map { entity ->
                                    async {
                                        when (val result = game.join(entity)) {
                                            is JoinResult.Success -> true
                                            is JoinResult.Failure -> {
                                                val msg = result.message ?: "Unknown reason (${result.reason})"
                                                context.source.sender.sendMessage(
                                                    text("Failed to join entity ${entity.name} to game ${game.id}: $msg", RED)
                                                )
                                                false
                                            }
                                        }
                                    }
                                }.awaitAll()

                                context.source.sender.sendMessage(
                                    text("Joined ${joined.count { it }} entities to game with ID ${game.id}")
                                )
                            }

                            return@executes Command.SINGLE_SUCCESS
                        })))
            .then(literal("leave")
                .then(argument("targets", ArgumentTypes.entities())
                    .executes { context ->
                        val resolver = context.getArgument("targets", EntitySelectorArgumentResolver::class.java)
                        val entities = resolver.resolve(context.source)

                        entities.forEach { player ->
                            val game = player.currentGame ?: return@forEach

                            game.leave(player)
                        }

                        context.source.sender.sendMessage(
                            text("Removed ${entities.size} entities from their current games")
                        )

                        return@executes Command.SINGLE_SUCCESS
                    }))
            .then(literal("timer")
                .then(argument("id", GameArgument)
                    .then(argument("remaining", LongArgumentType.longArg(0))
                        .executes { context ->
                            val game = context.getArgument("id", Game::class.java)
                            val remaining = context.getArgument("remaining", Long::class.java)
                            val phase = game.currentPhase

                            if (phase == null) {
                                context.source.sender.sendMessage(
                                    text("Game with ID ${game.id} does not have an active phase", RED)
                                )
                                return@executes Command.SINGLE_SUCCESS
                            }

                            if (phase !is TimerPhase) {
                                context.source.sender.sendMessage(
                                    text("Game with ID ${game.id} does not have a timer in the current phase", RED)
                                )
                                return@executes Command.SINGLE_SUCCESS
                            }

                            phase.timer.remaining = remaining

                            context.source.sender.sendMessage(
                                text("Set timer for game with ID ${game.id} to ${remaining.seconds} remaining")
                            )

                            return@executes Command.SINGLE_SUCCESS
                        })))
            .build()
    }
}