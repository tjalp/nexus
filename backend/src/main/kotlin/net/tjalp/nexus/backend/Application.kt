@file:Suppress("unused")

package net.tjalp.nexus.backend

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cors.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    routing {
        get("/") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/api/parkour/records") {
            call.respond(sampleRecords)
        }

        get("/api/parkour/runs") {
            call.respond(sampleRuns)
        }
    }
}

@Serializable
data class ParkourRecord(
    val course: String,
    val player: String,
    val timeMs: Long,
    val date: String,
    val video: String? = null
)

@Serializable
data class ParkourRun(
    val id: String,
    val player: String,
    val course: String,
    val timeMs: Long,
    val status: String,
    val submittedAt: String,
    val device: String? = null,
    val notes: String? = null
)

private val sampleRecords = listOf(
    ParkourRecord("Skyline Sprint", "Nova", 42133, "2024-12-06T12:00:00Z", "https://example.com/video/skyline"),
    ParkourRecord("Helix Drop", "Iris", 38995, "2024-12-04T18:10:00Z"),
    ParkourRecord("Frostbite", "Rook", 50221, "2024-12-03T10:40:00Z"),
    ParkourRecord("Carbon Canyon", "Solace", 44777, "2024-12-02T20:15:00Z"),
    ParkourRecord("Metro Glide", "Kyto", 43120, "2024-12-01T08:45:00Z")
)

private val sampleRuns = listOf(
    ParkourRun("RUN-001", "Nova", "Skyline Sprint", 42133, "verified", "2024-12-06T12:00:00Z", "KB+M", "Clean run, no skips."),
    ParkourRun("RUN-002", "Iris", "Helix Drop", 38995, "verified", "2024-12-04T18:10:00Z", "Controller"),
    ParkourRun("RUN-003", "Rook", "Frostbite", 50221, "pending", "2024-12-03T10:40:00Z", notes = "Awaiting demo review."),
    ParkourRun("RUN-004", "Solace", "Carbon Canyon", 44777, "verified", "2024-12-02T20:15:00Z"),
    ParkourRun("RUN-005", "Kyto", "Metro Glide", 43120, "verified", "2024-12-01T08:45:00Z", "KB+M"),
    ParkourRun("RUN-006", "Astra", "Aurora Leap", 46215, "pending", "2024-12-05T13:12:00Z"),
    ParkourRun("RUN-007", "Vex", "Metro Glide", 47201, "rejected", "2024-12-05T15:35:00Z", notes = "Video missing last checkpoint."),
    ParkourRun("RUN-008", "Nova", "Helix Drop", 39501, "verified", "2024-12-06T09:02:00Z", "KB+M")
)
