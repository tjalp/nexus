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
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.parkour.NodeType
import net.tjalp.nexus.feature.parkour.ParkourDefinition
import net.tjalp.nexus.feature.parkour.ParkourEdge
import net.tjalp.nexus.feature.parkour.ParkourNode
import net.tjalp.nexus.feature.parkour.ParkourRegion
import net.tjalp.nexus.parkour.ParkourAttachment
import net.tjalp.nexus.util.profile
import org.bukkit.entity.Player
import java.util.*

object ParkourCommand {

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
                            .then(argument("x1", StringArgumentType.word())
                                .then(argument("y1", StringArgumentType.word())
                                    .then(argument("z1", StringArgumentType.word())
                                        .then(argument("x2", StringArgumentType.word())
                                            .then(argument("y2", StringArgumentType.word())
                                                .then(argument("z2", StringArgumentType.word())
                                                    .executes { ctx ->
                                                        val player = ctx.source.sender as? Player ?: throw ERROR_NOT_PLAYER.create()
                                                        val parkourName = StringArgumentType.getString(ctx, "parkour")
                                                        val nodeName = StringArgumentType.getString(ctx, "node")
                                                        val x1 = StringArgumentType.getString(ctx, "x1").toIntOrNull() ?: 0
                                                        val y1 = StringArgumentType.getString(ctx, "y1").toIntOrNull() ?: 0
                                                        val z1 = StringArgumentType.getString(ctx, "z1").toIntOrNull() ?: 0
                                                        val x2 = StringArgumentType.getString(ctx, "x2").toIntOrNull() ?: 0
                                                        val y2 = StringArgumentType.getString(ctx, "y2").toIntOrNull() ?: 0
                                                        val z2 = StringArgumentType.getString(ctx, "z2").toIntOrNull() ?: 0
                                                        setNodeRegion(player, parkourName, nodeName, x1, y1, z1, x2, y2, z2)
                                                    })))))))))))
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
        player: Player, parkourName: String, nodeName: String,
        x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int
    ): Int {
        val feat = parkour
        val def = feat.definitions.getByName(parkourName) ?: throw ERROR_PARKOUR_NOT_FOUND.create()
        val node = def.nodeByName(nodeName) ?: throw ERROR_NODE_NOT_FOUND.create()

        val worldId = player.world.uid
        val region = ParkourRegion(
            worldId = worldId,
            minX = minOf(x1, x2), minY = minOf(y1, y2), minZ = minOf(z1, z2),
            maxX = maxOf(x1, x2), maxY = maxOf(y1, y2), maxZ = maxOf(z1, z2)
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
                text("  ${def.name} (${def.nodes.size} nodes, ${def.edges.size} edges) [${def.id}]", NamedTextColor.WHITE)
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
