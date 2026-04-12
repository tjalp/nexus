@file:Suppress("UnstableApiUsage")

package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver
import io.papermc.paper.math.BlockPosition
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.parkour.NodeType
import net.tjalp.nexus.feature.parkour.ParkourNode
import net.tjalp.nexus.feature.parkour.ParkourRegion
import net.tjalp.nexus.feature.parkour.ParkourSegment
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*

object ParkourCommand {
    private const val VISUALIZER_BURSTS = 40
    private const val VISUALIZER_INTERVAL_TICKS = 6L
    private val activeVisualizers = mutableMapOf<UUID, BukkitTask>()
    private val visualizerBurstsRemaining = mutableMapOf<UUID, Int>()
    private val visualizerPhases = mutableMapOf<UUID, Int>()

    private val ERROR_NOT_PLAYER = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("This command can only be run by a player."))
    )
    private val ERROR_NOT_ENABLED = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("The Parkour feature is not enabled."))
    )
    private val ERROR_NODE_NOT_FOUND = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("No node with that name was found."))
    )
    private val ERROR_SEGMENT_NOT_FOUND = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("No segment with that name was found."))
    )
    private val ERROR_NO_SESSION = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("You are not currently running a parkour."))
    )
    private val ERROR_DUPLICATE_NODE = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("A node with that name already exists."))
    )
    private val ERROR_DUPLICATE_SEGMENT = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("A segment with that name already exists."))
    )

    private val parkour get() = NexusPlugin.parkour ?: throw ERROR_NOT_ENABLED.create()

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("parkour")
            .requires { it.sender.hasPermission("nexus.command.parkour") }
            .then(literal("node")
                .then(literal("add")
                    .then(argument("type", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.word())
                            .executes { ctx ->
                                val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                addNode(
                                    player,
                                    StringArgumentType.getString(ctx, "type"),
                                    StringArgumentType.getString(ctx, "name")
                                )
                            })))
                .then(literal("region")
                    .then(argument("node", StringArgumentType.word())
                        .then(argument("from", ArgumentTypes.blockPosition())
                            .then(argument("to", ArgumentTypes.blockPosition())
                                .executes { ctx ->
                                    val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                    setNodeRegion(
                                        player,
                                        StringArgumentType.getString(ctx, "node"),
                                        ctx.getArgument("from", BlockPositionResolver::class.java).resolve(ctx.source),
                                        ctx.getArgument("to", BlockPositionResolver::class.java).resolve(ctx.source)
                                    )
                                }))))
                .then(literal("delete")
                    .then(argument("node", StringArgumentType.word())
                        .executes { ctx ->
                            val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                            deleteNode(player, StringArgumentType.getString(ctx, "node"))
                        })))
            .then(literal("segment")
                .then(literal("add")
                    .then(argument("name", StringArgumentType.word())
                        .then(argument("from", StringArgumentType.word())
                            .then(argument("to", StringArgumentType.word())
                                .executes { ctx ->
                                    val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                    addSegment(
                                        player,
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "from"),
                                        StringArgumentType.getString(ctx, "to")
                                    )
                                }))))
                .then(literal("delete")
                    .then(argument("name", StringArgumentType.word())
                        .executes { ctx ->
                            val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                            deleteSegment(player, StringArgumentType.getString(ctx, "name"))
                        }))
                .then(literal("visualize")
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                        visualizeAllSegments(player)
                    })
                .then(literal("visualize-stop")
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                        stopVisualization(player)
                    }))
            .then(literal("start")
                .then(argument("node", StringArgumentType.word())
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                        startRun(player, StringArgumentType.getString(ctx, "node"))
                    }))
            .then(literal("stop")
                .executes { ctx ->
                    val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                    stopRun(player)
                })
            .then(literal("list")
                .executes { ctx -> listGraph(ctx.source) })
            .build()
    }

    private fun addNode(player: Player, typeStr: String, nodeName: String): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val type = try {
            NodeType.valueOf(typeStr.uppercase())
        } catch (_: IllegalArgumentException) {
            player.sendMessage(text("Unknown node type '$typeStr'. Use ENTRY, CHECKPOINT, or FINISH.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        if (definition.nodeByName(nodeName) != null) throw ERROR_DUPLICATE_NODE.create()
        val loc = player.location
        definition.nodes += ParkourNode(
            name = nodeName,
            type = type,
            region = ParkourRegion(
                worldId = loc.world.uid,
                minX = loc.blockX - 1, minY = loc.blockY - 1, minZ = loc.blockZ - 1,
                maxX = loc.blockX + 1, maxY = loc.blockY + 1, maxZ = loc.blockZ + 1
            )
        )
        feat.definitions.update(definition)
        feat.runtime.reindex()
        player.sendMessage(text("Added $type node '$nodeName'. Use /parkour node region to adjust.", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    private fun setNodeRegion(player: Player, nodeName: String, from: BlockPosition, to: BlockPosition): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val node = definition.nodeByName(nodeName) ?: throw ERROR_NODE_NOT_FOUND.create()

        val region = ParkourRegion(
            worldId = player.world.uid,
            minX = minOf(from.blockX(), to.blockX()),
            minY = minOf(from.blockY(), to.blockY()),
            minZ = minOf(from.blockZ(), to.blockZ()),
            maxX = maxOf(from.blockX(), to.blockX()),
            maxY = maxOf(from.blockY(), to.blockY()),
            maxZ = maxOf(from.blockZ(), to.blockZ())
        )
        val idx = definition.nodes.indexOfFirst { it.id == node.id }
        definition.nodes[idx] = node.copy(region = region)
        feat.definitions.update(definition)
        feat.runtime.reindex()
        player.sendMessage(text("Updated region for node '$nodeName'.", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    private fun deleteNode(player: Player, nodeName: String): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val node = definition.nodeByName(nodeName) ?: throw ERROR_NODE_NOT_FOUND.create()
        definition.removeNode(node.id)
        feat.definitions.update(definition)
        feat.runtime.reindex()
        player.sendMessage(text("Deleted node '$nodeName' and linked segments.", NamedTextColor.YELLOW))
        return Command.SINGLE_SUCCESS
    }

    private fun addSegment(player: Player, segmentName: String, fromName: String, toName: String): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val fromNode = definition.nodeByName(fromName) ?: throw ERROR_NODE_NOT_FOUND.create()
        val toNode = definition.nodeByName(toName) ?: throw ERROR_NODE_NOT_FOUND.create()
        if (definition.segmentByName(segmentName) != null) throw ERROR_DUPLICATE_SEGMENT.create()

        definition.segments += ParkourSegment(
            name = segmentName,
            fromNodeId = fromNode.id,
            toNodeId = toNode.id
        )
        feat.definitions.update(definition)
        player.sendMessage(text("Added segment '$segmentName' ($fromName → $toName).", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    private fun deleteSegment(player: Player, segmentName: String): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val segment = definition.segmentByName(segmentName) ?: throw ERROR_SEGMENT_NOT_FOUND.create()
        definition.removeSegment(segment.id)
        feat.definitions.update(definition)
        player.sendMessage(text("Deleted segment '$segmentName'.", NamedTextColor.YELLOW))
        return Command.SINGLE_SUCCESS
    }

    private fun visualizeAllSegments(player: Player): Int {
        val definition = parkour.definitions.definition
        if (definition.segments.isEmpty()) {
            player.sendMessage(text("No segments to visualize.", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        val worldId = player.world.uid
        val paths = definition.segments.mapNotNull { segment ->
            val from = definition.nodeById(segment.fromNodeId) ?: return@mapNotNull null
            val to = definition.nodeById(segment.toNodeId) ?: return@mapNotNull null
            if (from.worldId != worldId || to.worldId != worldId) return@mapNotNull null
            nodeCenter(player.world, from) to nodeCenter(player.world, to)
        }

        if (paths.isEmpty()) {
            player.sendMessage(text("No segments are in your current world.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        startSegmentVisualization(player, paths)
        player.sendMessage(text("Visualizing ${paths.size} segment(s).", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    private fun stopVisualization(player: Player): Int {
        activeVisualizers.remove(player.uniqueId)?.cancel()
        visualizerBurstsRemaining.remove(player.uniqueId)
        visualizerPhases.remove(player.uniqueId)
        player.sendMessage(text("Stopped parkour visualization.", NamedTextColor.YELLOW))
        return Command.SINGLE_SUCCESS
    }

    private fun startSegmentVisualization(player: Player, segments: List<Pair<Location, Location>>) {
        activeVisualizers.remove(player.uniqueId)?.cancel()
        visualizerBurstsRemaining[player.uniqueId] = VISUALIZER_BURSTS
        visualizerPhases[player.uniqueId] = 0

        val task = parkour.scheduler.repeat(initialDelay = 0, interval = VISUALIZER_INTERVAL_TICKS) {
            if (!player.isOnline) {
                activeVisualizers.remove(player.uniqueId)?.cancel()
                visualizerBurstsRemaining.remove(player.uniqueId)
                visualizerPhases.remove(player.uniqueId)
                return@repeat
            }

            val phase = visualizerPhases[player.uniqueId] ?: 0
            segments.forEachIndexed { index, (from, to) ->
                drawDirectionalParticles(player, from, to, phase + index * 4)
            }
            visualizerPhases[player.uniqueId] = phase + 1

            val left = (visualizerBurstsRemaining[player.uniqueId] ?: VISUALIZER_BURSTS) - 1
            if (left <= 0) {
                activeVisualizers.remove(player.uniqueId)?.cancel()
                visualizerBurstsRemaining.remove(player.uniqueId)
                visualizerPhases.remove(player.uniqueId)
            } else {
                visualizerBurstsRemaining[player.uniqueId] = left
            }
        }
        activeVisualizers[player.uniqueId] = task
    }

    private fun nodeCenter(world: org.bukkit.World, node: ParkourNode): Location {
        val r = node.region
        return Location(
            world,
            (r.minX + r.maxX) / 2.0 + 0.5,
            (r.minY + r.maxY) / 2.0 + 0.5,
            (r.minZ + r.maxZ) / 2.0 + 0.5
        )
    }

    private fun drawDirectionalParticles(player: Player, from: Location, to: Location, phase: Int) {
        val points = 8
        val speed = 0.08
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z

        for (i in 0 until points) {
            val progress = ((phase * speed) + i.toDouble() / points) % 1.0
            player.spawnParticle(
                Particle.DUST,
                from.x + dx * progress,
                from.y + dy * progress,
                from.z + dz * progress,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                Particle.DustOptions(Color.YELLOW, 1.0f)
            )
        }
    }

    private fun startRun(player: Player, nodeName: String): Int {
        val definition = parkour.definitions.definition
        val node = definition.nodeByName(nodeName) ?: throw ERROR_NODE_NOT_FOUND.create()
        if (node.type != NodeType.ENTRY) {
            player.sendMessage(text("You can only start a run from an ENTRY node.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }
        parkour.runtime.startSession(player, node)
        return Command.SINGLE_SUCCESS
    }

    private fun stopRun(player: Player): Int {
        if (!parkour.runtime.hasSession(player.uniqueId)) throw ERROR_NO_SESSION.create()
        parkour.runtime.stopSession(player)
        return Command.SINGLE_SUCCESS
    }

    private fun listGraph(source: CommandSourceStack): Int {
        val definition = parkour.definitions.definition
        source.sender.sendMessage(text("Parkour graph:", NamedTextColor.GOLD))
        source.sender.sendMessage(text("  Nodes: ${definition.nodes.size}", NamedTextColor.WHITE))
        definition.nodes.forEach { node ->
            source.sender.sendMessage(text("    [${node.type}] ${node.name} (${node.id})", NamedTextColor.WHITE))
        }
        source.sender.sendMessage(text("  Segments: ${definition.segments.size}", NamedTextColor.WHITE))
        definition.segments.forEach { seg ->
            val fromName = definition.nodeById(seg.fromNodeId)?.name ?: seg.fromNodeId.toString()
            val toName = definition.nodeById(seg.toNodeId)?.name ?: seg.toNodeId.toString()
            val status = if (seg.enabled) "" else " (disabled)"
            source.sender.sendMessage(text("    ${seg.name}: $fromName → $toName$status", NamedTextColor.WHITE))
        }
        return Command.SINGLE_SUCCESS
    }
}
