@file:OptIn(ExperimentalDatabaseMigrationApi::class)

package net.tjalp.nexus

import net.tjalp.nexus.profile.attachment.EffortShopTable
import net.tjalp.nexus.profile.attachment.GeneralTable
import net.tjalp.nexus.profile.attachment.PunishmentsTable
import net.tjalp.nexus.profile.model.ProfilesTable
import org.flywaydb.core.Flyway
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
    val flyway = Flyway.configure()
        .dataSource(h2db.url, "root", "")
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load()

    flyway.migrate()

    transaction(h2db) {
        generateMigrationScript()
    }
}

fun generateMigrationScript() {
    // Generate the migration script

    // ** Don't forget to adjust the version and name! **
    // ** Make sure to add new tables if you added some in the code! **
    MigrationUtils.generateMigrationScript(
        ProfilesTable,
        GeneralTable,
        EffortShopTable,
        PunishmentsTable,
        scriptDirectory = "src/main/resources/db/migration",
        scriptName = "V2__add_punishments"
    )
}