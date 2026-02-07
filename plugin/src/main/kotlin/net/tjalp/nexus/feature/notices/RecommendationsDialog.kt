package net.tjalp.nexus.feature.notices

import com.ibm.icu.text.ListFormatter
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.keys.DialogKeys
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor.AQUA
import net.kyori.adventure.text.format.NamedTextColor.GRAY
import net.kyori.adventure.text.format.TextDecoration.BOLD
import net.kyori.adventure.text.format.TextDecoration.UNDERLINED
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.COMPLEMENTARY_COLOR
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.config.RecommendationsConfig
import net.tjalp.nexus.util.translate
import java.util.*

@Suppress("UnstableApiUsage")
object RecommendationsDialog {

    private val mm = MiniMessage.miniMessage()
    private val strictMm = MiniMessage.builder().strict(true).build()

    private val config: RecommendationsConfig
        get() = NexusPlugin.configuration.features.notices.recommendations

    val KEY = DialogKeys.create(key("nexus", "recommendations"))

    /**
     * Creates a dialog that shows the recommendations to the player, and calls the callback with whether they accepted it or not.
     *
     * @param locale The locale of the player to show the dialog to.
     * @param callback The callback to call with whether the player accepted the recommendations or not.
     * @return The dialog to show to the player.
     */
    fun create(locale: Locale, callback: (Boolean) -> Unit = {}): Dialog = Dialog.create { builder ->
        builder.empty()
            .base(base(locale = locale))
            .type(type(callback))
    }

    /**
     * Creates the base of the recommendations dialog, which contains the message and the list of recommended settings and mods.
     *
     * @param config The recommendations config to get the recommended settings and mods from.
     * @param locale The locale of the player to show the dialog to.
     * @return The base of the recommendations dialog.
     */
    fun base(
        config: RecommendationsConfig = NexusPlugin.configuration.features.notices.recommendations,
        locale: Locale? = null
    ): DialogBase {
        val bodies = mutableListOf<DialogBody>()
        val settings = config.settings
        val mods = config.mods

        bodies += DialogBody.plainMessage(
            translatable(
                "dialog.recommendations.message",
                COMPLEMENTARY_COLOR,
                Argument.component("command", text("/recommendations", PRIMARY_COLOR))
            ).translate(locale),
            250
        )

        if (settings.isNotEmpty()) {
            val settingsComponent = text()
                .append(
                    translatable(
                        "dialog.recommendations.settings.title",
                        PRIMARY_COLOR,
                        BOLD
                    )
                )
                .appendNewline()
                .append(translatable("dialog.recommendations.settings.description", GRAY))
                .appendNewline().appendNewline()

            settings.forEachIndexed { index, setting ->
                val hoverComponent = mm.deserialize(setting.description).colorIfAbsent(PRIMARY_COLOR)
                    .appendNewline().appendNewline()
                    .append(mm.deserialize(setting.settingPath))
                val component = text()
                    .color(GRAY)
                    .append(
                        mm.deserialize(setting.name).colorIfAbsent(MONOCHROME_COLOR)
                            .hoverEvent(HoverEvent.showText(hoverComponent))
                    )
                    .append(text(" → "))
                    .append(mm.deserialize(setting.value).colorIfAbsent(MONOCHROME_COLOR))

                if (index != 0) settingsComponent.appendNewline()

                settingsComponent.append(component)
            }

            bodies += DialogBody.plainMessage(settingsComponent.build().translate(locale), 250)
        }

        if (mods.isNotEmpty()) {
            val modsComponent = text()
                .append(
                    translatable(
                        "dialog.recommendations.mods.title",
                        PRIMARY_COLOR,
                        BOLD,
                    )
                )
                .appendNewline()
                .append(
                    translatable(
                        "dialog.recommendations.mods.description", GRAY, Argument.component(
                            "fabric",
                            text(
                                "Fabric", AQUA,
                                UNDERLINED
                            ).clickEvent(ClickEvent.openUrl("https://docs.fabricmc.net/players/installing-fabric/"))
                        )
                    )
                )
                .appendNewline().appendNewline()

            val stringList = mods.map { mod ->
                val component = mm.deserialize(mod.name)
                    .colorIfAbsent(MONOCHROME_COLOR)
                    .hoverEvent(HoverEvent.showText(mm.deserialize(mod.description).colorIfAbsent(PRIMARY_COLOR)))
                    .clickEvent(ClickEvent.openUrl(mod.link))
                    .translate(locale)

                strictMm.serialize(component)
            }
            val formattedList = ListFormatter.getInstance(locale ?: Locale.US).format(stringList)
            val formattedComponent = mm.deserialize(formattedList)
                .colorIfAbsent(GRAY)

            modsComponent.append(formattedComponent)

            bodies += DialogBody.plainMessage(modsComponent.build().translate(locale), 250)
        }

        return DialogBase.builder(translatable("dialog.recommendations.title").translate(locale))
            .body(bodies)
            .build()
    }

    /**
     * Creates the type of the recommendations dialog, which contains the "OK" button that the player can click to accept the recommendations.
     *
     * @param callback The callback to call with whether the player accepted the recommendations or not.
     * @return The type of the recommendations dialog.
     */
    fun type(callback: (Boolean) -> Unit = {}): DialogType {
        return DialogType.notice(
            ActionButton.builder(translatable("gui.ok")).action(DialogAction.customClick({ _, _ ->
                callback(true)
            }, ClickCallback.Options.builder().build())).build()
        )
    }
}