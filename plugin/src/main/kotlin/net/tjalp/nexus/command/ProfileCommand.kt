package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.tjalp.nexus.NexusPlugin
import org.bukkit.command.CommandSender
import java.util.*

object ProfileCommand {

    fun create(nexus: NexusPlugin): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("profile")
            .requires(Commands.restricted { source -> source.sender.hasPermission("nexus.command.profile") })
            .then(Commands.literal("get")
                .then(Commands.literal("id")
                    .then(Commands.argument("unique_id", ArgumentTypes.uuid())
                        .executes { context -> executeWithId(nexus, context) }
                        .then(Commands.argument("use_cache", BoolArgumentType.bool())
                            .executes { context -> executeWithId(nexus, context, context.getArgument("use_cache", Boolean::class.java)) })))
                .then(Commands.literal("name")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes { context -> executeWithName(nexus, context) }
                        .then(Commands.argument("use_cache", BoolArgumentType.bool())
                            .executes { context -> executeWithName(nexus, context, context.getArgument("use_cache", Boolean::class.java)) }))))
            .build()
    }

    private fun executeWithId(nexus: NexusPlugin, context: CommandContext<CommandSourceStack>, useCache: Boolean = true): Int {
        val uniqueId = context.getArgument("unique_id", UUID::class.java)

        fetchProfile(nexus, uniqueId, context.source.sender, useCache)

        return Command.SINGLE_SUCCESS
    }

    private fun executeWithName(nexus: NexusPlugin, context: CommandContext<CommandSourceStack>, useCache: Boolean = true): Int {
        val argument = context.getArgument( "name", String::class.java)

        CoroutineScope(Dispatchers.Default).launch {
            val uniqueId = nexus.server.getPlayerUniqueId(argument)

            if (uniqueId == null) {
                context.source.sender.sendRichMessage("<red>No player found with name '$argument'</red>")
                return@launch
            }

            fetchProfile(nexus, uniqueId, context.source.sender, useCache)
        }

        return Command.SINGLE_SUCCESS
    }

    private fun fetchProfile(nexus: NexusPlugin, uniqueId: UUID, sender: CommandSender, useCache: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val profile = nexus.profiles.get(uniqueId, bypassCache = !useCache)

            if (profile == null) {
                sender.sendRichMessage("<red>No profile found for ID $uniqueId</red>")
                return@launch
            }

            sender.sendRichMessage("<green>Profile for ID $uniqueId:</green>\n$profile")
        }
    }
}