package net.tjalp.nexus.common.profile.attachment

import net.tjalp.nexus.common.NexusServices
import net.tjalp.nexus.common.profile.ProfileModule
import net.tjalp.nexus.common.profile.ProfileSnapshot
import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

object GeneralTable : Table("general_attachment") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val lastKnownName = varchar("last_known_name", 16).nullable()

    override val primaryKey = PrimaryKey(profileId)
}

data class GeneralAttachment(val lastKnownName: String?)

object GeneralAttachmentModule : ProfileModule {

    private val database = NexusServices.get(Database::class)

    init {
        transaction(database) {
            SchemaUtils.create(GeneralTable)
        }
    }

    override suspend fun onProfileLoad(profile: ProfileSnapshot) {
        val row = suspendTransaction(database) {
            GeneralTable.selectAll().where { GeneralTable.profileId eq profile.id.value }
                .single()
        }

        profile.setAttachment(CommonKeys.GENERAL, GeneralAttachment(
            lastKnownName = row[GeneralTable.lastKnownName]
        ))
    }

    override suspend fun onProfileSave(profile: ProfileSnapshot) {
        val attachment = profile.getAttachment(CommonKeys.GENERAL)
            ?: return suspendTransaction(database) {
                GeneralTable.upsert { it[profileId] = profile.id.value }
            }

        suspendTransaction(database) {
            GeneralTable.upsert {
                it[profileId] = profile.id.value
                it[lastKnownName] = attachment.lastKnownName
            }
        }
    }
}