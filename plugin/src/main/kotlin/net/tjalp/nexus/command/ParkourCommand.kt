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
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.ParkourNodeArgument
import net.tjalp.nexus.command.argument.ParkourNodeTypeArgument
import net.tjalp.nexus.command.argument.ParkourSegmentArgument
import net.tjalp.nexus.feature.parkour.NodeType
import net.tjalp.nexus.feature.parkour.ParkourNode
import net.tjalp.nexus.feature.parkour.ParkourRegion
import net.tjalp.nexus.feature.parkour.ParkourSegment
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ThreadLocalRandom

object ParkourCommand {
    private const val VISUALIZER_BURSTS = 240
    private const val VISUALIZER_INTERVAL_TICKS = 1L
    private const val DEFAULT_NODE_HALF_SIZE = 1
    private val activeVisualizers = mutableMapOf<UUID, BukkitTask>()
    private val visualizerBurstsRemaining = mutableMapOf<UUID, Int>()

    private val ERROR_NOT_PLAYER = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("This command can only be run by a player."))
    )
    private val ERROR_NOT_ENABLED = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("The Parkour feature is not enabled."))
    )
    private val ERROR_NO_SESSION = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(translatable("command.parkour.error.no_session"))
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
                    .then(argument("type", ParkourNodeTypeArgument)
                        .then(argument("node", StringArgumentType.word())
                            .executes { ctx ->
                                val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                addNode(
                                    player,
                                    ctx.getArgument("type", NodeType::class.java),
                                    StringArgumentType.getString(ctx, "node")
                                )
                            })))
                .then(literal("delete")
                    .then(argument("node", ParkourNodeArgument)
                        .executes { ctx ->
                            val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                            deleteNode(player, ctx.getArgument("node", ParkourNode::class.java))
                        }))
                .then(literal("modify")
                    .then(argument("node", ParkourNodeArgument)
                        .then(literal("type")
                            .then(argument("type", ParkourNodeTypeArgument)
                                .executes { ctx ->
                                    setNodeType(
                                        ctx.source,
                                        ctx.getArgument("node", ParkourNode::class.java),
                                        ctx.getArgument("type", NodeType::class.java)
                                    )
                                }))
                        .then(literal("name")
                            .then(argument("name", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    setNodeName(
                                        ctx.source,
                                        ctx.getArgument("node", ParkourNode::class.java),
                                        StringArgumentType.getString(ctx, "name")
                                    )
                                }))
                        .then(literal("region")
                            .then(argument("from", ArgumentTypes.blockPosition())
                                .then(argument("to", ArgumentTypes.blockPosition())
                                    .executes { ctx ->
                                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                        setNodeRegion(
                                            player,
                                            ctx.getArgument("node", ParkourNode::class.java),
                                            ctx.getArgument("from", BlockPositionResolver::class.java).resolve(ctx.source),
                                            ctx.getArgument("to", BlockPositionResolver::class.java).resolve(ctx.source)
                                        )
                                    }))))))
            .then(literal("segment")
                .then(literal("add")
                    .then(argument("segment", StringArgumentType.word())
                        .then(argument("from", ParkourNodeArgument)
                            .then(argument("to", ParkourNodeArgument)
                                .executes { ctx ->
                                    val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                    addSegment(
                                        player,
                                        StringArgumentType.getString(ctx, "segment"),
                                        ctx.getArgument("from", ParkourNode::class.java),
                                        ctx.getArgument("to", ParkourNode::class.java)
                                    )
                                }))))
                .then(literal("delete")
                    .then(argument("segment", ParkourSegmentArgument)
                        .executes { ctx ->
                            val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                            deleteSegment(player, ctx.getArgument("segment", ParkourSegment::class.java))
                        }))
                .then(literal("visualize")
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                        visualizeAllSegments(player)
                    }
                    .then(literal("stop")
                        .executes { ctx ->
                            val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                            stopVisualization(player)
                        }))
                .then(literal("modify")
                    .then(argument("segment", ParkourSegmentArgument)
                        .then(literal("name")
                            .then(argument("name", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    setSegmentName(
                                        ctx.source,
                                        ctx.getArgument("segment", ParkourSegment::class.java),
                                        StringArgumentType.getString(ctx, "name")
                                    )
                                })))))
            .then(literal("start")
                .then(argument("node", ParkourNodeArgument)
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                        startRun(player, ctx.getArgument("node", ParkourNode::class.java))
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

    private fun addNode(player: Player, type: NodeType, nodeKey: String): Int {
        val feat = parkour
        val definition = feat.definitions.definition

        if (definition.nodeByKey(nodeKey) != null) throw ERROR_DUPLICATE_NODE.create()
        val loc = player.location
        definition.nodes += ParkourNode(
            key = nodeKey,
            type = type,
            region = ParkourRegion(
                worldId = loc.world.uid,
                minX = loc.blockX - DEFAULT_NODE_HALF_SIZE,
                minY = loc.blockY - DEFAULT_NODE_HALF_SIZE,
                minZ = loc.blockZ - DEFAULT_NODE_HALF_SIZE,
                maxX = loc.blockX + DEFAULT_NODE_HALF_SIZE,
                maxY = loc.blockY + DEFAULT_NODE_HALF_SIZE,
                maxZ = loc.blockZ + DEFAULT_NODE_HALF_SIZE
            )
        )
        feat.definitions.update(definition)
        feat.runtime.reindex()
        player.sendMessage(text("Added $type node '$nodeKey'. Use /parkour node region to adjust.", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    private fun setNodeName(source: CommandSourceStack, node: ParkourNode, name: String): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val idx = definition.nodes.indexOfFirst { it.key == node.key }

        definition.nodes[idx] = node.copy(name = name)
        feat.definitions.update(definition)
        feat.runtime.reindex()

        source.sender.sendMessage(text("Updated ${node.key}'s name to '$name'.", NamedTextColor.GREEN))

        return Command.SINGLE_SUCCESS
    }

    private fun setNodeType(source: CommandSourceStack, node: ParkourNode, type: NodeType): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val idx = definition.nodes.indexOfFirst { it.key == node.key }

        definition.nodes[idx] = node.copy(type = type)
        feat.definitions.update(definition)
        feat.runtime.reindex()

        source.sender.sendMessage(text("Updated ${node.key}'s type to '$type'.", NamedTextColor.GREEN))

        return Command.SINGLE_SUCCESS
    }

    private fun setNodeRegion(player: Player, node: ParkourNode, from: BlockPosition, to: BlockPosition): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val region = ParkourRegion(
            worldId = player.world.uid,
            minX = minOf(from.blockX(), to.blockX()),
            minY = minOf(from.blockY(), to.blockY()),
            minZ = minOf(from.blockZ(), to.blockZ()),
            maxX = maxOf(from.blockX(), to.blockX()),
            maxY = maxOf(from.blockY(), to.blockY()),
            maxZ = maxOf(from.blockZ(), to.blockZ())
        )
        val idx = definition.nodes.indexOfFirst { it.key == node.key }

        definition.nodes[idx] = node.copy(region = region)
        feat.definitions.update(definition)
        feat.runtime.reindex()

        player.sendMessage(text("Updated region for node '${node.name}'.", NamedTextColor.GREEN))

        return Command.SINGLE_SUCCESS
    }

    private fun deleteNode(player: Player, node: ParkourNode): Int {
        val feat = parkour
        val definition = feat.definitions.definition

        definition.removeNode(node.key)
        feat.definitions.update(definition)
        feat.runtime.reindex()

        player.sendMessage(text("Deleted node '${node.name}' and linked segments.", NamedTextColor.YELLOW))

        return Command.SINGLE_SUCCESS
    }

    private fun addSegment(player: Player, segmentKey: String, from: ParkourNode, to: ParkourNode): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        if (definition.segmentByKey(segmentKey) != null) throw ERROR_DUPLICATE_SEGMENT.create()

        definition.segments += ParkourSegment(
            key = segmentKey,
            fromNodeKey = from.key,
            toNodeKey = to.key
        )
        feat.definitions.update(definition)

        player.sendMessage(text("Added segment '$segmentKey' (${from.name} → ${to.name}).", NamedTextColor.GREEN))

        return Command.SINGLE_SUCCESS
    }

    private fun deleteSegment(player: Player, segment: ParkourSegment): Int {
        val feat = parkour
        val definition = feat.definitions.definition

        definition.removeSegment(segment.key)
        feat.definitions.update(definition)

        player.sendMessage(text("Deleted segment '${segment.name}'.", NamedTextColor.YELLOW))

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
            val from = definition.nodeByKey(segment.fromNodeKey) ?: return@mapNotNull null
            val to = definition.nodeByKey(segment.toNodeKey) ?: return@mapNotNull null
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
        player.sendMessage(text("Stopped parkour visualization.", NamedTextColor.YELLOW))
        return Command.SINGLE_SUCCESS
    }

    private fun startSegmentVisualization(player: Player, segments: List<Pair<Location, Location>>) {
        activeVisualizers.remove(player.uniqueId)?.cancel()
        visualizerBurstsRemaining[player.uniqueId] = VISUALIZER_BURSTS

        val task = parkour.scheduler.repeat(initialDelay = 0, interval = VISUALIZER_INTERVAL_TICKS) {
            if (!player.isOnline) {
                activeVisualizers.remove(player.uniqueId)?.cancel()
                visualizerBurstsRemaining.remove(player.uniqueId)
                return@repeat
            }

            segments.forEach { (from, to) ->
                drawDirectionalParticles(player, from, to)
            }

            val left = (visualizerBurstsRemaining[player.uniqueId] ?: VISUALIZER_BURSTS) - 1
            if (left <= 0) {
                activeVisualizers.remove(player.uniqueId)?.cancel()
                visualizerBurstsRemaining.remove(player.uniqueId)
            } else {
                visualizerBurstsRemaining[player.uniqueId] = left
            }
        }
        activeVisualizers[player.uniqueId] = task
    }

    private fun setSegmentName(source: CommandSourceStack, segment: ParkourSegment, name: String): Int {
        val feat = parkour
        val definition = feat.definitions.definition
        val idx = definition.segments.indexOfFirst { it.key == segment.key }

        definition.segments[idx] = segment.copy(name = name)
        feat.definitions.update(definition)

        source.sender.sendMessage(text("Set segment ${segment.key}'s name to $name", NamedTextColor.GREEN))

        return Command.SINGLE_SUCCESS
    }

    private fun nodeCenter(world: World, node: ParkourNode): Location {
        val r = node.region
        return Location(
            world,
            (r.minX + r.maxX) / 2.0 + 0.5,
            (r.minY + r.maxY) / 2.0 + 0.5,
            (r.minZ + r.maxZ) / 2.0 + 0.5
        )
    }

    private fun drawDirectionalParticles(player: Player, from: Location, to: Location) {
        val random = ThreadLocalRandom.current()
        Particle.TRAIL.builder()
            .location(from)
            .offset(.5, .5, .5)
            .receivers(player)
            .count(1)
            .data(Particle.Trail(to, Color.ORANGE, random.nextInt(40) + 10))
            .spawn()
    }

    private fun startRun(player: Player, node: ParkourNode): Int {
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
            source.sender.sendMessage(text("    [${node.type}] ${node.name} (${node.key})", NamedTextColor.WHITE))
        }
        source.sender.sendMessage(text("  Segments: ${definition.segments.size}", NamedTextColor.WHITE))
        definition.segments.forEach { seg ->
            val fromName = definition.nodeByKey(seg.fromNodeKey)?.name ?: seg.fromNodeKey
            val toName = definition.nodeByKey(seg.toNodeKey)?.name ?: seg.toNodeKey
            val status = if (seg.enabled) "" else " (disabled)"
            source.sender.sendMessage(text("    ${seg.name}: $fromName → $toName$status", NamedTextColor.WHITE))
        }
        return Command.SINGLE_SUCCESS
    }
}
