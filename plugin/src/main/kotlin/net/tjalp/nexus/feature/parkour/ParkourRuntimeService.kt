package net.tjalp.nexus.feature.parkour

import com.ibm.icu.number.IntegerWidth
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Precision
import com.ibm.icu.util.MeasureUnit
import io.papermc.paper.advancement.AdvancementDisplay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.format.NamedTextColor.GRAY
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.TextColor.color
import net.kyori.adventure.text.minimessage.translation.Argument
import net.kyori.adventure.title.Title.Times.times
import net.kyori.adventure.title.Title.title
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.model.ParkourSegmentResultsTable
import net.tjalp.nexus.util.*
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/** Manages active parkour sessions, node entry handling, and segment result persistence. */
@OptIn(ExperimentalTime::class)
class ParkourRuntimeService(private val feature: ParkourFeature) {

    private val sessions = mutableMapOf<UUID, ParkourSession>()
    private val pendingStarts = mutableMapOf<UUID, PendingSessionStart>()
    private val chunkIndex = mutableMapOf<String, MutableList<ParkourNode>>()
    private val requiredStartHold = 3.seconds

    fun rebuildIndex() {
        chunkIndex.clear()
        feature.definitions.definition.nodes.forEach(::indexNode)
    }

    fun reindex() = rebuildIndex()

