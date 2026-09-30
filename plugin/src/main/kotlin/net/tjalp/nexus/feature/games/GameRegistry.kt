package net.tjalp.nexus.feature.games

/**
 * Registry for code-owned game templates.
 *
 * Templates describe the implementation and provide a safe default definition
 * used when a database has not been initialized yet.
 */
object GameTemplateRegistry {
    private val templates = linkedMapOf<String, GameTemplate>()

    val all: List<GameTemplate>
        get() = templates.values.toList()

    fun register(template: GameTemplate) {
        require(template.key.isNotBlank()) { "Game template key cannot be blank" }
        templates[template.key] = template
    }

    operator fun get(key: String): GameTemplate? = templates[key]

    fun require(key: String): GameTemplate =
        get(key) ?: error("No game template is registered for '$key'")
}

/**
 * Registry mapping a template implementation to its runtime [Game] class.
 */
object GameFactoryRegistry {
    private val factories = linkedMapOf<String, (GamesFeature, GameDefinition) -> Game>()

    fun register(
        implementationKey: String,
        factory: (GamesFeature, GameDefinition) -> Game
    ) {
        require(implementationKey.isNotBlank()) { "Game implementation key cannot be blank" }
        factories[implementationKey] = factory
    }

    operator fun get(implementationKey: String): ((GamesFeature, GameDefinition) -> Game)? =
        factories[implementationKey]

    fun create(feature: GamesFeature, definition: GameDefinition): Game {
        val template = GameTemplateRegistry.require(definition.templateKey)
        return factories[template.implementationKey]?.invoke(feature, definition)
            ?: error("No game factory is registered for '${template.implementationKey}'")
    }
}

/** Registry for phase implementations referenced by [GamePhaseDefinition]. */
object GamePhaseRegistry {
    private val factories = linkedMapOf<String, (Game) -> GamePhase>()

    fun register(implementationKey: String, factory: (Game) -> GamePhase) {
        require(implementationKey.isNotBlank()) { "Game phase implementation key cannot be blank" }
        factories[implementationKey] = factory
    }

    fun create(game: Game, implementationKey: String): GamePhase =
        factories[implementationKey]?.invoke(game)
            ?: error("No game phase factory is registered for '$implementationKey'")

    fun createOrNull(game: Game, implementationKey: String): GamePhase? =
        factories[implementationKey]?.invoke(game)
}
