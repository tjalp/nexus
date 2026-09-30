package net.tjalp.nexus.feature.games

import net.tjalp.nexus.game.GameDefinitionsTable
import net.tjalp.nexus.game.GamePhasesTable
import net.tjalp.nexus.game.GameScoringRulesTable
import net.tjalp.nexus.game.GameTeamsTable
import net.tjalp.nexus.game.GameTemplatesTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Loads game definitions from the database and ensures the built-in templates
 * exist. Database rows are intentionally plain values; runtime implementations
 * remain in [GameFactoryRegistry].
 */
class GameDefinitionsRepository(private val database: Database) {

    fun load(): List<GameDefinition> = transaction(database) {
        val templates = GameTemplatesTable.selectAll()
            .associate { it[GameTemplatesTable.id].value to it[GameTemplatesTable.key] }

        GameDefinitionsTable.selectAll().map { definition ->
            val definitionId = definition[GameDefinitionsTable.id].value
            val teams = GameTeamsTable.selectAll()
                .where { GameTeamsTable.definitionId eq definition[GameDefinitionsTable.id] }
                .map {
                    GameTeam(
                        key = it[GameTeamsTable.key],
                        name = it[GameTeamsTable.name],
                        minPlayers = it[GameTeamsTable.minPlayers],
                        maxPlayers = it[GameTeamsTable.maxPlayers]
                    )
                }
            val scoring = GameScoringRulesTable.selectAll()
                .where { GameScoringRulesTable.definitionId eq definition[GameDefinitionsTable.id] }
                .map {
                    GameScoringRule(
                        key = it[GameScoringRulesTable.key],
                        name = it[GameScoringRulesTable.name],
                        points = it[GameScoringRulesTable.points],
                        trigger = ScoringTrigger.valueOf(it[GameScoringRulesTable.trigger])
                    )
                }
            val phases = GamePhasesTable.selectAll()
                .where { GamePhasesTable.definitionId eq definition[GameDefinitionsTable.id] }
                .orderBy(GamePhasesTable.orderIndex to SortOrder.ASC)
                .map {
                    GamePhaseDefinition(
                        key = it[GamePhasesTable.key],
                        implementationKey = it[GamePhasesTable.implementationKey],
                        order = it[GamePhasesTable.orderIndex]
                    )
                }

            GameDefinition(
                key = definition[GameDefinitionsTable.key],
                templateKey = templates[definition[GameDefinitionsTable.templateId].value]
                    ?: error("Game definition '$definitionId' references a missing template"),
                name = definition[GameDefinitionsTable.name],
                minPlayers = definition[GameDefinitionsTable.minPlayers],
                maxPlayers = definition[GameDefinitionsTable.maxPlayers],
                pointsToWin = definition[GameDefinitionsTable.pointsToWin],
                teamAssignmentMode = TeamAssignmentMode.valueOf(definition[GameDefinitionsTable.teamAssignmentMode]),
                teams = teams,
                scoring = scoring,
                phases = phases
            )
        }
    }

    fun ensureSeeds() {
        transaction(database) {
            GameTemplateSeeds.all.forEach { seed ->
                val templateId = GameTemplatesTable
                    .selectAll()
                    .where { GameTemplatesTable.key eq seed.template.key }
                    .singleOrNull()
                    ?.get(GameTemplatesTable.id)
                    ?: GameTemplatesTable.insert {
                        it[key] = seed.template.key
                        it[name] = seed.template.name
                        it[implementationKey] = seed.template.implementationKey
                    }[GameTemplatesTable.id]

                val definitionId = GameDefinitionsTable
                    .selectAll()
                    .where { GameDefinitionsTable.key eq seed.definition.key }
                    .singleOrNull()
                    ?.get(GameDefinitionsTable.id)
                    ?: GameDefinitionsTable.insert {
                        it[key] = seed.definition.key
                        it[GameDefinitionsTable.templateId] = templateId
                        it[name] = seed.definition.name
                        it[minPlayers] = seed.definition.minPlayers
                        it[maxPlayers] = seed.definition.maxPlayers
                        it[pointsToWin] = seed.definition.pointsToWin
                        it[teamAssignmentMode] = seed.definition.teamAssignmentMode.name
                    }[GameDefinitionsTable.id]

                seed.definition.teams.forEach { team ->
                    if (GameTeamsTable.selectAll().where {
                            (GameTeamsTable.definitionId eq definitionId) and
                                (GameTeamsTable.key eq team.key)
                        }.empty()) {
                        GameTeamsTable.insert {
                            it[GameTeamsTable.definitionId] = definitionId
                            it[key] = team.key
                            it[name] = team.name
                            it[minPlayers] = team.minPlayers
                            it[maxPlayers] = team.maxPlayers
                        }
                    }

                }
                seed.definition.scoring.forEach { rule ->
                    if (GameScoringRulesTable.selectAll().where {
                            (GameScoringRulesTable.definitionId eq definitionId) and
                                (GameScoringRulesTable.key eq rule.key)
                        }.empty()) {
                        GameScoringRulesTable.insert {
                            it[GameScoringRulesTable.definitionId] = definitionId
                            it[key] = rule.key
                            it[name] = rule.name
                            it[points] = rule.points
                            it[trigger] = rule.trigger.name
                        }
                    }
                }
                seed.definition.phases.forEach { phase ->
                    if (GamePhasesTable.selectAll().where {
                            (GamePhasesTable.definitionId eq definitionId) and
                                (GamePhasesTable.key eq phase.key)
                        }.empty()) {
                        GamePhasesTable.insert {
                            it[GamePhasesTable.definitionId] = definitionId
                            it[key] = phase.key
                            it[implementationKey] = phase.implementationKey
                            it[orderIndex] = phase.order
                        }
                    }
                }
            }
        }
    }

