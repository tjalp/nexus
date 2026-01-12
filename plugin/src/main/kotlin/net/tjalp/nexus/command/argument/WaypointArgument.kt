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
import net.tjalp.nexus.feature.waypoints.Waypoint
import net.tjalp.nexus.feature.waypoints.WaypointsFeature
import java.util.concurrent.CompletableFuture

object WaypointArgument : CustomArgumentType.Converted<Waypoint, String> {

    private val ERROR_UNKNOWN_WAYPOINT = DynamicCommandExceptionType { id: Any? ->
        MessageComponentSerializer.message().serialize(translatable(
            "command.waypoint.unknown",
            Argument.string("id", id.toString())
        ))
    }

    override fun convert(nativeType: String): Waypoint {
        return WaypointsFeature.availableWaypoints.find { it.id.equals(nativeType, ignoreCase = true) }
            ?: throw ERROR_UNKNOWN_WAYPOINT.create(nativeType)
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.string()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        WaypointsFeature.availableWaypoints
            .map { it.id }
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .sorted()
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }
}