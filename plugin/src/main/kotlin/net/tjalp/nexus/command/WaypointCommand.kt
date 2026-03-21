@file:Suppress("UnstableApiUsage")

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
import io.papermc.paper.command.brigadier.argument.resolvers.AngleResolver
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver
import io.papermc.paper.command.brigadier.argument.resolvers.ColumnBlockPositionResolver
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.WaypointArgument
import net.tjalp.nexus.feature.waypoints.Waypoint
import net.tjalp.nexus.feature.waypoints.WaypointTarget
import net.tjalp.nexus.feature.waypoints.save
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
        return literal("nwaypoint")
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
            .then(literal("modify")
                .then(argument("id", WaypointArgument)
                    .then(literal("color")
                        .then(argument("color", ArgumentTypes.namedColor())
                            .executes { context -> setWaypointColor(
                                context.source,
                                context.getArgument("id", Waypoint::class.java),
                                context.getArgument("color", TextColor::class.java)
                            ) })
                        .then(literal("hex")
                            .then(argument("color", ArgumentTypes.hexColor())
                                .executes { context -> setWaypointColor(
                                    context.source,
                                    context.getArgument("id", Waypoint::class.java),
                                    context.getArgument("color", TextColor::class.java)
                                ) })))
                    .then(literal("style")
                        .then(argument("style", ArgumentTypes.key())
                            .executes { context -> setWaypointStyle(
                                context.source,
                                context.getArgument("id", Waypoint::class.java),
                                context.getArgument("style", Key::class.java)
                            ) }))
                    .then(literal("position")
                        .then(literal("block")
                            .then(argument("position", ArgumentTypes.blockPosition())
                                .executes { context -> setWaypointTarget(
                                    context.source,
                                    context.getArgument("id", Waypoint::class.java),
                                    context.getArgument("position", BlockPositionResolver::class.java)
                                ) }))
                        .then(literal("chunk")
                            .then(argument("position", ArgumentTypes.columnBlockPosition())
                                .executes { context -> setWaypointTarget(
                                    context.source,
                                    context.getArgument("id", Waypoint::class.java),
                                    context.getArgument("position", ColumnBlockPositionResolver::class.java)
                                ) }))
                        .then(literal("azimuth")
                            .then(argument("angle", ArgumentTypes.angle())
                                .executes { context -> setWaypointTarget(
                                    context.source,
                                    context.getArgument("id", Waypoint::class.java),
                                    context.getArgument("angle", AngleResolver::class.java)
                                ) })))))
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

        waypoint.save(world)

        context.source.sender.sendMessage(
            translatable("command.waypoint.create.success", PRIMARY_COLOR, Argument.string("id", id))
        )

        return Command.SINGLE_SUCCESS
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createTestWaypoint(context: CommandContext<CommandSourceStack>): Int {
        val location = context.source.location

        for (angle in setOf(0f, .5f * Math.PI.toFloat(), Math.PI.toFloat(), 1.5f * Math.PI.toFloat())) {
            val waypoint = Waypoint(
                id = UUID.randomUUID().toString(),
                worldId = location.world.uid.toKotlinUuid(),
                target = WaypointTarget.Azimuth(angle),
                colorRgb = Color.RED.asRGB(),
                styleString = "minecraft:default",
                transmitRange = 10.0
            )

            waypoint.save(location.world)

            context.source.sender.sendMessage(text("Created test waypoint with ID '${waypoint.id}'", PRIMARY_COLOR))
        }

        return Command.SINGLE_SUCCESS
    }

    private fun setWaypointColor(source: CommandSourceStack, waypoint: Waypoint, color: TextColor): Int {
        waypoint.color = Color.fromRGB(color.value())
        waypoint.save()

        source.sender.sendMessage(translatable("command.waypoint.modify.color", PRIMARY_COLOR))

        return Command.SINGLE_SUCCESS
    }

    private fun setWaypointStyle(source: CommandSourceStack, waypoint: Waypoint, style: Key): Int {
        waypoint.style = style
        waypoint.save()

        source.sender.sendMessage(translatable("command.waypoint.modify.style", PRIMARY_COLOR))

        return Command.SINGLE_SUCCESS
    }

    private fun setWaypointTarget(source: CommandSourceStack, waypoint: Waypoint, resolver: BlockPositionResolver): Int {
        val position = resolver.resolve(source)

        waypoint.target = WaypointTarget.Block(position.blockX(), position.blockY(), position.blockZ())
        waypoint.save()
        source.sender.sendMessage(translatable("command.waypoint.modify.target.block", PRIMARY_COLOR))

        return Command.SINGLE_SUCCESS
    }

    private fun setWaypointTarget(source: CommandSourceStack, waypoint: Waypoint, resolver: ColumnBlockPositionResolver): Int {
        val position = resolver.resolve(source)

        waypoint.target = WaypointTarget.Chunk(position.blockX() / 16, position.blockZ() / 16)
        waypoint.save()
        source.sender.sendMessage(translatable("command.waypoint.modify.target.chunk", PRIMARY_COLOR))

        return Command.SINGLE_SUCCESS
    }

    private fun setWaypointTarget(source: CommandSourceStack, waypoint: Waypoint, resolver: AngleResolver): Int {
        val angleDeg = resolver.resolve(source)
        val angleRadians = angleDeg * (Math.PI.toFloat() / 180.0f)

        waypoint.target = WaypointTarget.Azimuth(angleRadians)
        waypoint.save()
        source.sender.sendMessage(translatable("command.waypoint.modify.target.azimuth", PRIMARY_COLOR))

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