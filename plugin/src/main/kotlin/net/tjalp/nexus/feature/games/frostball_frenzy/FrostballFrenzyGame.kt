package net.tjalp.nexus.feature.games.frostball_frenzy

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import kotlinx.coroutines.launch
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.event.ClickCallback
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.feature.games.GameSettings
import net.tjalp.nexus.feature.games.GameType
import net.tjalp.nexus.feature.games.GamesFeature
import net.tjalp.nexus.feature.games.minigame.*
import org.bukkit.entity.EntityType
import java.util.*

class FrostballFrenzyGame(feature: GamesFeature, host: UUID? = null) : MinigameGame(
    feature = feature,
    type = GameType.FROSTBALL_FRENZY,
    definition = TEMPLATE.factory(),
    host = host
) {

    override val settings: Settings = Settings()

    override fun onPointsTargetReached(entity: org.bukkit.entity.Entity) {
        scheduler.launch {
            end()
        }
    }

    inner class Settings : GameSettings {
        @Suppress("UnstableApiUsage")
        private val dialogAction: DialogAction
            get() = DialogAction.customClick({ view, audience ->
                minPlayers = view.getFloat("minPlayers")!!.toInt()
                maxPlayers = view.getFloat("maxPlayers")!!.toInt()
                pointsToWin = view.getFloat("pointsToWin")!!.toInt()

                audience.sendActionBar(
                    text()
                        .color(PRIMARY_COLOR)
                        .append(type.formattedName.invoke(audience.get(Identity.LOCALE).get()))
                        .append(text(" settings have been updated"))
                )
            }, ClickCallback.Options.builder().build())

        override var maxPlayers: Int = 16
        override var minPlayers: Int = 2

        var pointsToWin: Int
            get() = definition.pointsToWin
            set(value) {
                definition.pointsToWin = value.coerceAtLeast(1)
            }

        var teamsEnabled: Boolean
            get() = definition.teams.enabled
            set(value) {
                definition.teams.enabled = value
            }

        @Suppress("UnstableApiUsage")
        override fun dialog() = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(text("Frostball Frenzy Settings"))
                        .inputs(
                            listOf(
                                DialogInput.numberRange("minPlayers", text("Minimum Player Count"), 1f, 32f)
                                    .step(1f)
                                    .initial(minPlayers.toFloat().coerceIn(1f, 32f))
                                    .build(),
                                DialogInput.numberRange("maxPlayers", text("Maximum Player Count"), 1f, 100f)
                                    .step(1f)
                                    .initial(maxPlayers.toFloat().coerceIn(1f, 100f))
                                    .build(),
                                DialogInput.numberRange("pointsToWin", text("Points To Win"), 1f, 250f)
                                    .step(1f)
                                    .initial(pointsToWin.toFloat().coerceIn(1f, 250f))
                                    .build()
                            )
                        )
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.builder(translatable("gui.cancel")).build(),
                        ActionButton.builder(translatable("gui.done"))
                            .action(dialogAction)
                            .build()
                    )
                )
        }
    }

    companion object {
        val TEMPLATE = MinigameTemplate(
            id = "frostball_frenzy",
            type = GameType.FROSTBALL_FRENZY
        ) {
            MinigameDefinition(
                pointsToWin = 30,
                scoringRules = mutableListOf(
                    ScoringRule(ScoreTrigger.ProjectileHit(setOf(EntityType.SNOWBALL)), 1),
                    ScoringRule(ScoreTrigger.PlayerKill, 2)
                ),
                teams = TeamSettings(
                    enabled = true,
                    mode = TeamMode.AUTO,
                    autoPreset = AutoTeamPreset.DEFAULT,
                    customTeams = mutableListOf()
                ),
                phases = PhasePipeline(
                    mutableListOf(
                        PhaseStage.Custom("waiting") { game -> FrostballFrenzyWaitingPhase(game) },
                        PhaseStage.Custom("fight") { game -> FrostballFrenzyFightPhase(game) }
                    )
                ),
                modifiers = mutableSetOf(FrostballKnockbackModifier.id)
            )
        }

        fun registerTemplate() {
            MinigameTemplates.register(TEMPLATE)
            MinigameModifierRegistry.register(FrostballKnockbackModifier)
        }
    }
}
