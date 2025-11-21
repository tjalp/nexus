package net.tjalp.nexus.command.argument

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.kyori.adventure.text.Component
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusServices
import java.util.concurrent.CompletableFuture

object FeatureArgument : CustomArgumentType.Converted<Feature, String> {

    private val ERROR_INVALID_FEATURE: DynamicCommandExceptionType = DynamicCommandExceptionType { feature: Any? ->
        MessageComponentSerializer.message().serialize(Component.text("Unknown feature: $feature"))
    }

    override fun convert(nativeType: String): Feature {
        return NexusServices.get<NexusPlugin>().features.find { it.name.equals(nativeType, ignoreCase = true) }
            ?: throw ERROR_INVALID_FEATURE.create(nativeType)
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.string()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        NexusServices.get<NexusPlugin>().features
            .map { it.name }
            .filter { it.startsWith(builder.remainingLowerCase) }
            .sorted()
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }
}