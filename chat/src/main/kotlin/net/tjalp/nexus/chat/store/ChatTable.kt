package net.tjalp.nexus.chat.store

import net.tjalp.nexus.common.profile.model.ProfilesTable.id
import org.jetbrains.exposed.v1.core.Table

object ChatTable : Table("chat") {
    val profileId = uuid("profile_id").uniqueIndex()
    val messageCount = integer("message_count").default(0)

    override val primaryKey = PrimaryKey(id)
}