package com.tmplayer.catalog

import com.tmplayer.data.MediaItem
import com.tmplayer.data.CatalogDeliveryTag
import com.tmplayer.data.MediaMapper
import com.tmplayer.data.Td
import com.tmplayer.data.valueOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageDocument

/** Resolve in the recipient account; Bot API message ids belong to the sender. */
suspend fun resolveCatalogDelivery(delivery: CatalogDelivery): MediaItem? = withTimeoutOrNull(30_000) {
    if (!CatalogDeliveryTag.valid(delivery.playbackTag) || delivery.botId <= 0) return@withTimeoutOrNull null
    val session = Td.awaitConnectedSessionOrNull(5_000) ?: return@withTimeoutOrNull null
    val client = session.client
    val username = delivery.botUsername.takeIf { it.matches(Regex("[A-Za-z0-9_]{5,32}")) }
    val chat = username?.let { client.searchPublicChat(it).valueOrNull }
        ?: client.createPrivateChat(delivery.botId, false).valueOrNull
        ?: return@withTimeoutOrNull null
    if (chat.id != delivery.botId || delivery.botId <= 0) return@withTimeoutOrNull null
    // At most six bounded reads, only this bot's recent messages. Never scan the account.
    repeat(6) {
        val history = client.getChatHistory(chat.id, 0, 0, 100, false).valueOrNull ?: return@withTimeoutOrNull null
        val messages = history.messages
        for (message in messages) {
            if (message == null) continue
            val caption = when (val content = message.content) {
                is MessageVideo -> content.caption?.text
                is MessageDocument -> content.caption?.text
                else -> null
            }
            if (CatalogDeliveryTag.matches(delivery.playbackTag, caption)) {
                // The original mapper preserves content restrictions and removes the tag
                // from the text displayed by the player.
                return@withTimeoutOrNull MediaMapper.fromMessage(message, chat.hasProtectedContent)
            }
        }
        delay(1_200)
    }
    null
}
