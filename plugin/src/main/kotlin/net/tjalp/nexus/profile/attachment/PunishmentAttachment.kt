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
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.ExperimentalTime

object PunishmentTable : IntIdTable("punishments") {
    val caseId = varchar("case_id", 32).uniqueIndex()
    val punishedProfileId = reference("punished_profile_id", ProfilesTable.id, onDelete = ReferenceOption.NO_ACTION)
    val type = varchar("type", 32)
    val reason = text("reason")
    val severity = varchar("severity", 32)
    val timestamp = timestamp("timestamp")
    val issuerProfileId = reference("issuer_profile_id", ProfilesTable.id, onDelete = ReferenceOption.NO_ACTION)
}

data class PunishmentAttachment(
    val profileId: UUID,
    val punishments: Collection<Punishment>
) {

    fun addPunishment(punishment: Punishment) {
        PunishmentTable.insert {
            it[caseId] = punishment.caseId
            it[punishedProfileId] = profileId
            it[type] = punishment.type.name
            it[reason] = punishment.reason
            it[severity] = punishment.severity.name
            it[timestamp] = punishment.timestamp
            it[issuerProfileId] = UUID.fromString(punishment.issuedBy)
        }
    }

    fun removePunishment(caseId: String) {
        PunishmentTable.deleteWhere {
            PunishmentTable.caseId eq caseId and (PunishmentTable.punishedProfileId eq profileId)
        }
    }
}

object PunishmentAttachmentProvider : AttachmentProvider<PunishmentAttachment> {

    override val key: AttachmentKey<PunishmentAttachment> = AttachmentKeys.PUNISHMENT

    override suspend fun load(profile: ProfileSnapshot): PunishmentAttachment = suspendTransaction {
        val punishments = PunishmentTable.selectAll().where(PunishmentTable.punishedProfileId eq profile.id)
            .map { it.toPunishment() }

        return@suspendTransaction PunishmentAttachment(
            profileId = profile.id,
            punishments = punishments
        )
    }
}

fun ResultRow.toPunishment(): Punishment = Punishment(
    type = PunishmentType.valueOf(this[PunishmentTable.type]),
    reason = this[PunishmentTable.reason],
    severity = PunishmentSeverity.valueOf(this[PunishmentTable.severity]),
    timestamp = this[PunishmentTable.timestamp],
    issuedBy = this[PunishmentTable.issuerProfileId].value.toString(),
    caseId = this[PunishmentTable.caseId],
)