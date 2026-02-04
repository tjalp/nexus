package net.tjalp.nexus.feature.punishments

import io.papermc.paper.connection.PlayerConfigurationConnection
import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.connection.PlayerLoginConnection
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.datetime.TimeZone
import net.kyori.adventure.identity.Identity
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.AttachmentKeys.GENERAL
import net.tjalp.nexus.profile.attachment.AttachmentKeys.PUNISHMENT
import net.tjalp.nexus.util.asDialogNotice
import net.tjalp.nexus.util.profile
import net.tjalp.nexus.util.translate
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlin.time.ExperimentalTime

@Suppress("UnstableApiUsage")
@OptIn(ExperimentalTime::class)
class PunishmentListener : Listener {

    @EventHandler
    fun on(event: PlayerConnectionValidateLoginEvent) {
        val conn = event.connection
        val (id, locale) = when (conn) {
            is PlayerConfigurationConnection -> Pair(
                conn.audience.get(Identity.UUID).getOrNull() ?: conn.profile.id,
                conn.audience.get(Identity.LOCALE).getOrNull()
            )
            is PlayerGameConnection -> Pair(conn.player.uniqueId, null)
            is PlayerLoginConnection -> Pair(conn.authenticatedProfile?.id, null)
            else -> Pair(null, null)
        }

        if (id == null) {
            NexusPlugin.logger.warning("Could not determine UUID for connection ${conn::class.java.simpleName}")
            return
        }

        val profile = NexusPlugin.profiles.getCached(id)
        val generalAtt = profile?.getAttachment(GENERAL)
        val playerLocale = locale ?: generalAtt?.preferredLocale ?: Locale.US
        val timeZone = profile?.getAttachment(GENERAL)?.timeZone ?: TimeZone.UTC
        val att = profile?.getAttachment(PUNISHMENT) ?: return

        // get the longest active ban, if any
        val activeBan = att.punishments
            .filter { it.type == PunishmentType.BAN && it.isActive }
            .maxByOrNull { it.expiresAt }

        if (activeBan == null) return

        val banComponent = PunishComponents.ban(activeBan, timeZone, playerLocale)

        event.kickMessage(banComponent.translate(playerLocale))
    }

    @EventHandler
    fun on(event: AsyncChatEvent) {
        val player = event.player
        val profile = player.profile()
        val att = profile.getAttachment(PUNISHMENT) ?: return

        // get the longest active mute, if any
        val activeMute = att.punishments
            .filter { it.type == PunishmentType.MUTE && it.isActive }
            .maxByOrNull { it.expiresAt }

        if (activeMute == null) return

        val timeZone = profile.getAttachment(GENERAL)?.timeZone ?: TimeZone.UTC
        val muteComponent = PunishComponents.mute(activeMute, timeZone, player.locale())

        player.showDialog(muteComponent.asDialogNotice(locale = player.locale()))

        event.isCancelled = true
    }
}