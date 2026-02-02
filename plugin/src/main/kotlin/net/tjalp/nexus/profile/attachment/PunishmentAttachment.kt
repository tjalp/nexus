@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.feature.punishments.Punishment
import net.tjalp.nexus.feature.punishments.PunishmentSeverity
import net.tjalp.nexus.feature.punishments.PunishmentType
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.ExperimentalTime

object PunishmentsTable : IntIdTable("punishments") {
    val caseId = varchar("case_id", 32).uniqueIndex()
    val punishedProfileId = reference("punished_profile_id", ProfilesTable.id, onDelete = ReferenceOption.NO_ACTION)
    val type = varchar("punishment_type", 32)
    val reason = text("reason")
    val severity = varchar("punishment_severity", 32)
    val duration = duration("duration")
    val timestamp = timestamp("punishment_timestamp")
    val issuerProfileId = reference("issuer_profile_id", ProfilesTable.id, onDelete = ReferenceOption.NO_ACTION)
}

data class PunishmentAttachment(
    val profileId: UUID,
    val punishments: Collection<Punishment>
) {

    fun addPunishment(punishment: Punishment) {
        PunishmentsTable.insert {
            it[caseId] = punishment.caseId
            it[punishedProfileId] = profileId
            it[type] = punishment.type.name
            it[reason] = punishment.reason
            it[duration] = punishment.duration
            it[severity] = punishment.severity.name
            it[timestamp] = punishment.timestamp
            it[issuerProfileId] = UUID.fromString(punishment.issuedBy)
        }
    }

    fun removePunishment(caseId: String) {
        PunishmentsTable.deleteWhere {
            PunishmentsTable.caseId eq caseId and (PunishmentsTable.punishedProfileId eq profileId)
        }
    }
}

object PunishmentAttachmentProvider : AttachmentProvider<PunishmentAttachment> {

    override val key: AttachmentKey<PunishmentAttachment> = AttachmentKeys.PUNISHMENT

    override suspend fun load(profile: ProfileSnapshot): PunishmentAttachment = suspendTransaction {
        val punishments = PunishmentsTable.selectAll().where(PunishmentsTable.punishedProfileId eq profile.id)
            .map { it.toPunishment() }

        return@suspendTransaction PunishmentAttachment(
            profileId = profile.id,
            punishments = punishments
        )
    }
}

fun ResultRow.toPunishment(): Punishment = Punishment(
    type = PunishmentType.valueOf(this[PunishmentsTable.type]),
    reason = this[PunishmentsTable.reason],
    duration = this[PunishmentsTable.duration],
    severity = PunishmentSeverity.valueOf(this[PunishmentsTable.severity]),
    timestamp = this[PunishmentsTable.timestamp],
    issuedBy = this[PunishmentsTable.issuerProfileId].value.toString(),
    caseId = this[PunishmentsTable.caseId],
)