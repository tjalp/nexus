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
import net.tjalp.nexus.feature.parkour.NodeType
import java.util.concurrent.CompletableFuture

object ParkourNodeTypeArgument : CustomArgumentType.Converted<NodeType, String> {

    private val ERROR_UNKNOWN_NODE_TYPE = DynamicCommandExceptionType { nodeType ->
        MessageComponentSerializer.message().serialize(translatable(
            "command.parkour.error.unknown_node_type",
            Argument.string("type", nodeType.toString())
        ))
    }

    override fun convert(nativeType: String): NodeType {
        return try {
            NodeType.valueOf(nativeType.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ERROR_UNKNOWN_NODE_TYPE.create(nativeType)
        }
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        NodeType.entries
            .map { it.name.lowercase() }
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .sorted()
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }
}