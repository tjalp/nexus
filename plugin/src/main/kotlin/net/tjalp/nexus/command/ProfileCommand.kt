package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.translate
import org.bukkit.command.CommandSender
import java.util.*

object ProfileCommand {

    private val ERROR_UNKNOWN_PLAYER = DynamicCommandExceptionType { name ->
        MessageComponentSerializer.message()
            .serialize(translatable("command.profile.error.unknown_player", Argument.string("name", name.toString())))
    }

    private val ERROR_NO_PROFILE = DynamicCommandExceptionType { name ->
        MessageComponentSerializer.message()
            .serialize(translatable("command.profile.error.no_profile", Argument.string("name", name.toString())))
    }

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("profile")
            .requires(Commands.restricted { source -> source.sender.hasPermission("nexus.command.profile") })
            .then(literal("get")
                .then(literal("id")
                    .then(argument("unique_id", ArgumentTypes.uuid())
                        .executes { context -> executeWithId(context) }
                        .then(argument("use_cache", BoolArgumentType.bool())
                            .executes { context -> executeWithId(context, context.getArgument("use_cache", Boolean::class.java)) })))
                .then(literal("name")
                    .then(argument("name", StringArgumentType.string())
                        .executes { context -> executeWithName(context) }
                        .then(argument("use_cache", BoolArgumentType.bool())
                            .executes { context -> executeWithName(context, context.getArgument("use_cache", Boolean::class.java)) }))))
            .then(literal("delete")
                .then(argument("username", StringArgumentType.string())
                    .executes { context -> executeDelete(context, context.getArgument("username", String::class.java)) }))
            .build()
    }

    private fun executeWithId(context: CommandContext<CommandSourceStack>, useCache: Boolean = true): Int {
        val uniqueId = context.getArgument("unique_id", UUID::class.java)

        fetchProfile(uniqueId, context.source.sender, useCache)

        return Command.SINGLE_SUCCESS
    }

    private fun executeWithName(context: CommandContext<CommandSourceStack>, useCache: Boolean = true): Int {
        val argument = context.getArgument( "name", String::class.java)

        NexusPlugin.scheduler.launch {
            val uniqueId = withContext(Dispatchers.IO) { NexusPlugin.server.getPlayerUniqueId(argument) }

            if (uniqueId == null) {
                context.source.sender.sendRichMessage("<red>No player found with name '$argument'</red>")
                return@launch
            }

            fetchProfile(uniqueId, context.source.sender, useCache)
        }

        return Command.SINGLE_SUCCESS
    }

    private fun fetchProfile(uniqueId: UUID, sender: CommandSender, useCache: Boolean) {
        NexusPlugin.scheduler.launch {
            val profile = NexusPlugin.profiles.get(uniqueId, bypassCache = !useCache)

            if (profile == null) {
                sender.sendRichMessage("<red>No profile found for ID $uniqueId</red>")
                return@launch
            }

            sender.sendRichMessage("<green>Profile for ID $uniqueId:</green>\n$profile")
        }
    }

    private fun executeDelete(context: CommandContext<CommandSourceStack>, username: String): Int {
        NexusPlugin.scheduler.launch {
            try {
                val uniqueId = withContext(Dispatchers.IO) { NexusPlugin.server.getPlayerUniqueId(username) }
                    ?: throw ERROR_UNKNOWN_PLAYER.create(username)
                val targetPlayer = NexusPlugin.server.getPlayer(uniqueId)

                targetPlayer?.kick(translatable("command.profile.delete.kick", RED)
                    .translate(targetPlayer.locale()))

                val deleted = NexusPlugin.profiles.delete(uniqueId)

                if (!deleted) throw ERROR_NO_PROFILE.create(username)
            } catch (e: CommandSyntaxException) {
                context.source.sender.sendMessage(
                    text(e.message ?: "An error occurred while deleting the profile", RED)
                )
                return@launch
            }

            context.source.sender.sendMessage(translatable(
                "command.profile.delete.success",
                Argument.string("name", username)
            ))
        }

        return Command.SINGLE_SUCCESS
    }
}