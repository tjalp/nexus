package net.tjalp.nexus.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.tjalp.nexus.chat.store.ChatTable
import net.tjalp.nexus.common.profile.ProfileModule
import net.tjalp.nexus.common.profile.ProfileSnapshot
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

class ChatModule(
    private val db: Database
) : ProfileModule {

    override suspend fun onProfileLoad(profile: ProfileSnapshot) {
        val messageCount = withContext(Dispatchers.IO) {
            transaction(db) {
                ChatTable.select(ChatTable.profileId eq profile.id.value)
                    .single()[ChatTable.messageCount]
            }
        }

        profile.setAttachment(ChatKeys.CHAT, ChatAttachment(messageCount))
    }

    override suspend fun onProfileSave(profile: ProfileSnapshot) {
        val attachment = profile.getAttachment(ChatKeys.CHAT) ?: return

        withContext(Dispatchers.IO) {
            transaction(db) {
                ChatTable.upsert {
                    it[profileId] = profile.id.value
                    it[messageCount] = attachment.messageCount
                }
            }
        }
    }
}