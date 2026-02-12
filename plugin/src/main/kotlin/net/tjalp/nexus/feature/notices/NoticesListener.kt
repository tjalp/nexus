package net.tjalp.nexus.feature.notices

import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.GeneralAttachment
import net.tjalp.nexus.profile.attachment.NoticesAttachment
import net.tjalp.nexus.profile.update
import net.tjalp.nexus.util.translate
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import kotlin.jvm.optionals.getOrNull

@Suppress("UnstableApiUsage")
class NoticesListener(val notices: NoticesFeature) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun on(event: AsyncPlayerConnectionConfigureEvent) {
        val conn = event.connection
        val audience = conn.audience
        val profileId = conn.profile.id ?: audience.get(Identity.UUID).getOrNull() ?: return
        val profile = runBlocking { NexusPlugin.profiles.get(profileId) } ?: return
        val att = profile.attachmentOf<NoticesAttachment>() ?: return
        val locale = audience.get(Identity.LOCALE).getOrNull() ?: profile.attachmentOf<GeneralAttachment>()?.preferredLocale

        runBlocking {
            val rulesConfig = NexusPlugin.configuration.features.notices.rules
            val rulesVersion = rulesConfig.rulesVersion

            if (rulesConfig.enable && !att.hasAcceptedRules(rulesVersion)) {
                val accepted = notices.showAndAwaitRules(audience)

                if (!accepted) {
                    audience.closeDialog()
                    event.connection.disconnect(
                        translatable("disconnect.rules_not_accepted", RED)
                            .translate(locale)
                    )
                    return@runBlocking
                }

                launch { profile.update { att.setAcceptedRules(rulesVersion) } }
            }

            val recommendationsConfig = NexusPlugin.configuration.features.notices.recommendations

            if (recommendationsConfig.enable && recommendationsConfig.showOnJoin && !att.hasSeenRecommendations) {
                val seen = notices.showAndAwaitRecommendations(audience)

                if (seen) {
                    launch { profile.update { att.setSeenRecommendations(true) } }
                }
            }
        }
    }
}