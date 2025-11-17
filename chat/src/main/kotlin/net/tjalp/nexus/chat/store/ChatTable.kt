package net.tjalp.nexus.chat.store

import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object ChatTable : Table("chat") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val messageCount = integer("message_count").default(0)

    override val primaryKey = PrimaryKey(profileId)
}