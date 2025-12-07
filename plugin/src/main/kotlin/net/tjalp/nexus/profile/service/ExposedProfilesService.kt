package net.tjalp.nexus.profile.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.tjalp.nexus.profile.ProfileEvent
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.profile.model.ProfilesTable
import net.tjalp.nexus.profile.model.toProfileSnapshot
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*
import kotlin.time.ExperimentalTime

class ExposedProfilesService(
    private val db: Database
) : ProfilesService {

    private val _updates = MutableSharedFlow<ProfileEvent.Updated>(replay = 0, extraBufferCapacity = 64)
    override val updates: SharedFlow<ProfileEvent.Updated> = _updates.asSharedFlow()

    private val profileCache = hashMapOf<UUID, ProfileSnapshot>()

    override suspend fun get(
        id: UUID,
        cache: Boolean,
        bypassCache: Boolean,
        allowCreation: Boolean
    ): ProfileSnapshot? = suspendTransaction(db) {
        SchemaUtils.create(ProfilesTable) // ensure schema
        if (!bypassCache) profileCache[id]?.let { return@suspendTransaction it }

        var profile = ProfilesTable.selectAll().where(ProfilesTable.id eq id).firstOrNull()?.toProfileSnapshot()

        if (profile == null && allowCreation) {
            ProfilesTable.upsert {
                it[ProfilesTable.id] = id
            }
            profile = ProfilesTable.selectAll().where(ProfilesTable.id eq id).firstOrNull()?.toProfileSnapshot()
        }

        profile?.also {
            AttachmentRegistry.load(it)
            if (cache) profileCache[id] = it
        }
    }

    override fun getCached(id: UUID): ProfileSnapshot? = profileCache[id]

    @OptIn(ExperimentalTime::class)
    override suspend fun upsert(
        profile: ProfileSnapshot,
        cache: Boolean,
        vararg statements: () -> Unit
    ): ProfileSnapshot = suspendTransaction(db) {
        ProfilesTable.upsert {
            it[id] = profile.id
            it[modifiedAt] = CurrentTimestamp
        }

        statements.forEach { it() }

        val profile = ProfilesTable.selectAll().where(ProfilesTable.id eq profile.id)
            .first().toProfileSnapshot()
            .also { AttachmentRegistry.load(it) }

        _updates.tryEmit(ProfileEvent.Updated(profile.id, profileCache[profile.id], profile))

        if (cache) profileCache[profile.id] = profile

        profile
    }

    override fun uncache(id: UUID): ProfileSnapshot? = profileCache.remove(id)
}