package net.tjalp.nexus.common.profile.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.tjalp.nexus.common.profile.*
import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
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
            addLogger(StdOutSqlLogger)
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
            if (!bypassCache && profileCache.contains(id)) return@suspendTransaction profileCache[id]

            var resultRow = ProfilesTable.selectAll().where { ProfilesTable.id eq id.value }.firstOrNull()

            // todo improve this code. This is abysmal
            if (resultRow == null && allowCreation) {
                ProfilesTable.upsert {
                    it[ProfilesTable.id] = id.value
                }
                resultRow = ProfilesTable.selectAll().where { ProfilesTable.id eq id.value }.firstOrNull()
            }

            val profile = resultRow?.toProfileSnapshot()?.also { moduleRegistry.initializeProfileModules(it) }

            if (cache && profile != null) profileCache[id] = profile

            profile
        }

    override suspend fun upsert(
        profile: ProfileSnapshot,
        cache: Boolean,
        statement: ProfilesTable.(UpsertStatement<Long>) -> Unit
    ): ProfileSnapshot = suspendTransaction(db) {
        moduleRegistry.saveProfileModules(profile)
        ProfilesTable.upsert {
            it[id] = profile.id.value
            statement.invoke(ProfilesTable, it)
        }

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
        createdAt = this[ProfilesTable.createdAt]
    )
}