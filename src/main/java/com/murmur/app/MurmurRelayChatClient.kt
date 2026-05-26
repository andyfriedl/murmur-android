package com.murmur.app

import com.murmurrelay.core.MurmurRelay
import com.murmurrelay.core.transport.InMemoryRelayTransport
import com.murmurrelay.core.transport.RelayTransport
import com.google.firebase.database.DatabaseReference
import com.murmurrelay.core.MurmurRelayResult

class MurmurRelayChatClient(
    private val channelId: String,
    private val channelKey: String,
    transport: RelayTransport
) {
    private val relay = MurmurRelay(transport)

    fun getChannelId(): String = channelId

    fun getChannelKeyLength(): Int = channelKey.length

    fun sendMessage(
        message: String,
        onComplete: (Boolean) -> Unit
    ) {
        relay.send(
            channelId = channelId,
            channelKey = channelKey,
            payload = message
        ) { result ->
            onComplete(result is MurmurRelayResult.Success)
        }
    }

    fun observeMessages(
        onMessage: (String) -> Unit
    ) {
        relay.observe(channelId, channelKey) { message ->
            onMessage(message.payload)
        }
    }

    fun runLocalEchoTest(onResult: (String) -> Unit) {
        relay.observe(channelId, channelKey) { message ->
            onResult(message.payload)
        }

        relay.send(
            channelId = channelId,
            channelKey = channelKey,
            payload = "MurmurRelay local test"
        ) { result ->
            // No-op for now. We only care that observe receives the decrypted payload.
        }
    }

    companion object {
        fun createForChannel(channelId: String): MurmurRelayChatClient {
            return MurmurRelayChatClient(
                channelId = channelId,
                channelKey = MurmurRelay.createChannelKey(),
                transport = InMemoryRelayTransport()
            )
        }

        fun createForFirebaseChannel(
            channelId: String,
            database: DatabaseReference
        ): MurmurRelayChatClient {
            return MurmurRelayChatClient(
                channelId = channelId,
                channelKey = MurmurRelay.createChannelKey(),
                transport = FirebaseMurmurRelayTransport(database)
            )
        }
    }
}