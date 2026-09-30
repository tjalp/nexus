package net.tjalp.nexus.feature.games.minigame

import net.tjalp.nexus.feature.games.GameType

data class MinigameTemplate(
    val id: String,
    val type: GameType,
    val factory: () -> MinigameDefinition
)

object MinigameTemplates {
    private val templates = mutableMapOf<String, MinigameTemplate>()

    val all: Collection<MinigameTemplate>
        get() = templates.values

    fun register(template: MinigameTemplate) {
        templates[template.id] = template
    }

    fun byId(id: String): MinigameTemplate? = templates[id]

    fun byType(type: GameType): MinigameTemplate? = templates.values.firstOrNull { it.type == type }
}
