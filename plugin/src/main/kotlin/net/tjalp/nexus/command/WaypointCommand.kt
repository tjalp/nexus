@file:Suppress("UnstableApiUsage")

package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
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
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.WaypointArgument
import net.tjalp.nexus.feature.waypoints.Waypoint
import net.tjalp.nexus.feature.waypoints.WaypointPersistence
import net.tjalp.nexus.feature.waypoints.WaypointTarget
import net.tjalp.nexus.feature.waypoints.save
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
        return literal("nwaypoint")
            .requires { NexusPlugin.waypoints != null && it.sender.hasPermission("nexus.command.waypoint") }
            .then(literal("create")
                .then(argument("id", StringArgumentType.word())
                    .executes { context -> createWaypoint(
                        context.source,
                        id = StringArgumentType.getString(context, "id")
                    ) }
                    .then(argument("persistent", BoolArgumentType.bool())
                        .executes { context -> createWaypoint(
                            context.source,
                            id = StringArgumentType.getString(context, "id"),
                            persistent = BoolArgumentType.getBool(context, "persistent")
                        ) })))
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
                    .then(literal("range")
                        .then(argument("range", DoubleArgumentType.doubleArg(0.0))
                            .executes { context -> setWaypointRange(
                                context.source,
                                context.getArgument("id", Waypoint::class.java),
                                DoubleArgumentType.getDouble(context, "range")
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
                    .executes { context -> removeWaypoint(
                        context.source,
                        context.getArgument("id", Waypoint::class.java)
                    ) }))
            .build()
    }

    private fun createWaypoint(
        source: CommandSourceStack,
        id: String,
        persistent: Boolean = true,
    ): Int {
        val location = source.location
        val world = location.world
        val persistence = if (persistent) WaypointPersistence.Persistent else WaypointPersistence.Runtime
        val waypoint = Waypoint(id, location, Color.WHITE, Key.key("default"))

        if (waypoints.availableWaypoints.find { it.id == id } != null) {
            throw ERROR_DUPLICATE_WAYPOINT.create(id)
        }

        waypoint.save(world, persistence)

        source.sender.sendMessage(
            translatable("command.waypoint.create.success", PRIMARY_COLOR, Argument.string("id", id))
        )

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

    private fun setWaypointRange(source: CommandSourceStack, waypoint: Waypoint, range: Double): Int {
        waypoint.transmitRange = range
        waypoint.save()

        source.sender.sendMessage(translatable("command.waypoint.modify.range", PRIMARY_COLOR))

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

    private fun removeWaypoint(source: CommandSourceStack, waypoint: Waypoint): Int {
        waypoints.removeWaypoint(waypoint.world ?: source.location.world, waypoint)

        source.sender.sendMessage(
            translatable("command.waypoint.remove.success", Argument.string("id", waypoint.id))
        )

        return Command.SINGLE_SUCCESS
    }
}