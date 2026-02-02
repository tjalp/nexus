package net.tjalp.nexus.profile.attachment

import kotlinx.datetime.TimeZone
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*

object GeneralTable : CompositeIdTable("general_attachment") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val lastKnownName = varchar("last_known_name", 16).nullable()
    val preferredLocale = varchar("preferred_locale", 64).default(Locale.US.toLanguageTag())
    val timeZone = varchar("time_zone", 64).default(TimeZone.UTC.id)

    init {
        addIdColumn(profileId)
    }

    override val primaryKey = PrimaryKey(profileId)
}

class GeneralAttachment(
    val id: UUID,
    lastKnownName: String?,
    preferredLocale: Locale,
    timeZone: TimeZone
) {

    var lastKnownName: String? = lastKnownName
        set(value) {
            GeneralTable.update({ GeneralTable.profileId eq id }) {
                it[GeneralTable.lastKnownName] = value
            }
        }

    var preferredLocale: Locale = preferredLocale
        set(value) {
            GeneralTable.update({ GeneralTable.profileId eq id }) {
                it[GeneralTable.preferredLocale] = value.toLanguageTag()
            }
        }

    var timeZone: TimeZone = timeZone
        set(value) {
            GeneralTable.update({ GeneralTable.profileId eq id }) {
                it[GeneralTable.timeZone] = value.id
            }
        }

    override fun toString(): String {
        return "GeneralAttachment(id=$id, lastKnownName=$lastKnownName, preferredLocale=$preferredLocale, timeZone=$timeZone)"
    }
}

object GeneralAttachmentProvider : AttachmentProvider<GeneralAttachment> {
    override val key: AttachmentKey<GeneralAttachment> = AttachmentKeys.GENERAL

    override suspend fun load(profile: ProfileSnapshot): GeneralAttachment? = suspendTransaction(NexusPlugin.database) {
        val attachment = GeneralTable.selectAll().where(GeneralTable.profileId eq profile.id)
            .firstOrNull()?.toGeneralAttachment()

        if (attachment == null) {
            val newAttachmentId = GeneralTable.upsert {
                it[profileId] = profile.id
            } get GeneralTable.id

            return@suspendTransaction GeneralTable.selectAll().where(GeneralTable.profileId eq profile.id)
                .firstOrNull()?.toGeneralAttachment()
        }

        attachment
    }
}

fun ResultRow.toGeneralAttachment(): GeneralAttachment = GeneralAttachment(
    id = this[GeneralTable.profileId].value,
    lastKnownName = this[GeneralTable.lastKnownName],
    preferredLocale = this[GeneralTable.preferredLocale].let { Locale.forLanguageTag(it) } ?: Locale.US,
    timeZone = this[GeneralTable.timeZone].let { TimeZone.of(it) }
)