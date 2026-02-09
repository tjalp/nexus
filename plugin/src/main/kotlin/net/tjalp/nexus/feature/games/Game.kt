package net.tjalp.nexus.feature.games

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.scheduler.Scheduler
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import java.nio.file.Files
import java.util.Comparator
import java.util.UUID
import kotlin.math.ceil

enum class GameParticipantState {
    ALIVE,
    DEAD,
    SPECTATOR,
    EXTERNAL_SPECTATOR
}

data class GameParticipant(
    val entity: Entity,
    var state: GameParticipantState = GameParticipantState.ALIVE,
    var team: GameTeam? = null
)

data class GameTeam(
    val id: String,
    val displayName: Component,
    val maxSize: Int? = null
) {
    val members: MutableSet<UUID> = mutableSetOf()

    fun canJoin(): Boolean = maxSize == null || members.size < maxSize
}

data class GameBounds(
    val worldName: String,
    val bounds: BoundingBox
) {
    fun contains(location: Location): Boolean {
        val world = location.world ?: return false
        if (world.name != worldName) return false
        return bounds.contains(location.x, location.y, location.z)
    }
}

class Game(
    val id: String,
    val type: GameType,
    private val feature: GamesFeature,
    val settings: GameSettings,
    joinRequirements: List<GameJoinRequirement>,
    private val phases: List<GamePhase>,
    private val worldSpec: GameWorldSpec
) {

    private val participantsById = mutableMapOf<UUID, GameParticipant>()
    private val teamsById = mutableMapOf<String, GameTeam>()
    private val joinRequirements = joinRequirements.associateBy { it.id }.toMutableMap()
    private val aiDefaults = mutableMapOf<UUID, Boolean>()
    private val restartVotes = mutableSetOf<UUID>()

    private var currentPhaseIndex = -1
    private var phaseElapsedTicks = 0L
    private var phaseTask: BukkitTask? = null
    private var isFinished = false
    private var worldInitialized = false

    val scheduler: Scheduler = feature.scheduler.fork("game/$id")

    var bounds: GameBounds? = null

    val participants: List<GameParticipant>
        get() = participantsById.values.toList()

    val currentPhase: GamePhase?
        get() = phases.getOrNull(currentPhaseIndex)

    val minPlayers: Int
        get() = settings.get(GameSettingKeys.MIN_PLAYERS)

    val maxPlayers: Int
        get() = settings.get(GameSettingKeys.MAX_PLAYERS)

    private val restartVoteThreshold: Double
        get() = settings.get(GameSettingKeys.RESTART_VOTE_THRESHOLD).coerceIn(0.0, 1.0)

    fun start() {
        if (currentPhase != null || phases.isEmpty()) return
        ensureWorldReady()
        scheduler.launch { enterNextPhase() }
    }

    fun join(entity: Entity): JoinResult {
        if (isFinished) {
            return JoinResult.Failure(JoinFailureReason.GAME_FINISHED, text("This game has already finished.", RED))
        }

        if (!feature.registerParticipant(entity, this)) {
            return JoinResult.Failure(JoinFailureReason.ALREADY_IN_GAME, text("Entity is already in another game.", RED))
        }

        if (participantsById.size >= maxPlayers) {
            feature.unregisterParticipant(entity)
            return JoinResult.Failure(JoinFailureReason.GAME_FULL, text("This game is full.", RED))
        }

        val phase = currentPhase
        if (phase != null && !phase.allowJoin) {
            feature.unregisterParticipant(entity)
            return JoinResult.Failure(JoinFailureReason.PHASE_DISALLOWS_JOIN, text("This phase does not allow joining.", RED))
        }

        val requirementFailure = firstRequirementFailure(entity, phase)
        if (requirementFailure != null) {
            feature.unregisterParticipant(entity)
            return JoinResult.Failure(JoinFailureReason.REQUIREMENTS_NOT_MET, requirementFailure.message)
        }

        val participant = GameParticipant(entity)
        participantsById[entity.uniqueId] = participant
        return JoinResult.Success(participant)
    }

    fun leave(entity: Entity) {
        val participant = participantsById.remove(entity.uniqueId) ?: return
        feature.unregisterParticipant(entity)

        participant.team?.members?.remove(entity.uniqueId)
        if (entity is LivingEntity) resetAi(entity)
    }

    fun addTeam(team: GameTeam) {
        teamsById[team.id] = team
    }

    fun setRequirementEnabled(id: String, enabled: Boolean): Boolean {
        val requirement = joinRequirements[id] ?: return false
        if (!requirement.isMutable) return false
        if (requirement is ToggleableJoinRequirement) {
            requirement.enabled = enabled
            return true
        }
        return false
    }

    fun assignToTeam(entity: Entity, teamId: String): Boolean {
        val participant = participantsById[entity.uniqueId] ?: return false
        val team = teamsById[teamId] ?: return false
        if (!team.canJoin()) return false

        participant.team?.members?.remove(entity.uniqueId)
        participant.team = team
        team.members.add(entity.uniqueId)
        return true
    }

    fun updateParticipantState(entity: Entity, state: GameParticipantState): Boolean {
        val participant = participantsById[entity.uniqueId] ?: return false
        participant.state = state
        return true
    }

    fun applyCustomAi(entity: LivingEntity, action: (LivingEntity) -> Unit) {
        aiDefaults.putIfAbsent(entity.uniqueId, entity.hasAI())
        action(entity)
    }

    suspend fun enterNextPhase() {
        if (isFinished) return

        currentPhase?.onExit(this)

        val nextIndex = currentPhaseIndex + 1
        if (nextIndex >= phases.size) {
            finishGame()
            return
        }

        currentPhaseIndex = nextIndex
        phaseElapsedTicks = 0L

        val nextPhase = phases[nextIndex]
        nextPhase.resetTimer()
        validateParticipants(nextPhase)
        nextPhase.onEnter(this)
        beginPhaseLoop()
    }

    suspend fun finishCurrentPhase() {
        val phase = currentPhase ?: return
        phase.finish()
        enterNextPhase()
    }

    fun voteToRestart(player: Player): RestartVoteResult {
        if (!isFinished) return RestartVoteResult(false, false, restartVotes.size, requiredVotes())
        if (!participantsById.containsKey(player.uniqueId)) {
            return RestartVoteResult(false, false, restartVotes.size, requiredVotes())
        }

        val added = restartVotes.add(player.uniqueId)
        val votesNeeded = requiredVotes()
        val shouldRestart = restartVotes.size >= votesNeeded

        if (shouldRestart) restartGame()

        return RestartVoteResult(added, shouldRestart, restartVotes.size, votesNeeded)
    }

    fun dispose() {
        phaseTask?.cancel()
        phaseTask = null

        participantsById.values.toList().forEach { leave(it.entity) }
        scheduler.dispose()

        if (worldSpec.isTemporary && worldInitialized) {
            val world = Bukkit.getWorld(worldName())
            if (world != null) {
                Bukkit.unloadWorld(world, false)
                deleteWorldFolder(world.worldFolder.toPath())
            }
        }
    }

    private fun ensureWorldReady() {
        if (worldInitialized) return
        worldSpec.resolveWorld(id)
        worldInitialized = true
    }

    private fun worldName(): String = when (worldSpec) {
        is GameWorldSpec.Existing -> worldSpec.worldName
        is GameWorldSpec.Temporary -> worldSpec.worldName ?: "game-$id"
    }

    private fun finishGame() {
        isFinished = true
        phaseTask?.cancel()
        phaseTask = null
        restartVotes.clear()
        currentPhaseIndex = phases.size
        participantsById.values.forEach { participant ->
            val entity = participant.entity
            if (entity is LivingEntity) resetAi(entity)
        }
    }

    private fun restartGame() {
        isFinished = false
        restartVotes.clear()
        currentPhaseIndex = -1
        phases.forEach { it.resetTimer() }
        participantsById.values.forEach { it.state = GameParticipantState.ALIVE }
        start()
    }

    private fun beginPhaseLoop() {
        if (phaseTask != null) return

        phaseTask = scheduler.repeat(interval = 20) {
            val phase = currentPhase ?: return@repeat
            phaseElapsedTicks += 20
            phase.updateTimer(20)
            phase.onTick(this@Game, phaseElapsedTicks)

            if (phase.shouldAdvance(this@Game, phaseElapsedTicks)) {
                enterNextPhase()
            }
        }
    }

    private fun validateParticipants(phase: GamePhase) {
        val participantsSnapshot = participantsById.values.toList()
        for (participant in participantsSnapshot) {
            val failure = firstRequirementFailure(participant.entity, phase)
            if (failure != null) {
                if (participant.entity is Player) {
                    participant.entity.sendMessage(failure.message ?: text("Failed join requirement.", RED))
                }
                leave(participant.entity)
            }
        }
    }

    private fun firstRequirementFailure(entity: Entity, phase: GamePhase?): RequirementResult? {
        val allRequirements = joinRequirements.values + (phase?.joinRequirements ?: emptyList())
        return allRequirements.firstNotNullOfOrNull { requirement ->
            val result = requirement.isSatisfied(entity, this)
            if (!result.allowed) result else null
        }
    }

    private fun resetAi(entity: LivingEntity) {
        val defaultAi = aiDefaults.remove(entity.uniqueId) ?: return
        entity.setAI(defaultAi)
    }

    private fun requiredVotes(): Int {
        if (participantsById.isEmpty()) return 0
        return ceil(participantsById.size * restartVoteThreshold).toInt().coerceAtLeast(1)
    }

    private fun deleteWorldFolder(path: java.nio.file.Path) {
        if (!Files.exists(path)) return
        runCatching {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }
}

data class RestartVoteResult(
    val accepted: Boolean,
    val restarted: Boolean,
    val currentVotes: Int,
    val requiredVotes: Int
)

val Entity.currentGame: Game?
    get() = NexusPlugin.games?.getGameFor(this)
