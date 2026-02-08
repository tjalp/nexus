package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.WaypointArgument
import net.tjalp.nexus.feature.waypoints.Waypoint
import org.bukkit.Color

object WaypointCommand {

    private val ERROR_DUPLICATE_WAYPOINT = DynamicCommandExceptionType { id: Any? ->
        MessageComponentSerializer.message().serialize(translatable(
            "command.waypoint.create.duplicate",
            Argument.string("id", id.toString())
        ))
    }

    private val waypoints
        get() = NexusPlugin.waypoints ?: error("Waypoints feature is not enabled")

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("waypoint")
            .requires { NexusPlugin.waypoints != null && it.sender.hasPermission("nexus.command.waypoint") }
            .then(literal("create")
                .then(argument("id", StringArgumentType.string())
                    .executes { context -> createWaypoint(context, context.getArgument("id", String::class.java)) }))
            .then(literal("remove")
                .then(argument("id", WaypointArgument)
                    .executes { context -> removeWaypoint(context, context.getArgument("id", Waypoint::class.java)) }))
            .build()
    }

    private fun createWaypoint(context: CommandContext<CommandSourceStack>, id: String): Int {
        val location = context.source.location
        val world = location.world
        val waypoint = Waypoint(id, location, Color.WHITE, Key.key("default"))

        if (waypoints.availableWaypoints.find { it.id == id } != null) {
            throw ERROR_DUPLICATE_WAYPOINT.create(id)
        }

        waypoints.saveWaypoint(world, waypoint)
        waypoint.spawn(world)

        context.source.sender.sendMessage(
            translatable("command.waypoint.create.success", Argument.string("id", id))
        )

        return Command.SINGLE_SUCCESS
    }

    private fun removeWaypoint(context: CommandContext<CommandSourceStack>, waypoint: Waypoint): Int {
        waypoints.removeWaypoint(waypoint.location.world ?: context.source.location.world, waypoint)

        context.source.sender.sendMessage(
            translatable("command.waypoint.remove.success", Argument.string("id", waypoint.id))
        )

        return Command.SINGLE_SUCCESS
    }
}