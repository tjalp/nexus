package net.tjalp.nexus.feature.games

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text

enum class GameType(
    val id: String,
    val displayName: Component,
    val worldSpec: GameWorldSpec,
    private val settingsFactory: () -> GameSettings,
    private val requirementsFactory: () -> List<GameJoinRequirement>,
    private val phasesFactory: () -> List<GamePhase>
) {
    SANDBOX(
        id = "sandbox",
        displayName = text("Sandbox"),
        worldSpec = GameWorldSpec.mainWorld(),
        settingsFactory = {
            GameSettings.of(
                GameSetting(
                    key = GameSettingKeys.MIN_PLAYERS,
                    displayName = text("Minimum players"),
                    value = 2,
                    isMutable = true
                ),
                GameSetting(
                    key = GameSettingKeys.MAX_PLAYERS,
                    displayName = text("Maximum players"),
                    value = 16,
                    isMutable = true
                ),
                GameSetting(
                    key = GameSettingKeys.RESTART_VOTE_THRESHOLD,
                    displayName = text("Restart vote threshold"),
                    value = 0.6,
                    isMutable = true
                )
            )
        },
        requirementsFactory = {
            listOf(
                InventoryEmptyRequirement(enabled = false, isMutable = true),
                BoundsRequirement(bounds = null, enabled = false, isMutable = true)
            )
        },
        phasesFactory = {
            listOf(
                GamePhase(
                    id = "waiting",
                    displayName = text("Waiting for players"),
                    allowJoin = true,
                    completionCondition = { game ->
                        game.participants.size >= game.minPlayers
                    }
                ),
                GamePhase(
                    id = "play",
                    displayName = text("Gameplay"),
                    allowJoin = false,
                    durationTicks = 20L * 180
                ),
                GamePhase(
                    id = "results",
                    displayName = text("Results"),
                    allowJoin = true,
                    durationTicks = 20L * 30
                )
            )
        }
    );

    fun createGame(feature: GamesFeature, id: String): Game {
        return Game(
            id = id,
            type = this,
            feature = feature,
            settings = settingsFactory().copyForGame(),
            joinRequirements = requirementsFactory().map { it.copyRequirement() },
            phases = phasesFactory(),
            worldSpec = worldSpec
        )
    }
}
