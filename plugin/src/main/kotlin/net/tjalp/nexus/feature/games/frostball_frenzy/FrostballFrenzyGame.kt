package net.tjalp.nexus.feature.games.frostball_frenzy

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.event.ClickCallback
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.feature.games.*

class FrostballFrenzyGame(
    feature: GamesFeature,
    definition: GameDefinition
) : Game(feature = feature, definition = definition) {

    /** Compatibility constructor for callers that used the original game API. */
    constructor(feature: GamesFeature) : this(
        feature,
        GameTemplateSeeds.all.first { it.template.key == GameType.FROSTBALL_FRENZY.definitionKey }.definition
    )

    override val settings: Settings = Settings()

    override val nextPhase: GamePhase
        get() = if (currentPhase == null || currentPhase is FrostballFrenzyFightPhase) {
            phase("waiting", "frostball_waiting")
        } else {
            phase("fight", "frostball_fight")
        }

    private fun phase(key: String, fallbackImplementationKey: String): GamePhase {
        val configuredKey = definition.phases
            .firstOrNull { it.key == key }
            ?.implementationKey
            ?: fallbackImplementationKey
        return GamePhaseRegistry.createOrNull(this, configuredKey) ?: when (configuredKey) {
            "frostball_waiting" -> FrostballFrenzyWaitingPhase(this)
            "frostball_fight" -> FrostballFrenzyFightPhase(this)
            else -> error("No game phase factory is registered for '$configuredKey'")
        }
    }

    inner class Settings : GameSettings {
        @Suppress("UnstableApiUsage")
        private val dialogAction: DialogAction
            get() = DialogAction.customClick({ view, audience ->
                val requestedMin = view.getFloat("minPlayers")!!.toInt()
                val requestedMax = view.getFloat("maxPlayers")!!.toInt()
                minPlayers = minOf(requestedMin, requestedMax)
                maxPlayers = maxOf(requestedMin, requestedMax)
                this@FrostballFrenzyGame.updateDefinition(
                    definition.copy(minPlayers = minPlayers, maxPlayers = maxPlayers)
                )

                audience.sendActionBar(
                    text()
                        .color(PRIMARY_COLOR)
                        .append(type.formattedName.invoke(audience.get(Identity.LOCALE).get()))
                        .append(text(" settings have been updated"))
                )
            }, ClickCallback.Options.builder().build())

        override var maxPlayers: Int = definition.maxPlayers
        override var minPlayers: Int = definition.minPlayers

        @Suppress("UnstableApiUsage")
        override fun dialog() = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(text("Snowball Fight Settings"))
                        .inputs(
                            listOf(
                                DialogInput.numberRange("minPlayers", text("Minimum Player Count"), 1f, 10f)
                                    .step(1f)
                                    .initial(minPlayers.toFloat().coerceIn(1f, 10f))
                                    .build(),
                                DialogInput.numberRange("maxPlayers", text("Maximum Player Count"), 1f, 100f)
                                    .step(1f)
                                    .initial(maxPlayers.toFloat().coerceIn(1f, 100f))
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
}