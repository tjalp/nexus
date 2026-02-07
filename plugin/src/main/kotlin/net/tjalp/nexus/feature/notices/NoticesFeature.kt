package net.tjalp.nexus.feature.notices

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.tjalp.nexus.Feature
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.NoticesAttachmentProvider
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.minutes

object NoticesFeature : Feature("notices") {

    private var listener: NoticesListener? = null

    override fun enable() {
        super.enable()

        AttachmentRegistry.register(NoticesAttachmentProvider)
        listener = NoticesListener().also { it.register() }
    }

    override fun disable() {
        listener?.unregister()
        listener = null
        AttachmentRegistry.unregister(NoticesAttachmentProvider)

        super.disable()
    }

    /**
     * Shows the recommendations dialog to the given audience, and returns whether they accepted it or not.
     *
     * @param audience The audience to show the dialog to.
     * @return Whether the audience saw the recommendations or not.
     */
    suspend fun showAndAwaitRecommendations(audience: Audience): Boolean {
        val seen = CompletableDeferred<Boolean>()
        val locale = audience.get(Identity.LOCALE).getOrNull() ?: Locale.US

        audience.showDialog(RecommendationsDialog.create(locale) { accepted ->
            seen.complete(accepted)
        })

        try {
            return withTimeoutOrNull(5.minutes) { seen.await() } ?: false
        } finally {
            if (seen.isCancelled) {
                audience.closeDialog()
            }
        }
    }

    /**
     * Shows the rules dialog to the given audience, and returns whether they accepted it or not.
     *
     * @param audience The audience to show the dialog to.
     * @return Whether the audience accepted the rules or not.
     */
    suspend fun showAndAwaitRules(audience: Audience): Boolean {
        val acceptedDeferred = CompletableDeferred<Boolean>()
        val locale = audience.get(Identity.LOCALE).getOrNull() ?: Locale.US

        audience.showDialog(RulesDialog.create(locale) { accepted ->
            acceptedDeferred.complete(accepted)
        })

        try {
            return withTimeoutOrNull(5.minutes) { acceptedDeferred.await() } ?: false
        } finally {
            if (acceptedDeferred.isCancelled) {
                audience.closeDialog()
            }
        }
    }
}