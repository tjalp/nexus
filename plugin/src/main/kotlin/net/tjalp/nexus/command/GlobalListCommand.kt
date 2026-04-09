package net.tjalp.nexus.command

import com.ibm.icu.text.ListFormatter
import com.mojang.brigadier.Command
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.servers.NetworkState
import net.tjalp.nexus.util.miniMessage
import net.tjalp.nexus.util.mmStrict
import java.util.*

object GlobalListCommand {

    private val ERROR_DEGRADED_STATE = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(
            translatable("command.globallist.error.degraded_state")
        )
    )

    val aliases = setOf("glist")

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("globallist")
            .requires { NexusPlugin.servers != null }
            .executes { context -> listPlayers(context.source) }
            .build()
    }

    private fun listPlayers(source: CommandSourceStack): Int {
        val feature = NexusPlugin.servers!!

        if (feature.networkState == NetworkState.DEGRADED) throw ERROR_DEGRADED_STATE.create()

        feature.scheduler.launch {
            val servers = async { feature.serverRegistry?.getOnlineServers() }
            val players = async { feature.playerRegistry?.getOnlinePlayers() }
            val playersByServer =
                players.await()?.groupBy { player -> servers.await()?.find { it.id == player.serverId } }
                    ?: emptyMap()
            val formatter = ListFormatter.getInstance(source.sender.getOrDefault(Identity.LOCALE, Locale.US))
            val entries = playersByServer.map { map ->
                val playerNames = map.value.map {
                    val component = text(it.username, MONOCHROME_COLOR)

                    mmStrict.serialize(component)
                }
                val formattedPlayers = formatter.format(playerNames)
                val coloredPlayers = miniMessage.deserialize(formattedPlayers).colorIfAbsent(PRIMARY_COLOR)

                textOfChildren(
                    text("▶ ", DARK_GRAY),
                    translatable(
                        "command.globallist.entry",
                        PRIMARY_COLOR,
                        Argument.numeric("player_count", map.value.size),
                        Argument.component("server_name", miniMessage.deserialize(map.key?.name ?: "Unknown")),
                        Argument.string("server_id", map.key?.id ?: "unknown"),
                        Argument.component("players", coloredPlayers)
                    )
                )
            }

            source.sender.sendMessage(
                translatable(
                    "command.globallist.list",
                    PRIMARY_COLOR,
                    Argument.numeric("player_count", players.await()?.size ?: 0),
                ).appendNewline().append(join(JoinConfiguration.newlines(), entries))
            )
        }

        return Command.SINGLE_SUCCESS
    }
}