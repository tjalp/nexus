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
import net.tjalp.nexus.profile.attachment.AttachmentKeys.GENERAL
import net.tjalp.nexus.profile.attachment.AttachmentKeys.PUNISHMENT
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.PunishmentAttachmentProvider
import net.tjalp.nexus.profile.attachment.PunishmentsTable
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.util.asDialogNotice
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.translate
import net.tjalp.nexus.util.unregister
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object PunishmentsFeature : Feature("punishments") {

    private var listener: PunishmentListener? = null

    @Suppress("UnstableApiUsage")
    override fun enable() {
        super.enable()

        AttachmentRegistry.register(PunishmentAttachmentProvider)
        listener = PunishmentListener().also { it.register() }

        scheduler.launch {
            NexusPlugin.profiles.updates.collect { event ->
                val player = event.player ?: return@collect
                val oldAtt = event.old?.getAttachment(PUNISHMENT)
                val newAtt = event.new.getAttachment(PUNISHMENT) ?: return@collect
                val timeZone = event.new.getAttachment(GENERAL)?.timeZone ?: TimeZone.UTC

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

    override fun disable() {
        listener?.unregister()
        listener = null
        AttachmentRegistry.unregister(PunishmentAttachmentProvider)

        super.disable()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun punish(
        issuer: String,
        target: ProfileSnapshot,
        type: PunishmentType,
        severity: PunishmentSeverity,
        reason: String,
    ): Punishment? {
        val att = target.getAttachment(PUNISHMENT) ?: return null
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