package net.tjalp.nexus.game

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

/**
 * Database representation of a game implementation. The implementation key is
 * resolved by the plugin's game factory registry.
 */
object GameTemplatesTable : IntIdTable("game_templates") {
    val key = varchar("template_key", 64).uniqueIndex()
    val name = varchar("name", 255)
    val implementationKey = varchar("implementation_key", 64)
}

/** A persisted, configurable game definition based on a template. */
object GameDefinitionsTable : IntIdTable("game_definitions") {
    val key = varchar("definition_key", 64).uniqueIndex()
    val templateId = reference("template_id", GameTemplatesTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val minPlayers = integer("min_players")
    val maxPlayers = integer("max_players")
    val pointsToWin = integer("points_to_win")
    val teamAssignmentMode = varchar("team_assignment_mode", 32)
}

object GameTeamsTable : IntIdTable("game_teams") {
    val definitionId = reference("definition_id", GameDefinitionsTable, onDelete = ReferenceOption.CASCADE)
    val key = varchar("team_key", 64)
    val name = varchar("name", 255)
    val minPlayers = integer("min_players").default(0)
    val maxPlayers = integer("max_players").default(Int.MAX_VALUE)

    init {
        uniqueIndex(definitionId, key)
    }
}

object GameScoringRulesTable : IntIdTable("game_scoring_rules") {
    val definitionId = reference("definition_id", GameDefinitionsTable, onDelete = ReferenceOption.CASCADE)
    val key = varchar("scoring_key", 64)
    val name = varchar("name", 255)
    val points = integer("points")
    val trigger = varchar("trigger", 32)

    init {
        uniqueIndex(definitionId, key)
    }
}

object GamePhasesTable : IntIdTable("game_phases") {
    val definitionId = reference("definition_id", GameDefinitionsTable, onDelete = ReferenceOption.CASCADE)
    val key = varchar("phase_key", 64)
    val implementationKey = varchar("implementation_key", 64)
    val orderIndex = integer("order_index")

    init {
        uniqueIndex(definitionId, key)
    }
}
