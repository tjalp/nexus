package net.tjalp.nexus.feature.notices

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
import net.kyori.adventure.text.format.NamedTextColor.GRAY
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextDecoration.BOLD
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.COMPLEMENTARY_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.Constants.PUNISHMENTS_PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.config.RulesConfig
import net.tjalp.nexus.util.translate
import java.util.*

@Suppress("UnstableApiUsage")
object RulesDialog {

    private val mm = MiniMessage.miniMessage()

    val KEY = DialogKeys.create(key("nexus", "rules"))

    fun create(locale: Locale, callback: (Boolean) -> Unit = {}): Dialog = Dialog.create { builder ->
        builder.empty()
            .base(base(locale = locale))
            .type(type(callback))
    }

    fun base(
        config: RulesConfig = NexusPlugin.configuration.features.notices.rules,
        locale: Locale? = null
    ): DialogBase {
        val bodies = mutableListOf<DialogBody>()

        bodies += DialogBody.plainMessage(
            translatable(
                "dialog.rules.description",
                COMPLEMENTARY_COLOR,
                Argument.component("command", text("/rules", PRIMARY_COLOR))
            ).translate(locale),
            250
        )

        config.rules.forEach { rule ->
            val component = text().color(GRAY)
                .append(
                    mm.deserialize(rule.title).colorIfAbsent(PUNISHMENTS_PRIMARY_COLOR).decorationIfAbsent(
                        BOLD,
                        TextDecoration.State.TRUE
                    )
                )
                .appendNewline()
                .append(mm.deserialize(rule.description))
                .build()

            bodies += DialogBody.plainMessage(component, 250)
        }

        return DialogBase.builder(translatable("dialog.rules.title").translate(locale))
            .canCloseWithEscape(false)
            .body(bodies)
            .build()
    }

    fun type(callback: (Boolean) -> Unit = {}): DialogType {
        return DialogType.confirmation(
            ActionButton.builder(translatable("gui.acknowledge"))
                .action(DialogAction.customClick({ _, _ ->
                    callback(true)
                }, ClickCallback.Options.builder().build()))
                .build(),
            ActionButton.builder(translatable("menu.disconnect"))
                .action(DialogAction.customClick({ _, _ ->
                    callback(false)
                }, ClickCallback.Options.builder().build()))
                .build()
        )
    }
}