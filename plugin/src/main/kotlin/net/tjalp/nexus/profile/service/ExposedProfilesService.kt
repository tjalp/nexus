package net.tjalp.nexus.profile.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.tjalp.nexus.profile.ProfileEvent
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.model.ProfileEntity
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*
import kotlin.time.ExperimentalTime

class ExposedProfilesService(
    private val db: Database
) : ProfilesService {

    private val _updates = MutableSharedFlow<ProfileEvent.Updated>(replay = 0, extraBufferCapacity = 64)
    override val updates: SharedFlow<ProfileEvent.Updated> = _updates.asSharedFlow()

    private val profileCache = hashMapOf<UUID, ProfileEntity>()

    init {
        transaction(db) {
            SchemaUtils.create(ProfilesTable)
        }
    }

    override suspend fun get(
        id: UUID,
        cache: Boolean,
        bypassCache: Boolean,
        allowCreation: Boolean
    ): ProfileEntity? = suspendTransaction(db) {
        if (!bypassCache) profileCache[id]?.let { return@suspendTransaction it }

        var profile = ProfileEntity.findById(id)

        // todo improve this code. This is abysmal
        if (profile == null && allowCreation) {
            ProfilesTable.upsert {
                it[ProfilesTable.id] = id
            }
            profile = ProfileEntity.findById(id)
        }

        profile?.also {
            AttachmentRegistry.load(it)
            if (cache) profileCache[id] = it
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun upsert(
        profile: ProfileEntity,
        cache: Boolean,
        vararg statements: () -> Unit
    ): ProfileEntity = suspendTransaction(db) {
        ProfilesTable.upsert {
            it[id] = profile.id.value
            it[modifiedAt] = CurrentTimestamp
        }

        statements.forEach { it() }

        val profile = ProfileEntity[profile.id].also { AttachmentRegistry.load(it) }

        _updates.tryEmit(ProfileEvent.Updated(profile.id.value, profileCache[profile.id.value], profile))

        if (cache) profileCache[profile.id.value] = profile

        profile
    }

    override fun uncache(id: UUID): ProfileEntity? = profileCache.remove(id)
}