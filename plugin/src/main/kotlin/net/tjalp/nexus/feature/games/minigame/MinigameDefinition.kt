package net.tjalp.nexus.feature.games.minigame

import net.kyori.adventure.text.format.NamedTextColor
import net.tjalp.nexus.feature.games.GamePhase
import org.bukkit.entity.EntityType

/**
 * Mutable game definition used to configure a minigame instance at runtime.
 */
data class MinigameDefinition(
    var pointsToWin: Int,
    var scoringRules: MutableList<ScoringRule>,
    var teams: TeamSettings,
    var phases: PhasePipeline,
    var modifiers: MutableSet<String> = mutableSetOf()
)

data class ScoringRule(
    val trigger: ScoreTrigger,
    val points: Int = 1
)

sealed interface ScoreTrigger {
    data object PlayerKill : ScoreTrigger
    data class ProjectileHit(val projectileTypes: Set<EntityType>) : ScoreTrigger
}

data class TeamSettings(
    var enabled: Boolean,
    var mode: TeamMode,
    var autoPreset: AutoTeamPreset = AutoTeamPreset.DEFAULT,
    var customTeams: MutableList<GameTeamDefinition> = mutableListOf()
)

enum class TeamMode {
    AUTO,
    MANUAL
}

enum class AutoTeamPreset(val teams: List<GameTeamDefinition>) {
    DEFAULT(
        listOf(
            GameTeamDefinition("red", "Red", NamedTextColor.RED),
            GameTeamDefinition("green", "Green", NamedTextColor.GREEN),
            GameTeamDefinition("blue", "Blue", NamedTextColor.BLUE),
            GameTeamDefinition("yellow", "Yellow", NamedTextColor.YELLOW)
        )
    ),
    EXTENDED(
        listOf(
            GameTeamDefinition("red", "Red", NamedTextColor.RED),
            GameTeamDefinition("green", "Green", NamedTextColor.GREEN),
            GameTeamDefinition("blue", "Blue", NamedTextColor.BLUE),
            GameTeamDefinition("yellow", "Yellow", NamedTextColor.YELLOW),
            GameTeamDefinition("aqua", "Aqua", NamedTextColor.AQUA),
            GameTeamDefinition("purple", "Purple", NamedTextColor.LIGHT_PURPLE)
        )
    )
}

data class GameTeamDefinition(
    val key: String,
    val name: String,
    val color: NamedTextColor
)

data class PhasePipeline(
    val stages: MutableList<PhaseStage>
)

sealed interface PhaseStage {
    data class Generic(val kind: GenericPhaseKind) : PhaseStage
    data class Custom(
        val id: String,
        val factory: MinigamePhaseFactory
    ) : PhaseStage
}

enum class GenericPhaseKind {
    LOBBY,
    ACTIVE,
    FINISHED
}

fun interface MinigamePhaseFactory {
    fun create(game: MinigameGame): GamePhase
}
