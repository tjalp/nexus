package net.tjalp.nexus.backend

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import net.tjalp.nexus.auth.AuthService
import net.tjalp.nexus.backend.auth.authRoutes
import net.tjalp.nexus.profile.attachment.PunishmentAttachment
import java.util.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Application.configureRouting(authService: AuthService) {
    routing {
        swaggerUI("/swagger") {
            info = OpenApiInfo("Nexus API", "1.0.0")
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }
        openAPI(path = "openapi") {
            info = OpenApiInfo("Nexus API", "1.0.0")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }
        // TODO proper routing and endpoints
        get("/") {
            call.respondText("Hello World!")
        }

        // Authentication routes
        authRoutes(authService)

        /**
         * Get a single profile by its ID.
         *
         * Path: id [UUID] the ID of the profile
         *
         * Responses:
         *  - 200 The profile was found and returned successfully.
         *  - 400 The ID parameter is missing or invalid.
         *  - 404 No profile was found with the given ID.
         */
        get("/profile/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id parameter")
            val uniqueId = try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(HttpStatusCode.BadRequest, "Invalid id parameter")
            }
            val profile = profiles.get(uniqueId) ?: return@get call.respond(HttpStatusCode.NotFound, "Profile not found")

            call.respond(profile)
        }

        get("/punishments/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id parameter")
            val uniqueId = try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(HttpStatusCode.BadRequest, "Invalid id parameter")
            }
            val punishments = profiles.get(uniqueId)?.attachmentOf<PunishmentAttachment>()?.punishments ?: emptyList()

            call.respond(punishments)
        }
    }
}