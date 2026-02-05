@file:OptIn(ExperimentalDatabaseMigrationApi::class)

package net.tjalp.nexus

import net.tjalp.nexus.profile.attachment.EffortShopTable
import net.tjalp.nexus.profile.attachment.GeneralTable
import net.tjalp.nexus.profile.attachment.NoticesTable
import net.tjalp.nexus.profile.attachment.PunishmentsTable
import net.tjalp.nexus.profile.model.ProfilesTable
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

val db = Database.connect(
    url = "jdbc:postgresql://localhost:5432/postgres",
    driver = "org.postgresql.Driver",
    user = "postgres",
    password = "postgres"
)

fun main() {
    val flyway = Flyway.configure()
        .dataSource(db.url, "postgres", "postgres")
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load()

    flyway.migrate()

    transaction(db) {
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
        NoticesTable,
        scriptDirectory = "src/main/resources/db/migration",
        scriptName = "V4__add_time_zone_to_general_attachment"
    )
}