package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*

object EffortShopTable : CompositeIdTable("effort_shop_attachment") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val effortBalance = integer("effort_balance").default(0)

    init {
        addIdColumn(profileId)
    }

    override val primaryKey = PrimaryKey(profileId)
}

class EffortShopAttachment(
    val id: UUID,
    effortBalance: Int
) {

    var effortBalance: Int = effortBalance
        set(value) {
            EffortShopTable.update({ EffortShopTable.profileId eq id }) {
                it[EffortShopTable.effortBalance] = value
            }
        }

    // TODO dont use a separate query for this
    fun addEffort(amount: Int) {
        EffortShopTable.update({ EffortShopTable.profileId eq id }) {
            it[EffortShopTable.effortBalance] = EffortShopTable.effortBalance + amount
        }
    }

    override fun toString(): String {
        return "EffortShopAttachment(id=$id, effortBalance=$effortBalance)"
    }
}

object EffortShopAttachmentProvider : AttachmentProvider<EffortShopAttachment> {
    override val key: AttachmentKey<EffortShopAttachment> = AttachmentKeys.EFFORT_SHOP

    override suspend fun load(profile: ProfileSnapshot): EffortShopAttachment? = suspendTransaction(NexusPlugin.database) {
        val attachment = EffortShopTable.selectAll().where(EffortShopTable.profileId eq profile.id)
            .firstOrNull()?.toEffortShopAttachment()

        if (attachment == null) {
            val newAttachmentId = EffortShopTable.upsert {
                it[profileId] = profile.id
            } get EffortShopTable.id

            return@suspendTransaction EffortShopTable.selectAll().where(EffortShopTable.profileId eq profile.id)
                .firstOrNull()?.toEffortShopAttachment()
        }

        attachment
    }
}

fun ResultRow.toEffortShopAttachment(): EffortShopAttachment = EffortShopAttachment(
    id = this[EffortShopTable.profileId].value,
    effortBalance = this[EffortShopTable.effortBalance],
)