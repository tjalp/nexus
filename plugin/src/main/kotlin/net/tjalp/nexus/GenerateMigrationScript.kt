@file:OptIn(ExperimentalDatabaseMigrationApi::class)

package net.tjalp.nexus

import net.tjalp.nexus.profile.attachment.EffortShopTable
import net.tjalp.nexus.profile.attachment.GeneralTable
import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

val h2db = Database.connect(
    url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
    driver = "org.h2.Driver",
    user = "root",
    password = ""
)

fun main() {
    transaction(h2db) {
        generateMigrationScript()
    }
}

fun generateMigrationScript() {
    // Generate the migration script
    MigrationUtils.generateMigrationScript(
        ProfilesTable,
        GeneralTable,
        EffortShopTable,
        scriptDirectory = "src/main/resources/db/migration",
        scriptName = "V1__initial_setup"
    )
}