    /** Persists a definition and its child team, scoring, and phase values. */
    fun save(definition: GameDefinition) {
        transaction(database) {
            val templateId = GameTemplatesTable.selectAll()
                .where { GameTemplatesTable.key eq definition.templateKey }
                .singleOrNull()
                ?.get(GameTemplatesTable.id)
                ?: error("Cannot save '${definition.key}': template '${definition.templateKey}' is missing")

            val existing = GameDefinitionsTable.selectAll()
                .where { GameDefinitionsTable.key eq definition.key }
                .singleOrNull()
                ?.get(GameDefinitionsTable.id)
            val definitionId = existing ?: GameDefinitionsTable.insert {
                it[key] = definition.key
                it[GameDefinitionsTable.templateId] = templateId
                it[name] = definition.name
                it[minPlayers] = definition.minPlayers
                it[maxPlayers] = definition.maxPlayers
                it[pointsToWin] = definition.pointsToWin
                it[teamAssignmentMode] = definition.teamAssignmentMode.name
            }[GameDefinitionsTable.id]

            if (existing != null) {
                GameDefinitionsTable.update({ GameDefinitionsTable.id eq existing }) {
                    it[GameDefinitionsTable.templateId] = templateId
                    it[name] = definition.name
                    it[minPlayers] = definition.minPlayers
                    it[maxPlayers] = definition.maxPlayers
                }
                GameTeamsTable.deleteWhere { GameTeamsTable.definitionId eq existing }
                GameScoringRulesTable.deleteWhere { GameScoringRulesTable.definitionId eq existing }
                GamePhasesTable.deleteWhere { GamePhasesTable.definitionId eq existing }
            }

            definition.teams.forEach { team ->
                GameTeamsTable.insert {
                    it[GameTeamsTable.definitionId] = definitionId
                    it[key] = team.key
                    it[name] = team.name
                    it[minPlayers] = team.minPlayers
                    it[maxPlayers] = team.maxPlayers
                }
            }
            definition.scoring.forEach { rule ->
                GameScoringRulesTable.insert {
                    it[GameScoringRulesTable.definitionId] = definitionId
                    it[key] = rule.key
                    it[name] = rule.name
                    it[points] = rule.points
                    it[trigger] = rule.trigger.name
                }
            }
            definition.phases.forEach { phase ->
                GamePhasesTable.insert {
                    it[GamePhasesTable.definitionId] = definitionId
                    it[key] = phase.key
                    it[implementationKey] = phase.implementationKey
                    it[orderIndex] = phase.order
                }
            }
        }
    }

    fun loadOrSeed(): List<GameDefinition> {
        ensureSeeds()
        return load()
    }
}

internal object GameTemplateSeeds {
    val all: List<Seed> = listOf(
        Seed(
            template = GameTemplate(
                key = "frostball_frenzy",
                name = "Frostball Frenzy",
                implementationKey = "frostball_frenzy",
                defaultDefinition = GameDefinition(
                    key = "frostball_frenzy",
                    templateKey = "frostball_frenzy",
                    name = "Frostball Frenzy",
                    minPlayers = 2,
                    maxPlayers = 16,
                    pointsToWin = 10,
                    teamAssignmentMode = TeamAssignmentMode.AUTOMATIC
                )
            ),
            definition = GameDefinition(
                key = "frostball_frenzy",
                templateKey = "frostball_frenzy",
                name = "Frostball Frenzy",
                minPlayers = 2,
                maxPlayers = 16,
                pointsToWin = 10,
                teamAssignmentMode = TeamAssignmentMode.AUTOMATIC,
                teams = listOf(
                    GameTeam("red", "Red"),
                    GameTeam("blue", "Blue")
                ),
                scoring = listOf(
                    GameScoringRule("snowball_hit", "Snowball hit", 1, ScoringTrigger.PROJECTILE_HIT),
                    GameScoringRule("manual", "Manual adjustment", 1, ScoringTrigger.MANUAL)
                ),
                phases = listOf(
                    GamePhaseDefinition("waiting", "frostball_waiting", 0),
                    GamePhaseDefinition("fight", "frostball_fight", 1)
                )
            )
        )
    )

    data class Seed(val template: GameTemplate, val definition: GameDefinition)
}
