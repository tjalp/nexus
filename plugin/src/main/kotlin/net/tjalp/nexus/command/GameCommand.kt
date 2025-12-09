package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.textOfChildren
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.GameArgument
import net.tjalp.nexus.feature.games.Game
import net.tjalp.nexus.feature.games.GameType
import net.tjalp.nexus.feature.games.GamesFeature

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
                    .then(argument("targets", ArgumentTypes.players())
                        .executes { context ->
                            val game = context.getArgument("id", Game::class.java)
                            val resolver = context.getArgument("targets", PlayerSelectorArgumentResolver::class.java)
                            val players = resolver.resolve(context.source)

                            game.scheduler.launch {
                                val joined = players.map { player ->
                                    async {
                                        val success = game.join(player)

                                        if (!success) {
                                            context.source.sender.sendMessage(text("Failed to join player ${player.name} to game ${game.id}", RED))
                                        }
                                        success
                                    }
                                }.awaitAll()

                                context.source.sender.sendMessage(text("Joined ${joined.count { it }} player(s) to game with ID ${game.id}"))
                            }

                            return@executes Command.SINGLE_SUCCESS
                        })))
            .build()
    }
}