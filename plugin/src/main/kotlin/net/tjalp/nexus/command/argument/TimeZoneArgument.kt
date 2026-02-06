package net.tjalp.nexus.command.argument

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import kotlinx.datetime.TimeZone
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import java.util.concurrent.CompletableFuture

object TimeZoneArgument : CustomArgumentType.Converted<TimeZone, String> {

    val ERROR_INVALID_TIME_ZONE = DynamicCommandExceptionType { zoneId ->
        MessageComponentSerializer.message().serialize(
            translatable(
                "command.timezone.error.invalid_time_zone",
                Argument.string("zone_id", zoneId.toString())
            )
        )
    }

    override fun convert(nativeType: String): TimeZone {
        val validZoneId = TimeZone.availableZoneIds.find { it.equals(nativeType, ignoreCase = true) }
            ?: throw ERROR_INVALID_TIME_ZONE.create(nativeType)

        return TimeZone.of(validZoneId)
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.greedyString()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        TimeZone.availableZoneIds
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .sorted()
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }
}