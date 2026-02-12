package net.tjalp.nexus.profile.attachment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.tjalp.nexus.profile.model.ProfilesTable
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

object NoticesTable : CompositeIdTable("notices_attachments") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val acceptedRulesVersion = integer("accepted_rules_version").default(0)
    val seenRecommendations = bool("seen_recommendations").default(false)

    init {
        addIdColumn(profileId)
    }

    override val primaryKey = PrimaryKey(profileId)
}

@Serializable
@SerialName("notices")
data class NoticesAttachment(
    val acceptedRulesVersion: Int,
    val hasSeenRecommendations: Boolean
) : ProfileAttachment {

    @Transient lateinit var id: UUID

    fun setAcceptedRules(version: Int) {
        NoticesTable.update({ NoticesTable.profileId eq id }) {
            it[NoticesTable.acceptedRulesVersion] = version
        }
    }

    fun hasAcceptedRules(version: Int): Boolean = acceptedRulesVersion >= version

    fun setSeenRecommendations(value: Boolean) {
        NoticesTable.update({ NoticesTable.profileId eq id }) {
            it[NoticesTable.seenRecommendations] = value
        }
    }
}

object NoticesAttachmentProvider : AttachmentProvider<NoticesAttachment> {

    override suspend fun load(db: Database, id: UUID): NoticesAttachment? = suspendTransaction(db) {
        val attachment = NoticesTable.selectAll().where(NoticesTable.profileId eq id)
            .firstOrNull()?.toNoticesAttachment()

        if (attachment == null) {
            NoticesTable.upsert {
                it[profileId] = id
            }

            return@suspendTransaction NoticesTable.selectAll().where(NoticesTable.profileId eq id)
                .firstOrNull()?.toNoticesAttachment()
        }

        attachment
    }
}

fun ResultRow.toNoticesAttachment(): NoticesAttachment = NoticesAttachment(
    acceptedRulesVersion = this[NoticesTable.acceptedRulesVersion],
    hasSeenRecommendations = this[NoticesTable.seenRecommendations]
).also { it.id = this[NoticesTable.profileId].value }