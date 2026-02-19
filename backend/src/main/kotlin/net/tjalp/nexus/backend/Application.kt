package net.tjalp.nexus.backend

import com.apurebase.kgraphql.GraphQL
import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import kotlinx.datetime.TimeZone
import net.tjalp.nexus.auth.AuthService
import net.tjalp.nexus.auth.service.ExposedAuthService
import net.tjalp.nexus.backend.auth.JwtConfig
import net.tjalp.nexus.backend.schema.profileSchema
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.GeneralAttachmentProvider
import net.tjalp.nexus.profile.attachment.NoticesAttachmentProvider
import net.tjalp.nexus.profile.attachment.PunishmentAttachmentProvider
import net.tjalp.nexus.profile.service.ExposedProfilesService
import org.jetbrains.exposed.v1.jdbc.Database
import java.lang.System.getenv
import java.util.*
import kotlin.time.ExperimentalTime

val db = Database.connect(
    url = getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/nexus",
    driver = "org.postgresql.Driver",
    user = getenv("DATABASE_USER") ?: "postgres",
    password = getenv("DATABASE_PASSWORD") ?: "postgres"
)

val profiles: ProfilesService = ExposedProfilesService(db)
val authService: AuthService = ExposedAuthService(db)

fun main(args: Array<String>) {
    for (provider in listOf(GeneralAttachmentProvider, NoticesAttachmentProvider, PunishmentAttachmentProvider)) {
        AttachmentRegistry.register(provider)
    }

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

@OptIn(ExperimentalTime::class)
suspend fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    install(CallLogging)

    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.getRealm()
            verifier(
                JWT
                    .require(JwtConfig.algorithm)
                    .withAudience(JwtConfig.getAudience())
                    .withIssuer(JwtConfig.getIssuer())
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val username = credential.payload.getClaim("username").asString()
                val role = credential.payload.getClaim("role").asString()

                if (userId != null && username != null && role != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }
    }

    install(GraphQL) {
        playground = true

        schema {
            extendedScalars()

            stringScalar<UUID> {
                serialize = { it.toString() }
                deserialize = { UUID.fromString(it) }
            }

            stringScalar<Locale> {
                serialize = { it.toLanguageTag() }
                deserialize = { Locale.forLanguageTag(it) }
            }

            stringScalar<TimeZone> {
                serialize = { it.id }
                deserialize = { TimeZone.of(it) }
            }

            profileSchema(profiles, authService)
        }
    }

    configureRouting(authService)
}