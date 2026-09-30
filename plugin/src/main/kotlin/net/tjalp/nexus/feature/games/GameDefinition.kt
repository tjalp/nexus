package net.tjalp.nexus.feature.games

/**
 * A team that can participate in a game. Teams are deliberately data-only so
 * game implementations can add their own assignment and presentation rules.
 */
data class GameTeam(
    val key: String,
    val name: String = key,
    val minPlayers: Int = 0,
    val maxPlayers: Int = Int.MAX_VALUE
)

enum class TeamAssignmentMode {
    MANUAL,
    AUTOMATIC
}

enum class ScoringTrigger {
    PLAYER_KILL,
    PROJECTILE_HIT,
    MANUAL
}

/** A named scoring event supported by a game definition. */
data class GameScoringRule(
    val key: String,
    val name: String = key,
    val points: Int = 1,
    val trigger: ScoringTrigger = ScoringTrigger.MANUAL
)

/** A phase entry in a game definition's recommended lifecycle. */
data class GamePhaseDefinition(
    val key: String,
    val implementationKey: String,
    val order: Int
)

/**
 * Persisted configuration for a game. The implementation is selected through
 * [templateKey], allowing one implementation to be configured into multiple
 * game definitions without changing command or lifecycle code.
 */
data class GameDefinition(
    val key: String,
    val templateKey: String,
    val name: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val pointsToWin: Int = 1,
    val teamAssignmentMode: TeamAssignmentMode = TeamAssignmentMode.MANUAL,
    val teams: List<GameTeam> = emptyList(),
    val scoring: List<GameScoringRule> = emptyList(),
    val phases: List<GamePhaseDefinition> = emptyList()
) {
    init {
        require(key.isNotBlank()) { "Game definition key cannot be blank" }
        require(templateKey.isNotBlank()) { "Game definition template key cannot be blank" }
        require(minPlayers >= 0) { "Minimum player count cannot be negative" }
        require(maxPlayers >= minPlayers) { "Maximum player count cannot be less than minimum player count" }
        require(pointsToWin > 0) { "Points to win must be positive" }
        require(teams.map { it.key.lowercase() }.distinct().size == teams.size) { "Team keys must be unique" }
    }

    fun team(key: String): GameTeam? = teams.firstOrNull { it.key.equals(key, ignoreCase = true) }
    fun scoringRule(key: String): GameScoringRule? =
        scoring.firstOrNull { it.key.equals(key, ignoreCase = true) }
}

/**
 * A registered implementation template. Templates are code-owned while
 * [GameDefinition] values can be stored and changed independently.
 */
data class GameTemplate(
    val key: String,
    val name: String,
    val implementationKey: String,
    val defaultDefinition: GameDefinition
)
