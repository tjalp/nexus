package net.tjalp.nexus.command.argument

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.*
import java.util.concurrent.CompletableFuture

object LocaleArgument : CustomArgumentType.Converted<Locale, String> {

    override fun convert(nativeType: String): Locale = Locale.forLanguageTag(nativeType)

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.string()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        Locale.getAvailableLocales()
            .map { it.toLanguageTag() }
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .sorted()
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }
}