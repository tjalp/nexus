package net.tjalp.nexus.command

import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.server.ServerType
import org.bukkit.entity.Player

object ServerCommand {

//    fun create(): Collection<Commands> {
//        return listOf(
//            Commands.literal("server")
//                .then(
//                    Commands.literal("list")
//                        .executes { context ->
//                            listServers(context.source)
//                            1
//                        }
//                )
//                .then(
//                    Commands.literal("transfer")
//                        .then(
//                            Commands.argument("server", io.papermc.paper.command.brigadier.argument.ArgumentTypes.resource())
//                                .executes { context ->
//                                    val source = context.source
//                                    val sender = source.sender
//
//                                    if (sender !is Player) {
//                                        sender.sendMessage(Component.text("Only players can transfer servers", NamedTextColor.RED))
//                                        return@executes 0
//                                    }
//
//                                    val serverIdOrName = context.getArgument("server", String::class.java)
//                                    transferPlayer(sender, serverIdOrName)
//                                    1
//                                }
//                        )
//                )
//                .executes { context ->
//                    listServers(context.source)
//                    1
//                }
//        )
//    }

    private fun listServers(source: CommandSourceStack) {
        val sender = source.sender
        val serversFeature = NexusPlugin.servers

        if (serversFeature == null) {
            sender.sendMessage(Component.text("Servers feature is not enabled", NamedTextColor.RED))
            return
        }

        runBlocking {
            val servers = serversFeature.getOnlineServers()

            if (servers.isEmpty()) {
                sender.sendMessage(Component.text("No servers are currently online", NamedTextColor.YELLOW))
                return@runBlocking
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
    }

    private fun transferPlayer(player: Player, serverIdOrName: String) {
        val serversFeature = NexusPlugin.servers

        if (serversFeature == null) {
            player.sendMessage(Component.text("Servers feature is not enabled", NamedTextColor.RED))
            return
        }

        runBlocking {
            val servers = serversFeature.getOnlineServers()
            val targetServer = servers.find {
                it.id.equals(serverIdOrName, ignoreCase = true) ||
                it.name.equals(serverIdOrName, ignoreCase = true)
            }

            if (targetServer == null) {
                player.sendMessage(
                    Component.text("Server '", NamedTextColor.RED)
                        .append(Component.text(serverIdOrName, NamedTextColor.YELLOW))
                        .append(Component.text("' not found or is offline", NamedTextColor.RED))
                )
                return@runBlocking
            }

            player.sendMessage(
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



