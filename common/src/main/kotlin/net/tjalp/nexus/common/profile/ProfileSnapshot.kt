package net.tjalp.nexus.common.profile

import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class ProfileSnapshot @OptIn(ExperimentalTime::class) constructor(
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

    suspend fun update(statement: ProfilesTable.(UpsertStatement<Long>) -> Unit) =
        service.upsert(this, statement = statement)
}
