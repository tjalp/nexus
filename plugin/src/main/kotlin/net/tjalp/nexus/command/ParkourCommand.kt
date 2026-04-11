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
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.parkour.*
import net.tjalp.nexus.profile.attachment.ParkourAttachment
import net.tjalp.nexus.util.profile
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

    private val ERROR_NOT_PLAYER = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("This command can only be run by a player."))
    )
    private val ERROR_NOT_ENABLED = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("The Parkour feature is not enabled."))
    )
    private val ERROR_PARKOUR_NOT_FOUND = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("No parkour with that name was found."))
    )
    private val ERROR_NODE_NOT_FOUND = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("No node with that name was found in the parkour."))
    )
    private val ERROR_NO_SESSION = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("You are not currently running a parkour."))
    )
    private val ERROR_DUPLICATE_PARKOUR = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("A parkour with that name already exists."))
    )
    private val ERROR_DUPLICATE_NODE = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("A node with that name already exists in this parkour."))
    )
    private val ERROR_INVALID_ROUTE = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("Invalid route: no valid edge exists between consecutive nodes."))
    )
    private val ERROR_ROUTE_NO_ENTRY = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("The first node of the route must be an ENTRY node."))
    )
    private val ERROR_ROUTE_NO_FINISH = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(text("The last node of the route must be a FINISH node."))
    )

    private val parkour get() = NexusPlugin.parkour ?: throw ERROR_NOT_ENABLED.create()

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("parkour")
            .requires { it.sender.hasPermission("nexus.command.parkour") }
            // ---- create ----
            .then(literal("create")
                .then(argument("name", StringArgumentType.greedyString())
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                        val name = StringArgumentType.getString(ctx, "name")
                        createParkour(player, name)
                    }))
            // ---- node ----
            .then(literal("node")
                .then(literal("add")
                    .then(argument("parkour", StringArgumentType.word())
                        .then(argument("type", StringArgumentType.word())
                            .then(argument("name", StringArgumentType.word())
                                .executes { ctx ->
                                    val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                    val parkourName = StringArgumentType.getString(ctx, "parkour")
                                    val typeStr = StringArgumentType.getString(ctx, "type")
                                    val nodeName = StringArgumentType.getString(ctx, "name")
                                    addNode(player, parkourName, typeStr, nodeName)
                                }))))
                .then(literal("region")
                    .then(argument("parkour", StringArgumentType.word())
                        .then(argument("node", StringArgumentType.word())
                            .then(argument("from", ArgumentTypes.blockPosition())
                                .then(argument("to", ArgumentTypes.blockPosition())
                                    .executes { ctx ->
                                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                        val parkourName = StringArgumentType.getString(ctx, "parkour")
                                        val nodeName = StringArgumentType.getString(ctx, "node")
                                        val from = ctx.getArgument("from", BlockPositionResolver::class.java)
                                            .resolve(ctx.source)
                                        val to = ctx.getArgument("to", BlockPositionResolver::class.java)
                                            .resolve(ctx.source)

                                        setNodeRegion(player, parkourName, nodeName, from, to)
                                    }))))))
            // ---- edge ----
            .then(literal("edge")
                .then(literal("add")
                    .then(argument("parkour", StringArgumentType.word())
                        .then(argument("from", StringArgumentType.word())
                            .then(argument("to", StringArgumentType.word())
                                .executes { ctx ->
                                    val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                    val parkourName = StringArgumentType.getString(ctx, "parkour")
                                    val fromName = StringArgumentType.getString(ctx, "from")
                                    val toName = StringArgumentType.getString(ctx, "to")
                                    addEdge(player, parkourName, fromName, toName)
                                })))))
            // ---- route ----
            .then(literal("route")
                .then(literal("pin")
                    .then(argument("parkour", StringArgumentType.word())
                        .then(argument("nodes", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                val parkourName = StringArgumentType.getString(ctx, "parkour")
                                val nodesStr = StringArgumentType.getString(ctx, "nodes")
                                pinRoute(player, parkourName, nodesStr)
                            })))
                .then(literal("unpin")
                    .then(argument("parkour", StringArgumentType.word())
                        .then(argument("entryNode", StringArgumentType.word())
                            .executes { ctx ->
                                val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                val parkourName = StringArgumentType.getString(ctx, "parkour")
                                val entryNodeName = StringArgumentType.getString(ctx, "entryNode")
                                unpinRoute(player, parkourName, entryNodeName)
                            })))
                .then(literal("pins")
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                        listPins(player)
                    }))
                .then(literal("visualize")
                    .then(argument("parkour", StringArgumentType.word())
                        .then(argument("from", StringArgumentType.word())
                            .then(argument("to", StringArgumentType.word())
                                .executes { ctx ->
                                    val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                    val parkourName = StringArgumentType.getString(ctx, "parkour")
                                    val fromName = StringArgumentType.getString(ctx, "from")
                                    val toName = StringArgumentType.getString(ctx, "to")
                                    visualizeRoute(player, parkourName, fromName, toName)
                                }))))
                .then(literal("visualize-stop")
                    .executes { ctx ->
                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                        stopVisualization(player)
                    }))
            // ---- start / stop ----
            .then(literal("start")
                .then(argument("parkour", StringArgumentType.word())
                    .then(argument("node", StringArgumentType.word())
                        .executes { ctx ->
                            val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                            val parkourName = StringArgumentType.getString(ctx, "parkour")
                            val nodeName = StringArgumentType.getString(ctx, "node")
                            startRun(player, parkourName, nodeName)
                        })))
            .then(literal("stop")
                .executes { ctx ->
                    val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                    stopRun(player)
                })
            // ---- list ----
            .then(literal("list")
                .executes { ctx ->
                    listParkours(ctx.source)
                }
                .then(argument("parkour", StringArgumentType.word())
                    .executes { ctx ->
                        val parkourName = StringArgumentType.getString(ctx, "parkour")
                        listNodes(ctx.source, parkourName)
                    }))
            .build()
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private fun createParkour(player: Player, name: String): Int {
        val feat = parkour
        if (feat.definitions.getByName(name) != null) throw ERROR_DUPLICATE_PARKOUR.create()

        val def = ParkourDefinition(name = name)
        feat.definitions.upsert(def)
        feat.runtime.reindexParkour(def)

        player.sendMessage(text("Created parkour '$name' (${def.id}).", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    private fun addNode(player: Player, parkourName: String, typeStr: String, nodeName: String): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()

        val type = try {
            NodeType.valueOf(typeStr.uppercase())
        } catch (e: IllegalArgumentException) {
            player.sendMessage(text("Unknown node type '$typeStr'. Use ENTRY, CHECKPOINT, or FINISH.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        if (def.nodeByName(nodeName) != null) throw ERROR_DUPLICATE_NODE.create()

        val loc = player.location
        val region = ParkourRegion(
            worldId = loc.world.uid,
            minX = loc.blockX - 1, minY = loc.blockY - 1, minZ = loc.blockZ - 1,
            maxX = loc.blockX + 1, maxY = loc.blockY + 1, maxZ = loc.blockZ + 1
        )
        val node = ParkourNode(name = nodeName, type = type, region = region)
        def.nodes += node
        feat.definitions.upsert(def)
        feat.runtime.reindexParkour(def)

        player.sendMessage(
            text("Added $type node '$nodeName' to parkour '$parkourName'. Region auto-set to a 3x3 around your position. Use /parkour node region to adjust.", NamedTextColor.GREEN)
        )
        return Command.SINGLE_SUCCESS
    }

    private fun setNodeRegion(
        player: Player, parkourName: String, nodeName: String, from: BlockPosition, to: BlockPosition
    ): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()
        val node = def.nodeByName(nodeName) ?: throw ERROR_NODE_NOT_FOUND.create()

        val worldId = player.world.uid
        val region = ParkourRegion(
            worldId = worldId,
            minX = minOf(from.blockX(), to.blockX()),
            minY = minOf(from.blockY(), to.blockY()),
            minZ = minOf(from.blockZ(), to.blockZ()),
            maxX = maxOf(from.blockX(), to.blockX()),
            maxY = maxOf(from.blockY(), to.blockY()),
            maxZ = maxOf(from.blockZ(), to.blockZ()),
        )

        val idx = def.nodes.indexOfFirst { it.id == node.id }
        def.nodes[idx] = node.copy(region = region)
        feat.definitions.upsert(def)
        feat.runtime.reindexParkour(def)

        player.sendMessage(
            text("Region for node '$nodeName' updated in world ${player.world.name}.", NamedTextColor.GREEN)
        )
        return Command.SINGLE_SUCCESS
    }

    private fun addEdge(player: Player, parkourName: String, fromName: String, toName: String): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()
        val fromNode = def.nodeByName(fromName) ?: throw ERROR_NODE_NOT_FOUND.create()
        val toNode = def.nodeByName(toName) ?: throw ERROR_NODE_NOT_FOUND.create()

        def.edges += ParkourEdge(fromNodeId = fromNode.id, toNodeId = toNode.id)
        feat.definitions.upsert(def)

        player.sendMessage(
            text("Added edge '$fromName' → '$toName' in parkour '$parkourName'.", NamedTextColor.GREEN)
        )
        return Command.SINGLE_SUCCESS
    }

    private fun pinRoute(player: Player, parkourName: String, nodesStr: String): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()

        val nodeNames = nodesStr.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        val nodes = nodeNames.map { name ->
            def.nodeByName(name) ?: run {
                player.sendMessage(text("Node '$name' not found.", NamedTextColor.RED))
                throw ERROR_NODE_NOT_FOUND.create()
            }
        }

        if (nodes.isEmpty()) {
            player.sendMessage(text("Please provide at least 2 node names.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        if (nodes.first().type != NodeType.ENTRY) throw ERROR_ROUTE_NO_ENTRY.create()
        if (nodes.last().type != NodeType.FINISH) throw ERROR_ROUTE_NO_FINISH.create()

        // Validate edges between consecutive nodes
        for (i in 0 until nodes.size - 1) {
            if (!def.hasEdge(nodes[i].id, nodes[i + 1].id)) throw ERROR_INVALID_ROUTE.create()
        }

        val nodeIds = nodes.map { it.id }
        val routeKey = feat.runtime.pinRoute(player, def.id, nodes.first().id, nodeIds)

        player.sendMessage(
            text("Pinned route '$parkourName': ${nodeNames.joinToString(" → ")} (key: $routeKey).", NamedTextColor.GREEN)
        )
        player.sendMessage(
            text("Next time you stand on '${nodes.first().name}' this route will auto-track!", NamedTextColor.AQUA)
        )
        return Command.SINGLE_SUCCESS
    }

    private fun unpinRoute(player: Player, parkourName: String, entryNodeName: String): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()
        val node = def.nodeByName(entryNodeName) ?: throw ERROR_NODE_NOT_FOUND.create()

        feat.runtime.unpinRoute(player, node.id)

        player.sendMessage(
            text("Unpinned route starting at '$entryNodeName' in parkour '$parkourName'.", NamedTextColor.YELLOW)
        )
        return Command.SINGLE_SUCCESS
    }

    private fun listPins(player: Player): Int {
        val attachment = try {
            player.profile().attachmentOf<ParkourAttachment>()
        } catch (e: Exception) {
            null
        }

        if (attachment == null || attachment.pinnedRoutes.isEmpty()) {
            player.sendMessage(text("You have no pinned routes.", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        player.sendMessage(text("Your pinned routes:", NamedTextColor.GOLD))
        attachment.pinnedRoutes.forEach { (nodeIdStr, pin) ->
            player.sendMessage(
                text("  Entry node ${nodeIdStr.take(8)}… → ${pin.nodeIds.size} nodes (key: ${pin.routeKey})", NamedTextColor.WHITE)
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun visualizeRoute(player: Player, parkourName: String, fromName: String, toName: String): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()
        val fromNode = def.nodeByName(fromName) ?: throw ERROR_NODE_NOT_FOUND.create()
        val toNode = def.nodeByName(toName) ?: throw ERROR_NODE_NOT_FOUND.create()

        if (fromNode.type != NodeType.ENTRY) {
            player.sendMessage(text("Route visualizer start node must be an ENTRY node.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }
        if (toNode.type != NodeType.ENTRY && toNode.type != NodeType.FINISH) {
            player.sendMessage(text("Route visualizer target must be an ENTRY or FINISH node.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }
        if (fromNode.worldId != toNode.worldId) {
            player.sendMessage(text("Route visualizer only supports nodes in the same world.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }
        if (player.world.uid != fromNode.worldId) {
            player.sendMessage(text("Stand in the same world as this route to visualize it.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        val route = findRoute(def, fromNode.id, toNode.id)
        if (route == null || route.size < 2) {
            player.sendMessage(text("No valid route found from '$fromName' to '$toName'.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        startVisualization(player, route)
        player.sendMessage(
            text(
                "Visualizing route: ${route.joinToString(" → ") { it.name }}",
                NamedTextColor.GREEN
            )
        )
        return Command.SINGLE_SUCCESS
    }

    private fun stopVisualization(player: Player): Int {
        activeVisualizers.remove(player.uniqueId)?.cancel()
        visualizerBurstsRemaining.remove(player.uniqueId)
        player.sendMessage(text("Stopped route visualization.", NamedTextColor.YELLOW))
        return Command.SINGLE_SUCCESS
    }

    private fun findRoute(def: ParkourDefinition, fromId: UUID, toId: UUID): List<ParkourNode>? {
        val visited = mutableSetOf(fromId)
        val parent = mutableMapOf<UUID, UUID?>()
        val queue: ArrayDeque<UUID> = ArrayDeque()
        parent[fromId] = null
        queue.add(fromId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == toId) break

            def.successors(current).forEach { successor ->
                if (visited.add(successor.id)) {
                    parent[successor.id] = current
                    queue.add(successor.id)
                }
            }
        }

        if (!parent.containsKey(toId)) return null

        val nodeIds = mutableListOf<UUID>()
        var cursor: UUID? = toId
        while (cursor != null) {
            nodeIds += cursor
            cursor = parent[cursor]
        }
        nodeIds.reverse()

        return nodeIds.mapNotNull { def.nodeById(it) }
    }

    private fun startVisualization(player: Player, route: List<ParkourNode>) {
        activeVisualizers.remove(player.uniqueId)?.cancel()
        visualizerBurstsRemaining[player.uniqueId] = VISUALIZER_BURSTS

        val task = parkour.scheduler.repeat(initialDelay = 0, interval = VISUALIZER_INTERVAL_TICKS) {
            if (!player.isOnline || player.world.uid != route.first().worldId) {
                activeVisualizers.remove(player.uniqueId)?.cancel()
                visualizerBurstsRemaining.remove(player.uniqueId)
                return@repeat
            }

            renderRoute(player, route)

            val burstsLeft = (visualizerBurstsRemaining[player.uniqueId] ?: VISUALIZER_BURSTS) - 1
            if (burstsLeft <= 0) {
                activeVisualizers.remove(player.uniqueId)?.cancel()
                visualizerBurstsRemaining.remove(player.uniqueId)
            } else {
                visualizerBurstsRemaining[player.uniqueId] = burstsLeft
            }
        }

        activeVisualizers[player.uniqueId] = task
    }

    private fun renderRoute(player: Player, route: List<ParkourNode>) {
        route.forEach { node ->
            val center = nodeCenter(player.world, node)
            val color = when (node.type) {
                NodeType.ENTRY -> Color.LIME
                NodeType.CHECKPOINT -> Color.AQUA
                NodeType.FINISH -> Color.ORANGE
            }
            player.spawnParticle(
                Particle.DUST,
                center,
                16,
                0.25,
                0.25,
                0.25,
                0.0,
                Particle.DustOptions(color, 1.5f)
            )
        }

        for (i in 0 until route.lastIndex) {
            val from = nodeCenter(player.world, route[i])
            val to = nodeCenter(player.world, route[i + 1])
            drawParticleLine(player, from, to)
        }
    }

    private fun nodeCenter(world: org.bukkit.World, node: ParkourNode): Location {
        val r = node.region
        val x = (r.minX + r.maxX) / 2.0 + 0.5
        val y = (r.minY + r.maxY) / 2.0 + 0.5
        val z = (r.minZ + r.maxZ) / 2.0 + 0.5
        return Location(world, x, y, z)
    }

    private fun drawParticleLine(player: Player, from: Location, to: Location) {
        val distance = from.distance(to)
        val steps = (distance * 4).toInt().coerceAtLeast(1)
        val dx = (to.x - from.x) / steps
        val dy = (to.y - from.y) / steps
        val dz = (to.z - from.z) / steps

        for (i in 0..steps) {
            player.spawnParticle(
                Particle.END_ROD,
                from.x + dx * i,
                from.y + dy * i,
                from.z + dz * i,
                1,
                0.0,
                0.0,
                0.0,
                0.0
            )
        }
    }

    private fun startRun(player: Player, parkourName: String, nodeName: String): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()
        val node = def.nodeByName(nodeName) ?: throw ERROR_NODE_NOT_FOUND.create()

        if (node.type != NodeType.ENTRY) {
            player.sendMessage(text("You can only start a run from an ENTRY node.", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        feat.runtime.startSession(player, def, node)
        return Command.SINGLE_SUCCESS
    }

    private fun stopRun(player: Player): Int {
        val feat = parkour
        if (!feat.runtime.hasSession(player.uniqueId)) throw ERROR_NO_SESSION.create()
        feat.runtime.stopSession(player)
        return Command.SINGLE_SUCCESS
    }

    private fun listParkours(source: CommandSourceStack): Int {
        val feat = parkour
        val all = feat.definitions.parkours

        if (all.isEmpty()) {
            source.sender.sendMessage(text("No parkours defined.", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        source.sender.sendMessage(text("Parkours (${all.size}):", NamedTextColor.GOLD))
        all.values.forEach { def ->
            source.sender.sendMessage(
                text("  ${def.name} (${def.nodes.size} nodes, ${def.edges.size} edges)", NamedTextColor.WHITE)
                    .hoverEvent(HoverEvent.showText(text(def.id.toString())))
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun listNodes(source: CommandSourceStack, parkourName: String): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()

        if (def.nodes.isEmpty()) {
            source.sender.sendMessage(text("No nodes in parkour '${def.name}'.", NamedTextColor.YELLOW))
            return Command.SINGLE_SUCCESS
        }

        source.sender.sendMessage(text("Nodes in '${def.name}':", NamedTextColor.GOLD))
        def.nodes.forEach { node ->
            source.sender.sendMessage(
                text("  [${node.type}] ${node.name} (${node.id})", NamedTextColor.WHITE)
            )
        }

        if (def.edges.isNotEmpty()) {
            source.sender.sendMessage(text("Edges:", NamedTextColor.GOLD))
            def.edges.forEach { edge ->
                val fromName = def.nodeById(edge.fromNodeId)?.name ?: edge.fromNodeId.toString()
                val toName = def.nodeById(edge.toNodeId)?.name ?: edge.toNodeId.toString()
                val status = if (edge.enabled) "" else " (disabled)"
                source.sender.sendMessage(text("  $fromName → $toName$status", NamedTextColor.WHITE))
            }
        }
        return Command.SINGLE_SUCCESS
    }
}
