package com.murmur.app

import com.murmurrelay.core.MurmurRelay
import com.murmurrelay.core.transport.InMemoryRelayTransport
import com.murmurrelay.core.transport.RelayTransport

class MurmurRelayChatClient(
    private val channelId: String,
    private val channelKey: String,
    transport: RelayTransport
) {
    private val relay = MurmurRelay(transport)

    fun getChannelId(): String = channelId

    fun getChannelKeyLength(): Int = channelKey.length

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
    }
}