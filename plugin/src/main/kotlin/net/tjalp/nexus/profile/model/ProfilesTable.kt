package net.tjalp.nexus.profile.model

import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.ProfilesService
import org.bukkit.Bukkit
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object ProfilesTable : UUIDTable("profiles") {
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val modifiedAt = timestamp("modified_at").defaultExpression(CurrentTimestamp)
}

@OptIn(ExperimentalTime::class)
data class ProfileSnapshot(
    val id: UUID,
    val createdAt: Instant,
    val modifiedAt: Instant
) {

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
        .upsert(this, cache = Bukkit.getPlayer(this.id) != null, statements = statements)

    override fun toString(): String {
        return "ProfileEntity(id=${id}, createdAt=$createdAt, modifiedAt=$modifiedAt, attachments=$attachments)"
    }
}

@OptIn(ExperimentalTime::class)
fun ResultRow.toProfileSnapshot(): ProfileSnapshot = ProfileSnapshot(
    id = this[ProfilesTable.id].value,
    createdAt = this[ProfilesTable.createdAt],
    modifiedAt = this[ProfilesTable.modifiedAt]
)