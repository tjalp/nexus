package net.tjalp.nexus.backend.schema

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import kotlinx.datetime.TimeZone
import net.tjalp.nexus.auth.AuthService
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.GeneralAttachment
import net.tjalp.nexus.profile.attachment.NoticesAttachment
import net.tjalp.nexus.profile.attachment.PunishmentAttachment
import net.tjalp.nexus.punishment.Punishment
import net.tjalp.nexus.punishment.PunishmentSeverity
import net.tjalp.nexus.punishment.PunishmentType
import java.util.*
import kotlin.time.Duration

fun SchemaBuilder.profileSchema(service: ProfilesService, authService: AuthService) {
    query("profile") {
        description = "Fetches a profile by its ID."
        resolver { id: UUID -> service.get(id) }
    }

    generalAttachmentSchema(service, authService)
    noticesAttachmentSchema(service, authService)
    punishmentSchema(service, authService)
}

private fun SchemaBuilder.generalAttachmentSchema(service: ProfilesService, authService: AuthService) {
    type<GeneralAttachment> {
        GeneralAttachment::id.ignore()
    }

    mutation("updateGeneralAttachment") {
        resolver { ctx: Context, id: UUID, lastKnownName: String?, preferredLocale: Locale?, timeZone: TimeZone? ->
            // Require authentication and profile access
            ctx.requireProfileAccess(authService, id)

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

private fun SchemaBuilder.noticesAttachmentSchema(service: ProfilesService, authService: AuthService) {
    type<NoticesAttachment> {
        NoticesAttachment::id.ignore()
    }

    mutation("updateNoticesAttachment") {
        resolver { ctx: Context, id: UUID, acceptedRulesVersion: Int?, seenRecommendations: Boolean? ->
            // Require authentication and profile access
            ctx.requireProfileAccess(authService, id)

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

private fun SchemaBuilder.punishmentSchema(service: ProfilesService, authService: AuthService) {
    type<PunishmentAttachment> {
        PunishmentAttachment::id.ignore()
        PunishmentAttachment::punishments.ignore()

        // Filter punishments based on user permissions
        property("punishments") {
            resolver { attachment, ctx: Context ->
                val user = ctx.getAuthenticatedUser(authService)

                println("DEBUG: User authenticated: ${user != null}")
                println("DEBUG: User profileId: ${user?.profileId}")
                println("DEBUG: Attachment profileId: ${attachment.id}")
                println("DEBUG: Can view punishments: ${user?.canViewPunishments(attachment.id)}")
                println("DEBUG: Total punishments: ${attachment.punishments.size}")

                // If no user is authenticated or user cannot view punishments, return empty list
                if (user == null || !user.canViewPunishments(attachment.id)) {
                    emptyList()
                } else {
                    attachment.punishments.toList()
                }
            }
        }
    }

    mutation("addPunishment") {
        resolver { ctx: Context, id: UUID, type: PunishmentType, severity: PunishmentSeverity, reason: String, issuedBy: String?, duration: Duration? ->
            // Require moderator access to add punishments
            val user = ctx.requireAuthenticatedUser(authService)
            if (!user.isModerator()) {
                error("Only moderators and administrators can add punishments")
            }

            val profile = service.get(id) ?: error("Profile with ID $id does not exist")
            val attachment = profile.attachmentOf<PunishmentAttachment>()
                ?: error("Punishment attachment for profile with ID $id does not exist")
            val punishment = Punishment.create(
                type = type,
                severity = severity,
                reason = reason,
                issuedBy = issuedBy ?: user.username,
                duration = duration ?: severity.duration
            )

            profile.update(service, {
                attachment.addPunishment(punishment)
            }).attachmentOf<PunishmentAttachment>()?.punishments
        }
    }

    mutation("removePunishment") {
        resolver { ctx: Context, id: UUID, caseId: String ->
            // Require moderator access to remove punishments
            val user = ctx.requireAuthenticatedUser(authService)
            if (!user.isModerator()) {
                error("Only moderators and administrators can remove punishments")
            }

            val profile = service.get(id) ?: error("Profile with ID $id does not exist")
            val attachment = profile.attachmentOf<PunishmentAttachment>()
                ?: error("Punishment attachment for profile with ID $id does not exist")

            profile.update(service, {
                attachment.removePunishment(caseId)
            }).attachmentOf<PunishmentAttachment>()?.punishments
        }
    }
}