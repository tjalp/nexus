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
import net.tjalp.nexus.feature.parkour.ParkourNode
import java.util.concurrent.CompletableFuture

object ParkourNodeArgument : CustomArgumentType.Converted<ParkourNode, String> {

    private val ERROR_UNKNOWN_NODE = DynamicCommandExceptionType { id: Any? ->
        MessageComponentSerializer.message().serialize(translatable(
            "command.parkour.error.unknown_node",
            Argument.string("key", id.toString())
        ))
    }

    override fun convert(nativeType: String): ParkourNode =
        NexusPlugin.parkour?.definitions?.definition?.nodeByKey(nativeType)
            ?: throw ERROR_UNKNOWN_NODE.create(nativeType)

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        NexusPlugin.parkour?.definitions?.definition?.nodes
            ?.map { it.key }
            ?.filter { it.startsWith(builder.remaining, ignoreCase = true) }
            ?.sorted()
            ?.forEach { builder.suggest(it) }

        return builder.buildFuture()
    }
}