package net.tjalp.nexus.parkour

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.tjalp.nexus.profile.attachment.ProfileAttachment
import net.tjalp.nexus.profile.attachment.AttachmentProvider
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.*

/**
 * Table that stores pinned parkour routes per player profile.
 * Each row represents one pinned route: the entrypoint node ID and the route
 * key + sequence for that route.
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
 * automatically track the route identified by [routeKey] whose ordered node IDs
 * are stored in [nodeIds].
 */
@Serializable
data class PinnedRoute(
    val entryNodeId: String,
    val routeKey: String,
    val nodeIds: List<String>
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
            it[ParkourAttachmentTable.routeSequence] = Json.encodeToString(pinnedRoute.nodeIds)
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
            val nodeIds: List<String> = Json.decodeFromString(row[ParkourAttachmentTable.routeSequence])
            entryNodeId to PinnedRoute(
                entryNodeId = entryNodeId,
                routeKey = routeKey,
                nodeIds = nodeIds
            )
        }

        ParkourAttachment(pinnedRoutes = pinnedRoutes).also { it.id = id }
    }
}

@Suppress("unused")
fun ResultRow.toParkourAttachment(id: UUID): ParkourAttachment =
    ParkourAttachment(pinnedRoutes = emptyMap()).also { it.id = id }
