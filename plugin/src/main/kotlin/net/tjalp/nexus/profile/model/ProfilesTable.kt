package net.tjalp.nexus.profile.model

import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.ProfilesService
import org.bukkit.Bukkit
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.ImmutableEntityClass
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object ProfilesTable : UUIDTable("profiles") {
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val modifiedAt = timestamp("modified_at").defaultExpression(CurrentTimestamp)
}

@OptIn(ExperimentalTime::class)
class ProfileSnapshot(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : ImmutableEntityClass<UUID, ProfileSnapshot>(ProfilesTable)

    val createdAt by ProfilesTable.createdAt
    val modifiedAt by ProfilesTable.modifiedAt

    private val attachments = ConcurrentHashMap<AttachmentKey<*>, Any>()

    fun <T : Any> getAttachment(key: AttachmentKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return attachments[key] as? T
    }

    fun <T : Any> setAttachment(key: AttachmentKey<T>, value: T) {
        attachments[key] = value
    }

    fun <T : Any> removeAttachment(key: AttachmentKey<T>) {
        attachments.remove(key)
    }

    suspend fun update(statement: () -> Unit) = update(*arrayOf(statement))

    suspend fun update(vararg statements: () -> Unit) = NexusServices.get<ProfilesService>()
        .upsert(this, cache = Bukkit.getPlayer(this.id.value) != null, statements = statements)

    override fun toString(): String {
        return "ProfileEntity(id=${id.value}, createdAt=$createdAt, modifiedAt=$modifiedAt, attachments=$attachments)"
    }
}