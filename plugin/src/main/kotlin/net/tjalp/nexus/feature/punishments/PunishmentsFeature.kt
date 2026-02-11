package net.tjalp.nexus.feature.punishments

import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.sound
import net.kyori.adventure.text.Component.newline
import net.kyori.adventure.text.Component.textOfChildren
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.PUNISHMENTS
import net.tjalp.nexus.profile.attachment.*
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.profile.player
import net.tjalp.nexus.profile.update
import net.tjalp.nexus.punishment.Punishment
import net.tjalp.nexus.punishment.PunishmentSeverity
import net.tjalp.nexus.punishment.PunishmentType
import net.tjalp.nexus.util.asDialogNotice
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.translate
import net.tjalp.nexus.util.unregister
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class PunishmentsFeature : Feature(PUNISHMENTS) {

    private var listener: PunishmentListener? = null

    override fun onEnable() {
        AttachmentRegistry.register(PunishmentAttachmentProvider)
        listener = PunishmentListener().also { it.register() }

        scheduler.launch {
            NexusPlugin.profiles.updates.collect { event ->
                val player = event.player ?: return@collect
                val oldAtt = event.old?.attachmentOf<PunishmentAttachment>()
                val newAtt = event.new.attachmentOf<PunishmentAttachment>() ?: return@collect
                val timeZone = event.new.attachmentOf<GeneralAttachment>()?.timeZone ?: TimeZone.UTC

                // get all new punishments compared to the old attachment
                val newPunishments = if (oldAtt == null) {
                    newAtt.punishments
                } else {
                    newAtt.punishments.filter { newPunishment ->
                        oldAtt.punishments.none { oldPunishment ->
                            oldPunishment.caseId == newPunishment.caseId
                        }
                    }
                }

                if (newPunishments.isNotEmpty()) {
                    player.playSound(sound {
                        it.type(key("minecraft:entity.pillager.hurt"))
                        it.source(Sound.Source.MASTER)
                    }, Sound.Emitter.self())
                }

                for (punishment in newPunishments) {
                    when (punishment.type) {
                        PunishmentType.WARNING -> {
                            val warnComponent = PunishComponents.warning(punishment)

                            player.sendMessage(textOfChildren(newline(), warnComponent, newline()))
                            player.showDialog(warnComponent.asDialogNotice(locale = player.locale()))
                        }

                        PunishmentType.KICK,
                        PunishmentType.BAN -> {
                            val kickComponent = PunishComponents.kick(punishment)

                            player.kick(kickComponent.translate(player.locale()))
                        }

                        PunishmentType.MUTE -> {
                            val kickComponent = PunishComponents.mute(punishment, timeZone, player.locale())

                            player.showDialog(kickComponent.asDialogNotice(locale = player.locale()))
                        }
                    }
                }
            }
        }
    }

    override fun onDisposed() {
        listener?.unregister()
        AttachmentRegistry.unregister(PunishmentAttachmentProvider)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun punish(
        issuer: String,
        target: ProfileSnapshot,
        type: PunishmentType,
        severity: PunishmentSeverity,
        reason: String,
    ): Punishment? {
        val att = target.attachmentOf<PunishmentAttachment>() ?: return null
        val punishment = Punishment(
            type = type,
            duration = severity.duration,
            reason = reason,
            severity = severity,
            timestamp = Clock.System.now(),
            issuedBy = issuer,
            caseId = Punishment.generateCaseId(type)
        )

        target.update { att.addPunishment(punishment) }

        return punishment
    }

    suspend fun withdraw(caseId: String) = suspendTransaction(NexusPlugin.database) {
        PunishmentsTable.deleteReturning(listOf(PunishmentsTable.punishedProfileId)) {
            PunishmentsTable.caseId eq caseId
        }.map {
            it[PunishmentsTable.punishedProfileId].value
        }.forEach { profileId ->
            NexusPlugin.profiles.get(
                id = profileId,
                bypassCache = true
            )
        }
    }
}