package net.tjalp.nexus.feature.punishments

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.AttachmentKeys.PUNISHMENT
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.PunishmentAttachmentProvider
import net.tjalp.nexus.profile.attachment.PunishmentsTable
import net.tjalp.nexus.profile.model.ProfileSnapshot
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
    ) {
        val att = target.getAttachment(PUNISHMENT) ?: return
        val punishment = Punishment(
            type = type,
            reason = reason,
            severity = severity,
            timestamp = Clock.System.now(),
            issuedBy = issuer.toString(),
            caseId = Punishment.generateCaseId(type)
        )

        target.update { att.addPunishment(punishment) }
    }

    suspend fun withdraw(caseId: String) = suspendTransaction(NexusPlugin.database) {
        PunishmentsTable.deleteWhere {
            PunishmentsTable.caseId eq caseId
        }
    }
}