    private fun indexNode(node: ParkourNode) {
        val r = node.region
        val minCX = r.minX shr 4
        val maxCX = r.maxX shr 4
        val minCZ = r.minZ shr 4
        val maxCZ = r.maxZ shr 4
        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                val key = "${r.worldId}:$cx:$cz"
                chunkIndex.getOrPut(key) { mutableListOf() } += node
            }
        }
    }

    fun getSession(playerId: UUID): ParkourSession? = sessions[playerId]
    fun hasSession(playerId: UUID): Boolean = sessions.containsKey(playerId)

    fun startSession(player: Player, entryNode: ParkourNode) {
        beginPendingStart(player, entryNode)
    }

    private fun activateSession(player: Player, entryNode: ParkourNode) {
        pendingStarts.remove(player.uniqueId)

        val session = ParkourSession(
            playerId = player.uniqueId,
            currentNodeKey = entryNode.key,
            path = mutableListOf(entryNode.key),
        )
        sessions[player.uniqueId] = session

        title(
            empty(),
            translatable("parkour.started", PRIMARY_COLOR),
            times(Duration.ZERO.toJavaDuration(), .5.seconds.toJavaDuration(), .5.seconds.toJavaDuration())
        ).sendTo(player)

        translatable("parkour.timer.armed").asEventMessage().sendTo(player)

        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.5f)
    }

    fun stopSession(player: Player) {
        sessions.remove(player.uniqueId)
        pendingStarts.remove(player.uniqueId)
        player.sendActionBar(empty())

        translatable("parkour.stopped", RED).asEventMessage().sendTo(player)
    }

    fun onPlayerMoved(player: Player, fromNodes: List<ParkourNode>, toNodes: List<ParkourNode>) {
        val fromKeys = fromNodes.asSequence().map(ParkourNode::key).toSet()
        val toKeys = toNodes.asSequence().map(ParkourNode::key).toSet()

        val session = sessions[player.uniqueId]
        if (session == null) {
            handlePendingStart(player, fromKeys, toNodes, toKeys)
            return
        }

        handleSessionProgression(player, session, fromKeys, toNodes, toKeys)
    }

    private fun handlePendingStart(
        player: Player,
        fromKeys: Set<String>,
        toNodes: List<ParkourNode>,
        toKeys: Set<String>
    ) {
        val pending = pendingStarts[player.uniqueId]
        if (pending != null) {
            if (pending.entryNodeKey !in toKeys) {
                pendingStarts.remove(player.uniqueId)
                translatable("parkour.pending.cancelled", RED).sendActionBarTo(player)
            }
            return
        }

        val enteredEntries = toNodes.asSequence()
            .filter { it.type == NodeType.ENTRY }
            .filter { it.key !in fromKeys }
            .toList()

        val entryNode = enteredEntries.firstOrNull() ?: return
        beginPendingStart(player, entryNode, isInsideEntryOverride = true)
    }

    private fun beginPendingStart(
        player: Player,
        entryNode: ParkourNode,
        isInsideEntryOverride: Boolean? = null
    ) {
        if (sessions.containsKey(player.uniqueId)) return

        val insideEntry = isInsideEntryOverride ?: run {
            val location = player.location
            entryNode.region.worldId == location.world.uid &&
                    entryNode.region.contains(location.blockX, location.blockY, location.blockZ)
        }
        if (!insideEntry) {
            translatable("parkour.pending.enter_start").asEventMessage().sendTo(player)
            return
        }

        val existing = pendingStarts[player.uniqueId]
        if (existing?.entryNodeKey == entryNode.key) return

        pendingStarts[player.uniqueId] = PendingSessionStart(entryNode.key, Clock.System.now())
    }

    private fun handleSessionProgression(
        player: Player,
        session: ParkourSession,
        fromKeys: Set<String>,
        toNodes: List<ParkourNode>,
        toKeys: Set<String>
    ) {
        val definition = feature.definitions.definition

        // Segment timers only run after leaving the current node region.
        if (session.currentNodeKey in fromKeys && session.currentNodeKey !in toKeys && session.currentSegmentStartTime == null) {
            val now = Clock.System.now()
            session.currentSegmentStartTime = now
            session.lastCheckpointTime = now

            title(
                empty(),
                translatable("parkour.timer.started", PRIMARY_COLOR),
                times(Duration.ZERO.toJavaDuration(), .5.seconds.toJavaDuration(), .5.seconds.toJavaDuration())
            ).sendTo(player)
        }

        val enteredNodes = toNodes.filter { it.key !in fromKeys }
        for (node in enteredNodes) {
            if (node.key == session.currentNodeKey) continue
            val segment = definition.findSegment(session.currentNodeKey, node.key) ?: continue
            val segmentStartTime = session.currentSegmentStartTime
            if (segmentStartTime == null) {
                translatable("parkour.timer.waiting_exit").asEventMessage().sendTo(player)
                continue
            }

            val now = Clock.System.now()
            val duration = now - segmentStartTime
            val timing = SegmentTiming(segmentKey = segment.key, duration = duration)

            session.path += node.key
            session.segmentTimings += timing
            session.currentSegmentStartTime = null
            session.currentNodeKey = node.key
            session.lastCheckpointTime = now
            if (node.type == NodeType.ENTRY) session.lastEntrypointTime = now

            handleSegmentSplit(player, timing, now)

            if (node.type == NodeType.FINISH) {
                finishSession(player, session)
                return
            }

            sendSplitActionBar(player, session)
            return
        }
    }

    private fun handleSegmentSplit(player: Player, timing: SegmentTiming, finishedAt: Instant) {
        val profileId = player.profile().id

        feature.scheduler.launch {
            try {
                val startedAt = finishedAt - timing.duration
                val outcome = transaction(NexusPlugin.database) {
                    val priorForPlayer = ParkourSegmentResultsTable
                        .select(ParkourSegmentResultsTable.duration)
                        .where {
                            (ParkourSegmentResultsTable.profileId eq profileId) and
                                    (ParkourSegmentResultsTable.segmentKey eq timing.segmentKey)
                        }.minOfOrNull { it[ParkourSegmentResultsTable.duration] }

                    val priorGlobal = ParkourSegmentResultsTable
                        .select(ParkourSegmentResultsTable.duration)
                        .where { ParkourSegmentResultsTable.segmentKey eq timing.segmentKey }
                        .minOfOrNull { it[ParkourSegmentResultsTable.duration] }

                    ParkourSegmentResultsTable.insert {
                        it[ParkourSegmentResultsTable.profileId] = profileId
                        it[ParkourSegmentResultsTable.segmentKey] = timing.segmentKey
                        it[ParkourSegmentResultsTable.duration] = timing.duration
                        it[ParkourSegmentResultsTable.startedAt] = startedAt
                        it[ParkourSegmentResultsTable.finishedAt] = finishedAt
                    }

                    val isPersonalBest = priorForPlayer == null || timing.duration < priorForPlayer
                    val isGlobalBest = priorGlobal == null || timing.duration < priorGlobal
                    SplitOutcome(isPersonalBest, isGlobalBest)
                }

                val segment = feature.definitions.definition.segmentByKey(timing.segmentKey)
                val segmentName = segment?.displayName ?: text("???", RED)
                val nameColor = if (outcome.isGlobalBest) color(0xff88ff) else PRIMARY_COLOR
                val timeColor = if (outcome.isGlobalBest) PRIMARY_COLOR else MONOCHROME_COLOR

                AdvancementMessage(
                    title = textOfChildren(
                        segmentName.colorIfAbsent(nameColor),
                        newline(), text("⌚ ${formatDuration(timing.duration, player.locale())}", timeColor)),
                    frame = if (outcome.isGlobalBest) AdvancementDisplay.Frame.CHALLENGE else AdvancementDisplay.Frame.GOAL,
                    icon = segment?.icon ?: @Suppress("UnstableApiUsage") ItemType.CLOCK.createItemStack()
                ).toast(player)

                when {
                    outcome.isGlobalBest -> {
//                        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f)

                        translatable(
                            "parkour.record.global",
                            Argument.component(
                                "time",
                                text("⌚ ${formatDuration(timing.duration, player.locale())}", MONOCHROME_COLOR)
                            ),
                            Argument.component(
                                "segment",
                                segment?.displayName?.colorIfAbsent(MONOCHROME_COLOR) ?: empty()
                            )
                        ).asEventMessage().sendTo(player)
                    }

                    outcome.isPersonalBest -> {
                        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f)

                        translatable(
                            "parkour.record.personal",
                            Argument.component(
                                "time",
                                text("⌚ ${formatDuration(timing.duration, player.locale())}", MONOCHROME_COLOR)
                            ),
                            Argument.component(
                                "segment",
                                segment?.displayName?.colorIfAbsent(MONOCHROME_COLOR) ?: empty()
                            )
                        ).asEventMessage().sendTo(player)
                    }

                    else -> {
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.5f)
                    }
                }
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Parkour] Failed to save segment result: ${e.message}")
            }
        }
    }

    private fun finishSession(player: Player, session: ParkourSession) {
        sessions.remove(player.uniqueId)
        player.sendActionBar(empty())
        val totalDuration = session.segmentTimings.fold(Duration.ZERO) { acc, timing -> acc + timing.duration }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f)

        translatable(
            "parkour.finished",
            Argument.component(
                "time",
                text("⌚ ${formatDuration(totalDuration, player.locale())}", MONOCHROME_COLOR)
            )
        ).asEventMessage().sendTo(player)
    }

    fun getNodesAt(worldId: UUID, x: Int, y: Int, z: Int): List<ParkourNode> {
        val cx = x shr 4
        val cz = z shr 4
        val key = "$worldId:$cx:$cz"
        val candidates = chunkIndex[key] ?: return emptyList()
        return candidates.filter { node -> node.region.contains(x, y, z) }
    }

    fun sendSplitActionBar(player: Player, session: ParkourSession) {
        val total = formatDuration(session.elapsedTime, player.locale())
        val currentSegmentDuration = session.currentSegmentStartTime?.let { Clock.System.now() - it }
            ?: session.segmentTimings.lastOrNull()?.duration
            ?: Duration.ZERO
        val currentSegment = formatDuration(currentSegmentDuration, player.locale())

        translatable(
            "parkour.split_actionbar",
            GRAY,
            Argument.component("total_time", text("⌚ $total", PRIMARY_COLOR)),
            Argument.component("segment_time", text("⌚ $currentSegment", PRIMARY_COLOR)),
        ).sendActionBarTo(player)
    }

    fun tickActionBars() {
        tickPendingStarts()

        sessions.forEach { (playerId, session) ->
            val player = NexusPlugin.server.getPlayer(playerId) ?: return@forEach
            sendSplitActionBar(player, session)
        }
    }

    private fun tickPendingStarts() {
        val now = Clock.System.now()

        pendingStarts.entries.toList().forEach { (playerId, pending) ->
            if (sessions.containsKey(playerId)) {
                pendingStarts.remove(playerId)
                return@forEach
            }

            val player = NexusPlugin.server.getPlayer(playerId)
            if (player == null) {
                pendingStarts.remove(playerId)
                return@forEach
            }

            val entryNode = feature.definitions.definition.nodeByKey(pending.entryNodeKey)
            if (entryNode == null) {
                pendingStarts.remove(playerId)
                return@forEach
            }

            val at = player.location
            val insideEntry = entryNode.region.worldId == at.world.uid && entryNode.region.contains(at.blockX, at.blockY, at.blockZ)
            if (!insideEntry) {
                pendingStarts.remove(playerId)
                translatable("parkour.pending.cancelled", RED).sendActionBarTo(player)
                return@forEach
            }

            val elapsed = now - pending.enteredAt
            val remaining = requiredStartHold - elapsed

            if (remaining > Duration.ZERO) {
                val remainingSeconds = remaining.inWholeMilliseconds / 1000.0
                val formattedRemainingSeconds = NumberFormatter.withLocale(player.locale())
                    .unit(MeasureUnit.SECOND)
                    .decimal(NumberFormatter.DecimalSeparatorDisplay.ALWAYS)
                    .precision(Precision.fixedFraction(1))
                    .format(remainingSeconds)
                    .toString()

                translatable(
                    "parkour.pending.countdown",
                    PRIMARY_COLOR,
                    Argument.component("seconds", text(formattedRemainingSeconds, MONOCHROME_COLOR))
                ).sendActionBarTo(player)
                return@forEach
            }

            pendingStarts.remove(playerId)
            activateSession(player, entryNode)
        }
    }

    private fun formatDuration(duration: Duration, locale: Locale = Locale.US): String {
        val ms = duration.inWholeMilliseconds // TODO rewrite to use duration
        val totalTenths = ms / 100
        val minutes = totalTenths / 600
        val secondsWithTenths = (totalTenths % 600) / 10.0 // 0.0..59.9

        val minuteFormatter = NumberFormatter.withLocale(locale)
            .integerWidth(IntegerWidth.zeroFillTo(2))

        val secondsFormatter = NumberFormatter.withLocale(locale)
            .integerWidth(IntegerWidth.zeroFillTo(2))
            .precision(Precision.fixedFraction(1))

        val mm = minuteFormatter.format(minutes).toString()
        val ssT = secondsFormatter.format(secondsWithTenths).toString()

        return "$mm:$ssT"
    }

    fun clearPlayerSession(playerId: UUID) {
        sessions.remove(playerId)
        pendingStarts.remove(playerId)
    }
}

private data class SplitOutcome(
    val isPersonalBest: Boolean,
    val isGlobalBest: Boolean
)

private data class PendingSessionStart(
    val entryNodeKey: String,
    val enteredAt: Instant
)

