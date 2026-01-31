package net.tjalp.nexus.feature.punishments

import net.tjalp.nexus.Feature
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.PunishmentAttachmentProvider
import net.tjalp.nexus.profile.model.ProfileSnapshot
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
        issuer: ProfileSnapshot,
        target: ProfileSnapshot,
        type: PunishmentType,
        severity: PunishmentSeverity,
        reason: String,
    ) {
        val punishment = Punishment(
            type = type,
            reason = reason,
            severity = severity,
            timestamp = Clock.System.now(),
            issuedBy = issuer.id.toString(),
            caseId = Punishment.generateCaseId(type)
        )
    }
}