package net.tjalp.nexus.profile

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.model.ProfileSnapshot

/**
 * Updates the profile with the provided statement.
 *
 * @param statement A lambda function containing the update logic.
 * @return The updated [ProfileSnapshot].
 */
suspend fun ProfileSnapshot.update(statement: () -> Unit) = update(*arrayOf(statement))

/**
 * Updates the profile with the provided statements.
 *
 * @param statements A vararg of lambda functions containing the update logic.
 * @return The updated [ProfileSnapshot].
 */
suspend fun ProfileSnapshot.update(vararg statements: () -> Unit) = update(NexusPlugin.profiles, *statements)

/**
 * Updates a specific attachment of the profile using the provided statement.
 *
 * @param key The key of the attachment to update.
 * @param statement A lambda function containing the update logic for the attachment.
 * @return The updated [ProfileSnapshot].
 * @throws IllegalStateException if the attachment is not loaded for the profile.
 */
suspend inline fun <reified T> ProfileSnapshot.update(noinline statement: (T) -> Unit) =
    update(*arrayOf(statement))

/**
 * Updates a specific attachment of the profile using the provided statements.
 *
 * @param key The key of the attachment to update.
 * @param statements A vararg of lambda functions containing the update logic for the attachment.
 * @return The updated [ProfileSnapshot].
 * @throws IllegalStateException if the attachment is not loaded for the profile.
 */
suspend inline fun <reified T> ProfileSnapshot.update(vararg statements: (T) -> Unit): ProfileSnapshot {
    val att = attachmentOf<T>() ?: error("Attachment ${T::class} not loaded for profile $id")
    val actualStatements = statements.map { stmt -> { stmt(att) } }.toTypedArray()

    return update(*actualStatements)
}