package net.tjalp.nexus.profile.model

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.AttachmentKey
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

/**
 * A snapshot of a profile at a certain point in time.
 *
 * @param id The unique identifier of the profile.
 * @param createdAt The timestamp when the profile was created.
 * @param modifiedAt The timestamp when the profile was last modified.
 */
@OptIn(ExperimentalTime::class)
data class ProfileSnapshot(
    val id: UUID,
    val createdAt: Instant,
    val modifiedAt: Instant
) {

    private val attachments = ConcurrentHashMap<AttachmentKey<*>, Any>()

    /**
     * Retrieves an attachment associated with this profile. Returns null if the attachment is not present.
     *
     * @param key The key of the attachment to retrieve.
     * @return The attachment associated with the given key, or null if not present.
     */
    fun <T : Any> getAttachment(key: AttachmentKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return attachments[key] as? T
    }

    /**
     * Sets an attachment for this profile.
     *
     * @param key The key of the attachment to set.
     * @param value The attachment value to associate with the given key.
     */
    fun <T : Any> setAttachment(key: AttachmentKey<T>, value: T) {
        attachments[key] = value
    }

    /**
     * Checks if an attachment is present for this profile.
     *
     * @param key The key of the attachment to check.
     * @return True if the attachment is present, false otherwise.
     */
    fun hasAttachment(key: AttachmentKey<*>) = attachments.containsKey(key)

    /**
     * Removes an attachment associated with this profile.
     *
     * @param key The key of the attachment to remove.
     */
    fun removeAttachment(key: AttachmentKey<*>) {
        attachments.remove(key)
    }

    /**
     * Updates the profile with the provided statement.
     *
     * @param statement A lambda function containing the update logic.
     * @return The updated [ProfileSnapshot].
     */
    suspend fun update(statement: () -> Unit) = update(*arrayOf(statement))

    /**
     * Updates the profile with the provided statements.
     *
     * @param statements A vararg of lambda functions containing the update logic.
     * @return The updated [ProfileSnapshot].
     */
    suspend fun update(vararg statements: () -> Unit) = NexusPlugin.profiles
        .upsert(this, statements = statements)

    /**
     * Updates a specific attachment of the profile using the provided statement.
     *
     * @param key The key of the attachment to update.
     * @param statement A lambda function containing the update logic for the attachment.
     * @return The updated [ProfileSnapshot].
     * @throws IllegalStateException if the attachment is not loaded for the profile.
     */
    suspend fun <T : Any> update(key: AttachmentKey<T>, statement: (T) -> Unit) =
        update(key, *arrayOf(statement))

    /**
     * Updates a specific attachment of the profile using the provided statements.
     *
     * @param key The key of the attachment to update.
     * @param statements A vararg of lambda functions containing the update logic for the attachment.
     * @return The updated [ProfileSnapshot].
     * @throws IllegalStateException if the attachment is not loaded for the profile.
     */
    suspend fun <T : Any> update(key: AttachmentKey<T>, vararg statements: (T) -> Unit): ProfileSnapshot {
        val att = getAttachment(key) ?: error("Attachment $key not loaded for profile $id")
        val actualStatements = statements.map { stmt -> { stmt(att) } }.toTypedArray()

        return update(*actualStatements)
    }

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