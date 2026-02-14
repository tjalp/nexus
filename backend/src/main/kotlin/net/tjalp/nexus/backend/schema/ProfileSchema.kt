package net.tjalp.nexus.backend.schema

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import kotlinx.datetime.TimeZone
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.GeneralAttachment
import net.tjalp.nexus.profile.attachment.NoticesAttachment
import net.tjalp.nexus.profile.attachment.PunishmentAttachment
import net.tjalp.nexus.punishment.Punishment
import net.tjalp.nexus.punishment.PunishmentSeverity
import net.tjalp.nexus.punishment.PunishmentType
import java.util.*
import kotlin.time.Duration

fun SchemaBuilder.profileSchema(service: ProfilesService) {
    query("profile") {
        description = "Fetches a profile by its ID."
        resolver { id: UUID -> service.get(id) }
    }

    generalAttachmentSchema(service)
    noticesAttachmentSchema(service)
    punishmentSchema(service)
}

private fun SchemaBuilder.generalAttachmentSchema(service: ProfilesService) {
    type<GeneralAttachment> {
        GeneralAttachment::id.ignore()
    }

    mutation("updateGeneralAttachment") {
        resolver { id: UUID, lastKnownName: String?, preferredLocale: Locale?, timeZone: TimeZone? ->
            val profile = service.get(id) ?: error("Profile with ID $id does not exist")
            val attachment = profile.attachmentOf<GeneralAttachment>()
                ?: error("General attachment for profile with ID $id does not exist")

            profile.update(service, {
                lastKnownName?.let { attachment.setLastKnownName(lastKnownName) }
                preferredLocale?.let { attachment.setPreferredLocale(it) }
                timeZone?.let { attachment.setTimeZone(it) }
            })
        }
    }
}

private fun SchemaBuilder.noticesAttachmentSchema(service: ProfilesService) {
    type<NoticesAttachment> {
        NoticesAttachment::id.ignore()
    }

    mutation("updateNoticesAttachment") {
        resolver { id: UUID, acceptedRulesVersion: Int?, seenRecommendations: Boolean? ->
            val profile = service.get(id) ?: error("Profile with ID $id does not exist")
            val attachment = profile.attachmentOf<NoticesAttachment>()
                ?: error("Notices attachment for profile with ID $id does not exist")

            profile.update(service, {
                acceptedRulesVersion?.let { attachment.setAcceptedRules(it) }
                seenRecommendations?.let { attachment.setSeenRecommendations(it) }
            })
        }
    }
}

private fun SchemaBuilder.punishmentSchema(service: ProfilesService) {
    type<PunishmentAttachment> {
        PunishmentAttachment::id.ignore()
    }

    mutation("addPunishment") {
        resolver { id: UUID, type: PunishmentType, severity: PunishmentSeverity, reason: String, issuedBy: String?, duration: Duration? ->
            val profile = service.get(id) ?: error("Profile with ID $id does not exist")
            val attachment = profile.attachmentOf<PunishmentAttachment>()
                ?: error("Punishment attachment for profile with ID $id does not exist")
            val punishment = Punishment.create(
                type = type,
                severity = severity,
                reason = reason,
                issuedBy = issuedBy ?: "System",
                duration = duration ?: severity.duration
            )

            profile.update(service, {
                attachment.addPunishment(punishment)
            }).attachmentOf<PunishmentAttachment>()?.punishments
        }
    }

    mutation("removePunishment") {
        resolver { id: UUID, caseId: String ->
            val profile = service.get(id) ?: error("Profile with ID $id does not exist")
            val attachment = profile.attachmentOf<PunishmentAttachment>()
                ?: error("Punishment attachment for profile with ID $id does not exist")

            profile.update(service, {
                attachment.removePunishment(caseId)
            }).attachmentOf<PunishmentAttachment>()?.punishments
        }
    }
}