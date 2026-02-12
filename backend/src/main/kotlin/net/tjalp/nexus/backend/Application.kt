package net.tjalp.nexus.backend

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.GeneralAttachmentProvider
import net.tjalp.nexus.profile.attachment.NoticesAttachmentProvider
import net.tjalp.nexus.profile.attachment.PunishmentAttachmentProvider
import net.tjalp.nexus.profile.service.ExposedProfilesService
import org.jetbrains.exposed.v1.jdbc.Database
import java.lang.System.getenv

val db = Database.connect(
    url = getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/nexus",
    driver = "org.postgresql.Driver",
    user = getenv("DATABASE_USER") ?: "postgres",
    password = getenv("DATABASE_PASSWORD") ?: "postgres"
)

val profiles = ExposedProfilesService(db)

fun main(args: Array<String>) {
    for (provider in listOf(GeneralAttachmentProvider, NoticesAttachmentProvider, PunishmentAttachmentProvider)) {
        AttachmentRegistry.register(provider)
    }

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

suspend fun Application.module() {
    log.info("Hello World!")

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    install(CallLogging)

    configureRouting()
}