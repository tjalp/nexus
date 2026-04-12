package net.tjalp.nexus.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
import net.tjalp.nexus.Constants.PRIMARY_COLOR

/**
 * Component helpers for reuse.
 */
object ComponentTemplates {

    /**
     * Format a component as an event.
     *
     * @param component The component to format
     * @return Formatted component
     */
    fun eventMessage(component: ComponentLike): Component {
        return text()
            .colorIfAbsent(PRIMARY_COLOR)
            .append(text("▶ ", DARK_GRAY))
            .append(component)
            .build()
    }
}

/**
 * Format this component as an event.
 *
 * @see ComponentTemplates.eventMessage
 * @return Formatted component
 */
fun ComponentLike.asEventMessage(): Component = ComponentTemplates.eventMessage(this)