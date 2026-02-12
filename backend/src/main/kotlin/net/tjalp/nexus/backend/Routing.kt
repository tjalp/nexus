package net.tjalp.nexus.backend

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import java.util.*

fun Application.configureRouting() {
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
        get("/") {
            call.respondText("Hello World!")
        }
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
    }
}