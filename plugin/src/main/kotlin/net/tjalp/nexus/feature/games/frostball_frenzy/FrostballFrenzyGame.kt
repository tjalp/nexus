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
import net.tjalp.nexus.feature.games.Game
import net.tjalp.nexus.feature.games.GamePhase
import net.tjalp.nexus.feature.games.GameSettings
import net.tjalp.nexus.feature.games.GameType

class FrostballFrenzyGame : Game(type = GameType.FROSTBALL_FRENZY) {

    override val settings: Settings = Settings()

    override val nextPhase: GamePhase
        get() = if (currentPhase == null || currentPhase is FrostballFrenzyFightPhase) {
            FrostballFrenzyWaitingPhase(this)
        } else {
            FrostballFrenzyFightPhase(this)
        }

    inner class Settings : GameSettings {
        @Suppress("UnstableApiUsage")
        private val dialogAction: DialogAction
            get() = DialogAction.customClick({ view, audience ->
                minPlayers = view.getFloat("minPlayers")!!.toInt()
                maxPlayers = view.getFloat("maxPlayers")!!.toInt()

                audience.sendActionBar(
                    text()
                        .color(PRIMARY_COLOR)
                        .append(type.formattedName.invoke(audience.get(Identity.LOCALE).get()))
                        .append(text(" settings have been updated"))
                )
            }, ClickCallback.Options.builder().build())

        override var maxPlayers: Int = 16
        override var minPlayers: Int = 2

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