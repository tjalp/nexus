package net.tjalp.nexus.feature.punishments

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.minimessage.translation.Argument
import net.kyori.adventure.translation.GlobalTranslator
import net.tjalp.nexus.Constants.PUNISHMENTS_MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PUNISHMENTS_PRIMARY_COLOR
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.AttachmentKeys.GENERAL
import net.tjalp.nexus.profile.attachment.AttachmentKeys.PUNISHMENT
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.PunishmentAttachmentProvider
import net.tjalp.nexus.profile.attachment.PunishmentsTable
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
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

                for (punishment in newPunishments) {
                    when (punishment.type) {
                        PunishmentType.WARNING -> {
                            player.sendMessage(buildWarnMessage(punishment))
                            player.playSound(sound {
                                it.type(key("minecraft:entity.pillager.hurt"))
                                it.source(Sound.Source.MASTER)
                            }, Sound.Emitter.self())
                        }

                        PunishmentType.KICK,
                        PunishmentType.BAN -> {
                            val kickComponent = PunishComponents.kick(punishment)
                            val translated = GlobalTranslator.render(kickComponent, player.locale())

                            player.kick(translated)
                        }

                        PunishmentType.MUTE -> {
                            val kickComponent = PunishComponents.mute(punishment, timeZone, player.locale())
                            val translated = GlobalTranslator.render(kickComponent, player.locale())

                            player.showDialog(Dialog.create { builder ->
                                builder.empty()
                                    .base(
                                        DialogBase.builder(empty())
                                            .body(
                                                listOf(
                                                    DialogBody.plainMessage(translated)
                                                )
                                            )
                                            .build()
                                    )
                                    .type(DialogType.notice())
                            })
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
        issuer: UUID,
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
            issuedBy = issuer.toString(),
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
                cache = NexusPlugin.server.getPlayer(profileId) != null,
                bypassCache = true
            )
        }
    }

    private fun buildWarnMessage(punishment: Punishment): Component {
        val reason = PunishmentReason.entries.firstOrNull {
            it.key.equals(punishment.reason, ignoreCase = true)
        }?.reason ?: text(punishment.reason)
        val header = translatable(
            "punishment.warning.received.header",
            PUNISHMENTS_PRIMARY_COLOR,
            Argument.component("reason", reason.colorIfAbsent(PUNISHMENTS_MONOCHROME_COLOR))
        )

        return text()
            .appendNewline()
            .append(header)
            .appendNewline()
            .build()
    }
}