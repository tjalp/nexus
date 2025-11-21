package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*

object GeneralTable : CompositeIdTable("general_attachment") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val lastKnownName = varchar("last_known_name", 16).nullable()

    init {
        addIdColumn(profileId)
    }

    override val primaryKey = PrimaryKey(profileId)
}

data class GeneralAttachment(
    val id: UUID,
    val lastKnownName: String?
)

object GeneralAttachmentProvider : AttachmentProvider<GeneralAttachment> {
    override val key: AttachmentKey<GeneralAttachment> = AttachmentKeys.GENERAL

    private val db; get() = NexusServices.get<Database>()

    override suspend fun init() = suspendTransaction {
        SchemaUtils.create(GeneralTable)
    }

    override suspend fun load(profile: ProfileSnapshot): GeneralAttachment? = suspendTransaction(db) {
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
)