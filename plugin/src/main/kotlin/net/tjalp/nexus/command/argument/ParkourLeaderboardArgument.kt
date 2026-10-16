package net.tjalp.nexus.command.argument

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.parkour.ParkourLeaderboard
import java.util.concurrent.CompletableFuture

object ParkourLeaderboardArgument : CustomArgumentType.Converted<ParkourLeaderboard, String> {

    private val ERROR_UNKNOWN_LEADERBOARD = DynamicCommandExceptionType { id: Any? ->
        MessageComponentSerializer.message().serialize(translatable(
            "command.parkour.error.unknown_leaderboard",
            Argument.string("key", id.toString())
        ))
    }

    override fun convert(nativeType: String): ParkourLeaderboard {
        return NexusPlugin.parkour?.definitions?.definition?.leaderboards?.first { it.key == nativeType }
            ?: throw ERROR_UNKNOWN_LEADERBOARD.create(nativeType)
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        NexusPlugin.parkour?.definitions?.definition?.leaderboards
            ?.map { it.key }
            ?.filter { it.startsWith(builder.remaining, ignoreCase = true) }
            ?.sorted()
            ?.forEach { builder.suggest(it) }

        return builder.buildFuture()
    }
}