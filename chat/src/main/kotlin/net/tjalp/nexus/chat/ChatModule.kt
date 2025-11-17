package net.tjalp.nexus.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.tjalp.nexus.chat.store.ChatTable
import net.tjalp.nexus.common.profile.ProfileModule
import net.tjalp.nexus.common.profile.ProfileSnapshot
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

class ChatModule(
    private val db: Database
) : ProfileModule {

    init {
        transaction(db) {
            SchemaUtils.create(ChatTable)
        }
    }

    override suspend fun onProfileLoad(profile: ProfileSnapshot) {
        val messageCount = withContext(Dispatchers.IO) {
            suspendTransaction(db) {
                ChatTable.selectAll().where { ChatTable.profileId eq profile.id.value }
                    .single()[ChatTable.messageCount]
            }
        }

        profile.setAttachment(ChatKeys.CHAT, ChatAttachment(messageCount))
    }

    override suspend fun onProfileSave(profile: ProfileSnapshot) {
        val attachment = profile.getAttachment(ChatKeys.CHAT)

        if (attachment == null) {
            withContext(Dispatchers.IO) {
                suspendTransaction(db) {
                    ChatTable.upsert { it[profileId] = profile.id.value }
                }
            }
            return
        }

        withContext(Dispatchers.IO) {
            suspendTransaction(db) {
                ChatTable.upsert {
                    it[profileId] = profile.id.value
                    it[messageCount] = attachment.messageCount
                }
            }
        }
    }
}