@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.profile.model.ProfilesTable
import net.tjalp.nexus.punishment.Punishment
import net.tjalp.nexus.punishment.PunishmentSeverity
import net.tjalp.nexus.punishment.PunishmentType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.ExperimentalTime

object PunishmentsTable : IntIdTable("punishments") {
    val caseId = varchar("case_id", 32).uniqueIndex()
    val punishedProfileId = reference("punished_profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val type = varchar("punishment_type", 32)
    val reason = text("reason")
    val severity = varchar("punishment_severity", 32)
    val duration = duration("duration")
    val timestamp = timestamp("punishment_timestamp")
    val issuedBy = varchar("issued_by", 36)
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
            it[issuedBy] = punishment.issuedBy
        }
    }

    fun removePunishment(caseId: String) {
        PunishmentsTable.deleteWhere {
            PunishmentsTable.caseId eq caseId and (PunishmentsTable.punishedProfileId eq profileId)
        }
    }
}

object PunishmentAttachmentProvider : AttachmentProvider<PunishmentAttachment> {

    override suspend fun load(db: Database, id: UUID): PunishmentAttachment = suspendTransaction(db) {
        val punishments = PunishmentsTable.selectAll().where(PunishmentsTable.punishedProfileId eq id)
            .map { it.toPunishment() }

        return@suspendTransaction PunishmentAttachment(
            profileId = id,
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
    issuedBy = this[PunishmentsTable.issuedBy],
    caseId = this[PunishmentsTable.caseId],
)