package net.tjalp.nexus.feature.games.minigame

import net.tjalp.nexus.feature.games.*
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team
import java.util.*

/**
 * Recommended command syntax (not implemented here):
 * - /game create <template>
 * - /game publish <id>
 * - /game unpublish <id>
 * - /game start <id>
 * - /game cancel <id>
 * - /game points add <id> <target> <points>
 * - /game phase next <id>
 */
abstract class MinigameGame(
    feature: GamesFeature,
    type: GameType,
    val definition: MinigameDefinition,
    val host: UUID? = null
) : Game(feature = feature, type = type) {

    val scores: Map<UUID, Int> get() = _scores.toMap()
    val publicationState: PublicationState get() = _publicationState

    private val _scores = mutableMapOf<UUID, Int>()
    private val teamsByKey = linkedMapOf<String, Team>()
    private val teamByEntity = mutableMapOf<UUID, String>()
    private var _publicationState = PublicationState.DRAFT
    private var phaseIndex = -1

    enum class PublicationState {
        DRAFT,
        PUBLISHED,
        RUNNING,
        CANCELLED
    }

    open fun canManage(actor: Entity): Boolean {
        val isHost = host != null && host == actor.uniqueId
        val isAdmin = (actor as? Player)?.hasPermission("nexus.command.game") == true || actor.isOp

        return isHost || isAdmin
    }

    fun publish(actor: Entity): Boolean {
        if (!canManage(actor) || _publicationState != PublicationState.DRAFT) return false
        _publicationState = PublicationState.PUBLISHED
        return true
    }

    fun unpublish(actor: Entity): Boolean {
        if (!canManage(actor) || _publicationState != PublicationState.PUBLISHED) return false
        _publicationState = PublicationState.DRAFT
        return true
    }

    suspend fun start(actor: Entity): Boolean {
        if (!canManage(actor) || _publicationState != PublicationState.PUBLISHED) return false
        _publicationState = PublicationState.RUNNING
        phaseIndex = -1
        enterNextPhase()
        return true
    }

    fun cancel(actor: Entity): Boolean {
        if (!canManage(actor)) return false
        _publicationState = PublicationState.CANCELLED
        end()
        return true
    }

    fun manualAddPoints(actor: Entity, target: Entity, points: Int): Boolean {
        if (!canManage(actor)) return false
        applyPoints(target, points)
        return true
    }

    suspend fun manualNextPhase(actor: Entity): Boolean {
        if (!canManage(actor)) return false
        enterNextPhase()
        return true
    }

    fun applyPoints(entity: Entity, amount: Int) {
        val total = (_scores[entity.uniqueId] ?: 0) + amount
        _scores[entity.uniqueId] = total

        if (definition.pointsToWin > 0 && total >= definition.pointsToWin) {
            onPointsTargetReached(entity)
        }
    }

    open fun onPointsTargetReached(entity: Entity) {}

    fun applyTeamPoint(entity: Entity, amount: Int) {
        val team = teamByEntity[entity.uniqueId] ?: return applyPoints(entity, amount)
        val members = teamByEntity.filterValues { it == team }.keys

        members.forEach { memberId ->
            _scores[memberId] = (_scores[memberId] ?: 0) + amount
        }

        if (definition.pointsToWin > 0 && members.any { (_scores[it] ?: 0) >= definition.pointsToWin }) {
            onPointsTargetReached(entity)
        }
    }

    fun applyProjectileModifiers(shooter: Entity, target: Entity, projectile: org.bukkit.entity.Projectile) {
        definition.modifiers.forEach { modifierId ->
            MinigameModifierRegistry.get(modifierId)?.onProjectileHit(this, shooter, target, projectile)
        }
    }

    fun isSameTeam(first: Entity, second: Entity): Boolean {
        if (!definition.teams.enabled) return false
        val a = teamByEntity[first.uniqueId]
        val b = teamByEntity[second.uniqueId]

        return a != null && a == b
    }

    fun isScoringTrigger(trigger: ScoreTrigger, projectileType: org.bukkit.entity.EntityType): Boolean {
        return when (trigger) {
            is ScoreTrigger.ProjectileHit -> projectileType in trigger.projectileTypes
            else -> false
        }
    }

    fun pointsForKill(): Int = definition.scoringRules
        .filter { it.trigger == ScoreTrigger.PlayerKill }
        .sumOf { it.points }

    fun pointsForProjectile(projectileType: org.bukkit.entity.EntityType): Int = definition.scoringRules
        .filter { rule ->
            val trigger = rule.trigger
            trigger is ScoreTrigger.ProjectileHit && projectileType in trigger.projectileTypes
        }
        .sumOf { it.points }

    override val nextPhase: GamePhase
        get() {
            val stages = definition.phases.stages
            if (stages.isEmpty()) return GenericFinishedPhase()

            phaseIndex = (phaseIndex + 1).coerceAtMost(stages.lastIndex)
            val stage = stages[phaseIndex]

            return when (stage) {
                is PhaseStage.Custom -> stage.factory.create(this)
                is PhaseStage.Generic -> when (stage.kind) {
                    GenericPhaseKind.LOBBY -> GenericLobbyPhase()
                    GenericPhaseKind.ACTIVE -> GenericActivePhase()
                    GenericPhaseKind.FINISHED -> GenericFinishedPhase()
                }
            }
        }

    override suspend fun join(entity: Entity): JoinResult {
        if (_publicationState == PublicationState.DRAFT) {
            return JoinResult.Failure(JoinFailureReason.GAME_NOT_PUBLISHED, "Game has not been published yet")
        }

        val result = super.join(entity)

        if (result is JoinResult.Success && definition.teams.enabled) {
            assignTeam(entity)
        }

        return result
    }

    override fun leave(entity: Entity) {
        removeFromTeam(entity)
        super.leave(entity)
    }

    override fun dispose() {
        teamsByKey.values.forEach { scoreboard.getTeam(it.name)?.unregister() }
        super.dispose()
    }

    private fun assignTeam(entity: Entity) {
        val teams = resolveTeams()
        if (teams.isEmpty()) return

        val nextTeam = teams.minByOrNull { (_, team) -> team.entries.size }?.value ?: return
        nextTeam.addEntity(entity)
        teamByEntity[entity.uniqueId] = nextTeam.name
    }

    private fun removeFromTeam(entity: Entity) {
        val teamName = teamByEntity.remove(entity.uniqueId) ?: return
        teamsByKey[teamName]?.removeEntity(entity)
    }

    private fun resolveTeams(): Map<String, Team> {
        if (teamsByKey.isNotEmpty()) return teamsByKey

        val definitions = when {
            definition.teams.mode == TeamMode.MANUAL && definition.teams.customTeams.isNotEmpty() -> definition.teams.customTeams
            else -> definition.teams.autoPreset.teams
        }

        definitions.forEachIndexed { index, def ->
            val key = "${def.key}_${index}".lowercase(Locale.getDefault())
            val team = scoreboard.registerNewTeam(key).apply {
                displayName(net.kyori.adventure.text.Component.text(def.name, def.color))
                color(def.color)
            }
            teamsByKey[key] = team
        }

        return teamsByKey
    }
}
