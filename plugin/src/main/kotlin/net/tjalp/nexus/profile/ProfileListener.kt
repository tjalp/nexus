package net.tjalp.nexus.profile

import com.destroystokyo.paper.ClientOption
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.minimessage.translation.Argument
import net.kyori.adventure.translation.Translator
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.GeneralAttachment
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.util.profile
import net.tjalp.nexus.util.translate
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.LOW
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLocaleChangeEvent
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class ProfileListener(private val profiles: ProfilesService) : Listener {

    @OptIn(ExperimentalTime::class)
    @Suppress("UnstableApiUsage")
    @EventHandler(priority = LOW)
    fun on(event: AsyncPlayerConnectionConfigureEvent) {
        val conn = event.connection
        val audience = conn.audience
        val uniqueId =
            audience.get(Identity.UUID).getOrNull() ?: conn.profile.id ?: error("No profile ID in connection")
        val username = audience.get(Identity.NAME).getOrNull() ?: conn.profile.name
        val locale = audience.get(Identity.LOCALE).getOrNull()
            ?: Translator.parseLocale(conn.getClientOption(ClientOption.LOCALE))

        runBlocking {
            val profile: ProfileSnapshot = try {
                profiles.get(uniqueId, cache = true, allowCreation = true) ?: error("Failed to load or create profile")
            } catch (e: Throwable) {
                e.printStackTrace()
                event.connection.disconnect(
                    text(
                        "An error occurred while loading your profile. Please try again later.",
                        RED
                    )
                )
                return@runBlocking
            }

            launch {
                waitForTimeZone(audience, profile)
            }

            profile.update<GeneralAttachment> { att ->
                att.lastKnownName = username
                locale?.let { att.preferredLocale = it }
            }
        }
    }

    @Suppress("UnstableApiUsage")
    private suspend fun waitForTimeZone(audience: Audience, profile: ProfileSnapshot) {
        if (profile.attachmentOf<GeneralAttachment>()?.timeZone != null) return

        val deferred = CompletableDeferred<TimeZone?>()
        val locale = audience.get(Identity.LOCALE).getOrNull()
            ?: profile.attachmentOf<GeneralAttachment>()?.preferredLocale

//        audience.showDialog(Dialog.create { builder ->
//            builder.empty()
//                .base(
//                    DialogBase.builder(translatable("dialog.time_zone.title").translate(locale))
//                        .body(
//                            listOf(
//                                DialogBody.plainMessage(
//                                    translatable(
//                                        "dialog.time_zone.description", Argument.component(
//                                            "default_zone",
//                                            text(TimeZone.currentSystemDefault().id, MONOCHROME_COLOR)
//                                        )
//                                    ).color(PRIMARY_COLOR).translate(locale)
//                                )
//                            )
//                        )
//                        .inputs(
//                            listOf(
//                                DialogInput.text(
//                                    "time_zone_input",
//                                    translatable("dialog.time_zone.input_label", PRIMARY_COLOR).translate(locale)
//                                )
//                                    .build()
//                            )
//                        )
//                        .build()
//                )
//                .type(
//                    DialogType.notice(
//                        ActionButton.builder(translatable("gui.done"))
//                            .action(DialogAction.customClick({ view, _ ->
//                                val text = view.getText("time_zone_input")
//                                val timeZone = if (!text.isNullOrBlank()) TimeZone.of(text) else null
//
//                                deferred.complete(timeZone)
//                            }, ClickCallback.Options.builder().build()))
//                            .build()
//                    )
//                )
//        })

        val commonZoneButtons = listOf(
            "Europe/Amsterdam"
        ).sorted().map { zoneId ->
            ActionButton.builder(text(zoneId))
                .action(DialogAction.customClick({ _, _ ->
                    deferred.complete(TimeZone.of(zoneId))
                }, ClickCallback.Options.builder().build()))
                .build()
        }

        val zoneButtons = TimeZone.availableZoneIds.sorted().map { zoneId ->
            ActionButton.builder(text(zoneId))
                .action(DialogAction.customClick({ _, _ ->
                    deferred.complete(TimeZone.of(zoneId))
                }, ClickCallback.Options.builder().build()))
                .build()
        }

        audience.showDialog(Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(translatable("dialog.time_zone.title").translate(locale))
                        .canCloseWithEscape(false)
                        .body(
                            listOf(
                                DialogBody.plainMessage(
                                    translatable(
                                        "dialog.time_zone.description", Argument.component(
                                            "default_zone",
                                            text(TimeZone.currentSystemDefault().id, MONOCHROME_COLOR)
                                        )
                                    ).color(PRIMARY_COLOR).translate(locale),
                                    300
                                )
                            )
                        )
                        .build()
                )
                .type(
                    DialogType.multiAction(commonZoneButtons + zoneButtons).columns(2).build()
                )
        })

        val zone = withTimeoutOrNull(5.minutes) {
            deferred.await()
        }

        audience.closeDialog()

        profile.update<GeneralAttachment> { att ->
            att.timeZone = zone
        }
    }

    @EventHandler
    fun on(event: PlayerConnectionCloseEvent) {
        profiles.uncache(event.playerUniqueId)
    }

    @EventHandler
    fun on(event: PlayerLocaleChangeEvent) {
        val profile = event.player.profile()
        val locale = event.locale()

        NexusPlugin.scheduler.launch {
            profile.update<GeneralAttachment> {
                it.preferredLocale = locale
            }
        }
    }
}