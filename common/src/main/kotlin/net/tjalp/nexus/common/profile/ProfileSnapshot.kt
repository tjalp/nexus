package net.tjalp.nexus.common.profile

import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A snapshot of a profile at a specific point in time.
 *
 * @property service The [ProfilesService] that manages this profile.
 * @property id The unique identifier of the profile.
 * @property createdAt The timestamp when the profile was created.
 * @property updatedAt The timestamp when the profile was last updated.
 */
@OptIn(ExperimentalTime::class)
data class ProfileSnapshot(
    private val service: ProfilesService,
    val id: ProfileId,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    private val attachments: MutableMap<AttachmentKey<*>, Any> = mutableMapOf()

    /**
     * Gets an attachment from this profile.
     *
     * @param key The key of the attachment to retrieve.
     * @return The attachment associated with the given key, or null if not present.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAttachment(key: AttachmentKey<T>): T? =
        attachments[key] as? T

    /**
     * Sets an attachment on this profile.
     *
     * @param key The key of the attachment to set.
     * @param value The value of the attachment to set.
     */
    fun <T : Any> setAttachment(key: AttachmentKey<T>, value: T) {
        attachments[key] = value
    }

    /**
     * Removes an attachment from this profile.
     *
     * @param key The key of the attachment to remove.
     */
    fun <T : Any> removeAttachment(key: AttachmentKey<T>) {
        attachments.remove(key)
    }

    /**
     * Updates this profile in the database.
     *
     * @param statement The upsert statement to apply to the profile.
     * @param additionalStatements Additional statements to execute after the upsert to, for example, modify attachments.
     * @return A [ProfileSnapshot] representing the updated profile.
     */
    suspend fun update(
        statement: ProfilesTable.(UpsertStatement<Long>) -> Unit = {},
        vararg additionalStatements: () -> Unit
    ) = service.upsert(this, statement = statement, additionalStatements = additionalStatements)

    override fun toString(): String {
        return "ProfileSnapshot(id=${id.value}, createdAt=$createdAt, updatedAt=$updatedAt, attachments=$attachments)"
    }
}
