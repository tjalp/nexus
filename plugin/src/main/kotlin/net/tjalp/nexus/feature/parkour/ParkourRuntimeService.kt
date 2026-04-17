package net.tjalp.nexus.feature.parkour

import com.ibm.icu.number.IntegerWidth
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Precision
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
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/** Manages active parkour sessions, node entry handling, and segment result persistence. */
@OptIn(ExperimentalTime::class)
class ParkourRuntimeService(private val feature: ParkourFeature) {

    private val sessions = mutableMapOf<UUID, RunSession>()
    private val chunkIndex = mutableMapOf<String, MutableList<ParkourNode>>()

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

    fun getSession(playerId: UUID): RunSession? = sessions[playerId]
    fun hasSession(playerId: UUID): Boolean = sessions.containsKey(playerId)

    fun startSession(player: Player, entryNode: ParkourNode) {
        val session = RunSession(
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
    }

    fun stopSession(player: Player) {
        sessions.remove(player.uniqueId)
        player.sendActionBar(empty())

        translatable("parkour.stopped", RED).asEventMessage().sendTo(player)
    }

    fun onNodeEntered(player: Player, node: ParkourNode) {
        val definition = feature.definitions.definition
        val session = sessions[player.uniqueId]
        if (session == null) {
            if (node.type == NodeType.ENTRY) startSession(player, node)
            return
        }

        if (node.key == session.currentNodeKey) return
        val segment = definition.findSegment(session.currentNodeKey, node.key) ?: return

        val now = Clock.System.now()
        val duration = now - session.currentSegmentStartTime
        val timing = SegmentTiming(segmentKey = segment.key, duration = duration)

        session.path += node.key
        session.segmentTimings += timing
        session.currentSegmentStartTime = now
        session.currentNodeKey = node.key
        session.lastCheckpointTime = now
        if (node.type == NodeType.ENTRY) session.lastEntrypointTime = now

        handleSegmentSplit(player, timing, now)

        if (node.type == NodeType.FINISH) {
            finishSession(player, session)
            return
        }

        sendSplitActionBar(player, session)
    }

    private fun handleSegmentSplit(player: Player, timing: SegmentTiming, finishedAt: Instant) {
        val profileId = player.profile().id

        feature.scheduler.launch {
            try {
                val startedAt = finishedAt - timing.duration
                val outcome = transaction(NexusPlugin.database) {
                    val priorForPlayer = ParkourSegmentResultsTable
                        .selectAll()
                        .where {
                            (ParkourSegmentResultsTable.profileId eq profileId) and
                                    (ParkourSegmentResultsTable.segmentKey eq timing.segmentKey)
                        }.minOfOrNull { it[ParkourSegmentResultsTable.duration] }

                    val priorGlobal = ParkourSegmentResultsTable
                        .selectAll()
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
//                val subtitle = text().color(GRAY)
//
//                subtitle.append(segmentName.colorIfAbsent(PRIMARY_COLOR)).append(text(" | "))
//                subtitle.append(text("⌚ ${formatDuration(timing.duration, player.locale())}", PRIMARY_COLOR))
//
//                title(
//                    empty(),
//                    subtitle.build(),
//                    times(Duration.ZERO.toJavaDuration(), 3.seconds.toJavaDuration(), .5.seconds.toJavaDuration())
//                ).sendTo(player)

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
                        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f)

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
                        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.5f)
                    }
                }
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Parkour] Failed to save segment result: ${e.message}")
            }
        }
    }

    private fun finishSession(player: Player, session: RunSession) {
        sessions.remove(player.uniqueId)
        player.sendActionBar(empty())
        val totalDuration = session.segmentTimings.fold(Duration.ZERO) { acc, timing -> acc + timing.duration }
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f)

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

    fun sendSplitActionBar(player: Player, session: RunSession) {
        val total = formatDuration(session.elapsedTime, player.locale())
        val currentSegment = formatDuration(Clock.System.now() - session.currentSegmentStartTime, player.locale())

        translatable(
            "parkour.split_actionbar",
            GRAY,
            Argument.component("total_time", text("⌚ $total", PRIMARY_COLOR)),
            Argument.component("segment_time", text("⌚ $currentSegment", PRIMARY_COLOR)),
        ).sendActionBarTo(player)
    }

    fun tickActionBars() {
        sessions.forEach { (playerId, session) ->
            val player = NexusPlugin.server.getPlayer(playerId) ?: return@forEach
            sendSplitActionBar(player, session)
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
    }
}

private data class SplitOutcome(
    val isPersonalBest: Boolean,
    val isGlobalBest: Boolean
)
