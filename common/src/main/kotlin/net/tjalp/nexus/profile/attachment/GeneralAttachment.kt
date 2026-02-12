package net.tjalp.nexus.profile.attachment

import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.tjalp.nexus.profile.model.ProfilesTable
import net.tjalp.nexus.serializer.LocaleAsStringSerializer
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*

object GeneralTable : CompositeIdTable("general_attachment") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val lastKnownName = varchar("last_known_name", 16).nullable()
    val preferredLocale = varchar("preferred_locale", 64).default(Locale.US.toLanguageTag())
    val timeZone = varchar("time_zone", 64).nullable()

    init {
        addIdColumn(profileId)
    }

    override val primaryKey = PrimaryKey(profileId)
}

@Serializable
@SerialName("general")
data class GeneralAttachment(
    val lastKnownName: String?,
    @Serializable(with = LocaleAsStringSerializer::class)
    val preferredLocale: Locale,
    val timeZone: TimeZone?
) : ProfileAttachment {

    @Transient lateinit var id: UUID

    fun setLastKnownName(value: String?) {
        GeneralTable.update({ GeneralTable.profileId eq id }) {
            it[GeneralTable.lastKnownName] = value
        }
    }

    fun setPreferredLocale(value: Locale) {
        GeneralTable.update({ GeneralTable.profileId eq id }) {
            it[GeneralTable.preferredLocale] = value.toLanguageTag()
        }
    }

    fun setTimeZone(value: TimeZone?) {
        GeneralTable.update({ GeneralTable.profileId eq id }) {
            it[GeneralTable.timeZone] = value?.id
        }
    }
}

object GeneralAttachmentProvider : AttachmentProvider<GeneralAttachment> {

    override suspend fun load(db: Database, id: UUID): GeneralAttachment? = suspendTransaction(db) {
        val attachment = GeneralTable.selectAll().where(GeneralTable.profileId eq id)
            .firstOrNull()?.toGeneralAttachment()

        if (attachment == null) {
            GeneralTable.upsert {
                it[profileId] = id
            }

            return@suspendTransaction GeneralTable.selectAll().where(GeneralTable.profileId eq id)
                .firstOrNull()?.toGeneralAttachment()
        }

        attachment
    }
}

fun ResultRow.toGeneralAttachment(): GeneralAttachment = GeneralAttachment(
    lastKnownName = this[GeneralTable.lastKnownName],
    preferredLocale = this[GeneralTable.preferredLocale].let { Locale.forLanguageTag(it) } ?: Locale.US,
    timeZone = this[GeneralTable.timeZone]?.let { TimeZone.of(it) }
).also { it.id = this[GeneralTable.profileId].value }