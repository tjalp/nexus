package net.tjalp.nexus.backend.util

import kotlinx.coroutines.runBlocking
import net.tjalp.nexus.auth.Role
import net.tjalp.nexus.auth.service.ExposedAuthService
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*

/**
 * Utility script to create admin users.
 * Usage: Run this as a main function or integrate into your setup.
 */
fun main() {
    val db = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/postgres",
        driver = "org.postgresql.Driver",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "postgres"
    )

    val authService = ExposedAuthService(db)

    // Example: Create an admin user
    runBlocking {
        try {
            // You need to provide a valid profile UUID here
            val profileId = UUID.fromString("ee903b7f-5371-44b0-bc5a-c1ef66262101") // Replace with actual profile UUID

            val admin = authService.createUser(
                profileId = profileId,
                username = "admin",
                password = "admin123", // Change this to a secure password!
                role = Role.ADMIN
            )

            println("Admin user created successfully!")
            println("Username: ${admin.username}")
            println("Role: ${admin.role}")
            println("User ID: ${admin.id}")
        } catch (e: Exception) {
            println("Failed to create admin user: ${e.message}")
            e.printStackTrace()
        }
    }
}

