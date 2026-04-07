package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.*
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.server.ServerType
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

object ServerCommand {

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("server")
            .requires(restricted { it.sender.hasPermission("nexus.command.server") && NexusPlugin.servers != null })
            .then(literal("list")
                .executes { context -> listServers(context.source) })
            .then(literal("transfer")
                .then(argument("server", StringArgumentType.string())
                    .suggests(::suggestServers)
                    .then(argument("targets", ArgumentTypes.players())
                        .executes { context ->
                            val targets = context.getArgument("targets", PlayerSelectorArgumentResolver::class.java)
                                .resolve(context.source)
                            transfer(
                                context,
                                targets,
                                context.getArgument("server",String::class.java)
                            )
                        })
                    .requires { it.executor is Player }
                    .executes { context ->
                        transfer(
                            context,
                            setOf(context.source.executor as Player),
                            context.getArgument("server", String::class.java)
                        )
                    }))
            .build()
    }

    private fun listServers(source: CommandSourceStack): Int {
        val sender = source.sender
        val serversFeature = NexusPlugin.servers

        if (serversFeature == null) {
            sender.sendMessage(Component.text("Servers feature is not enabled", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        serversFeature.scheduler.launch {
            val servers = serversFeature.getOnlineServers()

            if (servers.isEmpty()) {
                sender.sendMessage(Component.text("No servers are currently online", NamedTextColor.YELLOW))
                return@launch
            }

            sender.sendMessage(Component.text("Online Servers:", PRIMARY_COLOR))
            servers.forEach { server ->
                val typeColor = when (server.type) {
                    ServerType.CREATIVE -> NamedTextColor.AQUA
                    ServerType.SURVIVAL -> NamedTextColor.GREEN
                    ServerType.MINIGAMES -> NamedTextColor.GOLD
                    ServerType.LOBBY -> NamedTextColor.LIGHT_PURPLE
                    ServerType.OTHER -> NamedTextColor.GRAY
                }

                sender.sendMessage(
                    Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(server.name, PRIMARY_COLOR))
                        .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                        .append(Component.text(server.id, NamedTextColor.GRAY))
                        .append(Component.text(") - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(server.type.name, typeColor))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("${server.host}:${server.port}", NamedTextColor.GRAY))
                )
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun suggestServers(context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val serversFeature = NexusPlugin.servers ?: return builder.buildFuture()

        val future = serversFeature.scheduler.future {
            val servers = serversFeature.getOnlineServers()
            val input = builder.remaining.lowercase()

            servers
                .filter { it.id.startsWith(input, ignoreCase = true) || it.name.startsWith(input, ignoreCase = true) }
                .sortedBy { it.name }
                .forEach { server ->
                    builder.suggest(server.id)
                }

            builder.build()
        }

        return future
    }

    private fun transfer(context: CommandContext<CommandSourceStack>, players: Collection<Player>, serverIdOrName: String): Int {
        val sender = context.source.sender
        val serversFeature = NexusPlugin.servers

        if (serversFeature == null) {
            sender.sendMessage(Component.text("Servers feature is not enabled", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        if (serversFeature.networkState == net.tjalp.nexus.feature.servers.NetworkState.DEGRADED) {
            sender.sendMessage(
                Component.text("Transfers are currently disabled: network is in DEGRADED mode (Redis unavailable).", NamedTextColor.RED)
            )
            return Command.SINGLE_SUCCESS
        }

        serversFeature.scheduler.launch {
            val servers = serversFeature.getOnlineServers()
            val targetServer = servers.find {
                it.id.equals(serverIdOrName, ignoreCase = true) ||
                it.name.equals(serverIdOrName, ignoreCase = true)
            }

            if (targetServer == null) {
                sender.sendMessage(
                    Component.text("Server '", NamedTextColor.RED)
                        .append(Component.text(serverIdOrName, NamedTextColor.YELLOW))
                        .append(Component.text("' not found or is offline", NamedTextColor.RED))
                )
                return@launch
            }

            players.forEach { player ->
                launch {
                    player.sendActionBar(
                        Component.text("Transferring to ", NamedTextColor.GRAY)
                            .append(Component.text(targetServer.name, PRIMARY_COLOR))
                            .append(Component.text("...", NamedTextColor.GRAY))
                    )

                    val success = serversFeature.transferPlayer(player, targetServer.id)

                    if (!success) {
                        player.sendMessage(Component.text("Failed to transfer to server", NamedTextColor.RED))
                    }
                }
            }
        }

        return Command.SINGLE_SUCCESS
    }
}



