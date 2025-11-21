package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.model.ProfileSnapshot
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

object EffortShopTable : CompositeIdTable("effort_shop_attachment") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val effortPoints = integer("effort_points").default(0)

    init {
        addIdColumn(profileId)
    }

    override val primaryKey = PrimaryKey(profileId)
}

class EffortShopAttachment(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : ImmutableEntityClass<CompositeID, EffortShopAttachment>(EffortShopTable)

    val effortPoints by EffortShopTable.effortPoints
}

object EffortShopAttachmentProvider : AttachmentProvider<EffortShopAttachment> {
    override val key: AttachmentKey<EffortShopAttachment> = AttachmentKeys.EFFORT_SHOP

    private val db; get() = NexusServices.get<Database>()

    override suspend fun init() = suspendTransaction {
        SchemaUtils.create(EffortShopTable)
    }

    override suspend fun load(profile: ProfileSnapshot): EffortShopAttachment? = suspendTransaction(db) {
        val attachment = EffortShopAttachment.find { EffortShopTable.profileId eq profile.id.value }
            .singleOrNull()

        if (attachment == null) {
            val newAttachmentId = EffortShopTable.upsert {
                it[profileId] = profile.id.value
            } get EffortShopTable.id

            return@suspendTransaction EffortShopAttachment.findById(newAttachmentId)
        }

        attachment
    }
}