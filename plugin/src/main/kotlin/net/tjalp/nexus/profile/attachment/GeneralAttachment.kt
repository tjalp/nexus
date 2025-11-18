package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.model.ProfileEntity
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.CompositeEntity
import org.jetbrains.exposed.v1.dao.ImmutableEntityClass
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

object GeneralTable : CompositeIdTable("general_attachment") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val lastKnownName = varchar("last_known_name", 16).nullable()

    init {
        addIdColumn(profileId)
    }

    override val primaryKey = PrimaryKey(profileId)
}

class GeneralAttachment(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : ImmutableEntityClass<CompositeID, GeneralAttachment>(GeneralTable)

    val lastKnownName by GeneralTable.lastKnownName
}

object GeneralAttachmentProvider : AttachmentProvider<GeneralAttachment> {
    override val key: AttachmentKey<GeneralAttachment> = AttachmentKeys.GENERAL

    private val db; get() = NexusServices.get<Database>()

    override suspend fun init() = suspendTransaction {
        SchemaUtils.create(GeneralTable)
    }

    override suspend fun load(profile: ProfileEntity): GeneralAttachment? = suspendTransaction(db) {
        val attachment = GeneralAttachment.find { GeneralTable.profileId eq profile.id.value }
            .singleOrNull()

        if (attachment == null) {
            val newAttachmentId = GeneralTable.upsert {
                it[profileId] = profile.id.value
            } get GeneralTable.id

            return@suspendTransaction GeneralAttachment.findById(newAttachmentId)
        }

        attachment
    }
}