package net.tjalp.nexus.common.profile.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.tjalp.nexus.common.profile.*
import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.ExperimentalTime

class ExposedProfilesService(
    private val db: Database,
    private val moduleRegistry: ProfileModuleRegistry
) : ProfilesService {

    private val _updates = MutableSharedFlow<ProfileEvent.Updated>(replay = 0, extraBufferCapacity = 64)
    override val updates: SharedFlow<ProfileEvent.Updated> = _updates.asSharedFlow()

    private val profileCache = hashMapOf<ProfileId, ProfileSnapshot>()

    init {
        transaction(db) {
            SchemaUtils.create(ProfilesTable)
        }
    }

    override suspend fun get(
        id: ProfileId,
        cache: Boolean,
        bypassCache: Boolean,
        allowCreation: Boolean
    ): ProfileSnapshot? =
        suspendTransaction(db) {
            if (!bypassCache) profileCache[id]?.let { return@suspendTransaction it }

            var profile = ProfilesTable.selectAll().where { ProfilesTable.id eq id.value }.firstOrNull()?.toProfileSnapshot()

            // todo improve this code. This is abysmal
            if (profile == null && allowCreation) {
                ProfilesTable.upsert {
                    it[ProfilesTable.id] = id.value
                }
                val resultRow = ProfilesTable.selectAll().where { ProfilesTable.id eq id.value }.firstOrNull()
                profile = resultRow?.toProfileSnapshot()?.also { moduleRegistry.saveProfileModules(it) }
            }

            profile?.also {
                moduleRegistry.initializeProfileModules(it)
                if (cache) profileCache[id] = it
            }
        }

    @OptIn(ExperimentalTime::class)
    override suspend fun upsert(
        profile: ProfileSnapshot,
        cache: Boolean,
        statement: ProfilesTable.(UpsertStatement<Long>) -> Unit,
        vararg additionalStatements: () -> Unit
    ): ProfileSnapshot = suspendTransaction(db) {
//        moduleRegistry.saveProfileModules(profile)

        // apply main profiles upsert, allow caller to modify the upsert statement
        ProfilesTable.upsert {
            statement.invoke(ProfilesTable, it)

            it[id] = profile.id.value
            it[updatedAt] = CurrentTimestamp
        }

        // execute any additional lambdas inside the same transaction (e.g. module upserts)
        additionalStatements.forEach { it() }

        val profile = ProfilesTable.selectAll().where { ProfilesTable.id eq profile.id.value }
            .first()
            .toProfileSnapshot()
            .also {
                moduleRegistry.initializeProfileModules(it)
            }

        _updates.tryEmit(ProfileEvent.Updated(profile.id, profileCache[profile.id], profile))

        if (cache) profileCache[profile.id] = profile

        profile
    }

    override fun uncache(id: ProfileId): ProfileSnapshot? = profileCache.remove(id)

    @OptIn(ExperimentalTime::class)
    private fun ResultRow.toProfileSnapshot() = ProfileSnapshot(
        service = this@ExposedProfilesService,
        id = ProfileId(this[ProfilesTable.id]),
        lastKnownName = this[ProfilesTable.lastKnownName],
        createdAt = this[ProfilesTable.createdAt],
        updatedAt = this[ProfilesTable.updatedAt]
    )
}