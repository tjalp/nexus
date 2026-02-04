@file:Suppress("UnstableApiUsage")

package net.tjalp.nexus.util

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.empty
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.translation.GlobalTranslator
import java.util.*

/**
 * Converts this [ComponentLike] to a simple dialog notice.
 *
 * @param title The title of the dialog
 * @param translate Whether to translate the component using the [GlobalTranslator]
 * @param locale The [Locale] to translate to, if [translate] is true
 * @return A [Dialog] representing this component as a notice
 * @throws IllegalStateException if [translate] is true but no [Locale] is provided
 */
fun ComponentLike.asDialogNotice(
    title: ComponentLike = empty(),
    translate: Boolean = true,
    locale: Locale? = null
): Dialog {
    if (translate) require(locale != null) { "Locale must be provided when translate is true" }

    val component = if (translate) this.translate(locale!!) else this.asComponent()

    return Dialog.create { builder ->
        builder.empty()
            .base(
                DialogBase.builder(title.asComponent())
                    .body(
                        listOf(
                            DialogBody.plainMessage(component)
                        )
                    )
                    .build()
            )
            .type(DialogType.notice())
    }
}

/**
 * Translates this [ComponentLike] to the specified [Locale] using the [GlobalTranslator].
 *
 * @param locale The [Locale] to translate to, or null to not translate
 * @return The translated [Component]
 */
fun ComponentLike.translate(locale: Locale?): Component {
    return locale?.let { GlobalTranslator.render(this.asComponent(), it) } ?: this.asComponent()
}