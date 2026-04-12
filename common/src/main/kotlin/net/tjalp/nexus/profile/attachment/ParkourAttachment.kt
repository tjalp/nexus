package net.tjalp.nexus.profile.attachment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*

/**
 * Legacy table for pinned route data from the previous route-based parkour model.
 * The current parkour implementation no longer depends on this attachment and it
 * is retained only for migration/read-only compatibility until cleanup.
 */
object ParkourAttachmentTable : Table("parkour_attachments") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    val entryNodeId = uuid("entry_node_id")
    val routeKey = varchar("route_key", 64)
    val routeSequence = text("route_sequence")

    override val primaryKey = PrimaryKey(profileId, entryNodeId)
}

/**
 * Represents a single pinned route for a player: standing on [entryNodeId] will
 * automatically track the route identified by [routeKey] whose ordered segment IDs
 * are stored in [segmentIds].
 */
@Serializable
data class PinnedRoute(
    val entryNodeId: String,
    val routeKey: String,
    val routeName: String? = null,
    val segmentIds: List<String> = emptyList()
)

/**
 * Profile attachment that stores all pinned parkour routes for a player.
 * The key is the entry node UUID string; the value is the [PinnedRoute].
 */
@Serializable
@SerialName("parkour")
data class ParkourAttachment(
    val pinnedRoutes: Map<String, PinnedRoute>
) : ProfileAttachment {

    @Transient
    lateinit var id: UUID

    /**
     * Pins [pinnedRoute] for the given [entryNodeId], persisting the change.
     * Must be called within a transaction.
     */
    fun pin(entryNodeId: UUID, pinnedRoute: PinnedRoute) {
        ParkourAttachmentTable.upsert {
            it[ParkourAttachmentTable.profileId] = this@ParkourAttachment.id
            it[ParkourAttachmentTable.entryNodeId] = entryNodeId
            it[ParkourAttachmentTable.routeKey] = pinnedRoute.routeKey
            it[ParkourAttachmentTable.routeSequence] = Json.encodeToString(
                ListSerializer(String.serializer()),
                pinnedRoute.segmentIds
            )
        }
    }

    /**
     * Unpins the route for the given [entryNodeId], persisting the change.
     * Must be called within a transaction.
     */
    fun unpin(entryNodeId: UUID) {
        ParkourAttachmentTable.deleteWhere {
            (ParkourAttachmentTable.profileId eq this@ParkourAttachment.id) and
                    (ParkourAttachmentTable.entryNodeId eq entryNodeId)
        }
    }
}

object ParkourAttachmentProvider : AttachmentProvider<ParkourAttachment> {

    override suspend fun load(db: Database, id: UUID): ParkourAttachment = suspendTransaction(db) {
        val rows = ParkourAttachmentTable.selectAll()
            .where(ParkourAttachmentTable.profileId eq id)
            .toList()

        val pinnedRoutes = rows.associate { row ->
            val entryNodeId = row[ParkourAttachmentTable.entryNodeId].toString()
            val routeKey = row[ParkourAttachmentTable.routeKey]
            val segmentIds: List<String> = Json.decodeFromString(
                ListSerializer(String.serializer()),
                row[ParkourAttachmentTable.routeSequence]
            )
            entryNodeId to PinnedRoute(
                entryNodeId = entryNodeId,
                routeKey = routeKey,
                segmentIds = segmentIds
            )
        }

        ParkourAttachment(pinnedRoutes = pinnedRoutes).also { it.id = id }
    }
}

@Suppress("unused")
fun ResultRow.toParkourAttachment(id: UUID): ParkourAttachment =
    ParkourAttachment(pinnedRoutes = emptyMap()).also { it.id = id }
