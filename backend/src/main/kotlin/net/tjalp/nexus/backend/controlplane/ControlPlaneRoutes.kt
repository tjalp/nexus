package net.tjalp.nexus.backend.controlplane

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.tjalp.nexus.controlplane.ControlActionRequest

fun Route.controlPlaneRoutes(service: ControlPlaneService) {
    route("/control-plane") {
        get("/providers") {
            call.respond(service.listProviders())
        }

        get("/stacks") {
            call.respond(service.listStacks())
        }

        get("/stacks/{stackId}/servers") {
            val stackId = call.parameters["stackId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing stackId parameter"))

            call.respond(service.listServers(stackId))
        }

        get("/servers") {
            call.respond(service.listAllServers())
        }

        get("/servers/{serverId}") {
            val serverId = call.parameters["serverId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing serverId parameter"))

            val server = service.getServer(serverId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Server not found"))

            call.respond(server)
        }

        get("/servers/{serverId}/players") {
            val serverId = call.parameters["serverId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing serverId parameter"))

            val server = service.getServer(serverId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Server not found"))

            call.respond(service.listPlayers(server.id))
        }

        post("/servers/{serverId}/actions") {
            val serverId = call.parameters["serverId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing serverId parameter"))

            val request = try {
                call.receive<ControlActionRequest>()
            } catch (_: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            }

            val server = service.getServer(serverId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Server not found"))

            val result = service.performAction(server.id, request)
            call.respond(if (result.accepted) HttpStatusCode.Accepted else HttpStatusCode.BadRequest, result)
        }

        webSocket("/ws") {
            val json = Json { ignoreUnknownKeys = true }
            var subscriptionJob: Job? = null

            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val request = try {
                    json.decodeFromString<ControlPlaneWsRequest>(text)
                } catch (_: Exception) {
                    sendSerialized(mapOf("type" to "error", "message" to "Invalid websocket payload"))
                    continue
                }

                when (request.type) {
                    "subscribe" -> {
                        val serverId = request.serverId
                        if (serverId.isNullOrBlank()) {
                            sendSerialized(mapOf("type" to "error", "message" to "serverId is required"))
                            continue
                        }

                        if (service.getServer(serverId) == null) {
                            sendSerialized(mapOf("type" to "error", "message" to "Unknown server"))
                            continue
                        }

                        subscriptionJob?.cancel()
                        subscriptionJob = launch {
                            service.subscribe(serverId).collectLatest { event ->
                                sendSerialized(event)
                            }
                        }

                        sendSerialized(mapOf("type" to "subscribed", "serverId" to serverId))
                    }

                    "ping" -> sendSerialized(mapOf("type" to "pong"))
                    else -> sendSerialized(mapOf("type" to "error", "message" to "Unknown request type"))
                }
            }

            subscriptionJob?.cancel()
        }
    }
}

