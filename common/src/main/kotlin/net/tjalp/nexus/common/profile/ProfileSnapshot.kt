package net.tjalp.nexus.common.profile

import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class ProfileSnapshot(
    private val service: ProfilesService,
    val id: ProfileId,
    val lastKnownName: String?,
    val createdAt: Instant,
) {

    private val attachments: MutableMap<AttachmentKey<*>, Any> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAttachment(key: AttachmentKey<T>): T? =
        attachments[key] as? T

    fun <T : Any> setAttachment(key: AttachmentKey<T>, value: T) {
        attachments[key] = value
    }

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
        return "ProfileSnapshot(id=${id.value}, lastKnownName=$lastKnownName, createdAt=$createdAt, attachments=$attachments)"
    }
}
