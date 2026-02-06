package net.tjalp.nexus.feature.notices

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.AttachmentKeys.GENERAL
import net.tjalp.nexus.profile.attachment.AttachmentKeys.NOTICES
import net.tjalp.nexus.util.translate
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.minutes

@Suppress("UnstableApiUsage")
class NoticesListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun on(event: AsyncPlayerConnectionConfigureEvent) {
        val conn = event.connection
        val audience = conn.audience
        val profileId = conn.profile.id ?: audience.get(Identity.UUID).getOrNull() ?: return
        val profile = runBlocking { NexusPlugin.profiles.get(profileId) } ?: return
        val att = profile.getAttachment(NOTICES) ?: return
        val locale = audience.get(Identity.LOCALE).getOrNull() ?: profile.getAttachment(GENERAL)?.preferredLocale

        if (att.hasAcceptedRules(1)) return

        val acceptedDeferred = CompletableDeferred<Boolean>()

        audience.showDialog(Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(translatable("dialog.rules.title").translate(locale))
                        .canCloseWithEscape(false)
                        .body(
                            listOf(
                                DialogBody.plainMessage(
                                    translatable(
                                        "dialog.rules.description",
                                        PRIMARY_COLOR
                                    ).translate(locale)
                                )
                            )
                        )
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.builder(translatable("gui.acknowledge"))
                            .action(DialogAction.customClick({ _, _ ->
                                acceptedDeferred.complete(true)
                            }, ClickCallback.Options.builder().build()))
                            .build(),
                        ActionButton.builder(translatable("menu.disconnect"))
                            .action(DialogAction.customClick({ _, _ ->
                                acceptedDeferred.complete(false)
                            }, ClickCallback.Options.builder().build()))
                            .build()
                    )
                )
        })

        runBlocking {
            val accepted = withTimeoutOrNull(5.minutes) { acceptedDeferred.await() } ?: false

            if (accepted) {
                profile.update { att.acceptedRulesVersion = 1 }
                return@runBlocking
            }

            audience.closeDialog()
            event.connection.disconnect(
                translatable("disconnect.rules_not_accepted", RED)
                    .translate(locale)
            )
        }
    }
}