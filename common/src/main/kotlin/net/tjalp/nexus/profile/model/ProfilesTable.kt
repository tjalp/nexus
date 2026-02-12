package net.tjalp.nexus.profile.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.serializer.UUIDAsStringSerializer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import java.util.*
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
@Serializable
data class ProfileSnapshot(
    @Serializable(with = UUIDAsStringSerializer::class)
    val id: UUID,
    val createdAt: Instant,
    val modifiedAt: Instant,
    @Contextual
    val attachments: Collection<Any> = emptyList()
) {

    /**
     * Retrieves an attachment associated with this profile.
     *
     * @param T The type of the attachment to retrieve.
     * @return The attachment associated with the given type, or null if not present.
     */
    inline fun <reified T> attachmentOf(): T? {
        return attachments.filterIsInstance<T>().firstOrNull()
    }

    /**
     * Updates the profile using the provided statements and the specified [ProfilesService].
     *
     * @param service The [ProfilesService] to use for the update operation.
     * @param statements A vararg of lambda functions containing the update logic.
     * @return The updated [ProfileSnapshot].
     */
    suspend fun update(service: ProfilesService, vararg statements: () -> Unit) =
        service.upsert(this, statements = statements)
}

@OptIn(ExperimentalTime::class)
fun ResultRow.toProfileSnapshot(attachments: Collection<Any>): ProfileSnapshot = ProfileSnapshot(
    id = this[ProfilesTable.id].value,
    createdAt = this[ProfilesTable.createdAt],
    modifiedAt = this[ProfilesTable.modifiedAt],
    attachments = attachments
)