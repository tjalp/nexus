package net.tjalp.nexus.feature.punishments

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.AttachmentKeys.PUNISHMENT
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.PunishmentAttachmentProvider
import net.tjalp.nexus.profile.attachment.PunishmentsTable
import net.tjalp.nexus.profile.model.ProfileSnapshot
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object PunishmentsFeature : Feature("punishments") {

    override fun enable() {
        super.enable()

        AttachmentRegistry.register(PunishmentAttachmentProvider)
    }

    override fun disable() {
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
}