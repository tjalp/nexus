package net.tjalp.nexus.feature.notices

import net.tjalp.nexus.Feature
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.NoticesAttachmentProvider
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister

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
}