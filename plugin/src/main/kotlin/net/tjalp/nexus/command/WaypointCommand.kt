package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.WaypointArgument
import net.tjalp.nexus.feature.waypoints.Waypoint
import net.tjalp.nexus.feature.waypoints.WaypointTarget
import org.bukkit.Color
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

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
                    .executes { context -> createWaypoint(context, context.getArgument("id", String::class.java)) }
                    .then(argument("color", ArgumentTypes.namedColor())
                        .executes { context -> createWaypoint(
                            context,
                            id = StringArgumentType.getString(context, "id"),
                            color = Color.fromRGB(context.getArgument("color", TextColor::class.java).value())
                        ) }
                        .then(argument("style", StringArgumentType.word())
                            .executes { context -> createWaypoint(
                                context,
                                id = StringArgumentType.getString(context, "id"),
                                color = Color.fromRGB(context.getArgument("color", TextColor::class.java).value()),
                                style = Key.key(context.getArgument("style", String::class.java))
                            ) }
                            .then(argument("transmitRange", DoubleArgumentType.doubleArg())
                                .executes { context -> createWaypoint(
                                    context,
                                    id = StringArgumentType.getString(context, "id"),
                                    color = Color.fromRGB(context.getArgument("color", TextColor::class.java).value()),
                                    style = Key.key(context.getArgument("style", String::class.java)),
                                    transmitRange = DoubleArgumentType.getDouble(context, "transmitRange"),
                                ) }))))
                .then(literal("test")
                    .executes(::createTestWaypoint)))
            .then(literal("remove")
                .then(argument("id", WaypointArgument)
                    .executes { context -> removeWaypoint(context, context.getArgument("id", Waypoint::class.java)) }))
            .build()
    }

    private fun createWaypoint(
        context: CommandContext<CommandSourceStack>,
        id: String,
        color: Color = Color.WHITE,
        style: Key = Key.key("default"),
        transmitRange: Double = Double.MAX_VALUE,
    ): Int {
        val location = context.source.location
        val world = location.world
        val waypoint = Waypoint(id, location, color, style, transmitRange)

        if (waypoints.availableWaypoints.find { it.id == id } != null) {
            throw ERROR_DUPLICATE_WAYPOINT.create(id)
        }

        waypoints.saveWaypoint(world, waypoint)

        context.source.sender.sendMessage(
            translatable("command.waypoint.create.success", Argument.string("id", id))
        )

        return Command.SINGLE_SUCCESS
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createTestWaypoint(context: CommandContext<CommandSourceStack>): Int {
        val location = context.source.location
        val waypoint = Waypoint(
            id = UUID.randomUUID().toString(),
            worldId = location.world.uid.toKotlinUuid(),
            target = WaypointTarget.Block(location.blockX, location.blockY, location.blockZ),
            colorRgb = Color.RED.asRGB(),
            styleString = "minecraft:default",
            transmitRange = 10.0
        )

        waypoints.saveWaypoint(location.world, waypoint)

        context.source.sender.sendMessage(text("Created test waypoint with ID '${waypoint.id}'"))

        return Command.SINGLE_SUCCESS
    }

    private fun removeWaypoint(context: CommandContext<CommandSourceStack>, waypoint: Waypoint): Int {
        waypoints.removeWaypoint(waypoint.world ?: context.source.location.world, waypoint)

        context.source.sender.sendMessage(
            translatable("command.waypoint.remove.success", Argument.string("id", waypoint.id))
        )

        return Command.SINGLE_SUCCESS
    }
}