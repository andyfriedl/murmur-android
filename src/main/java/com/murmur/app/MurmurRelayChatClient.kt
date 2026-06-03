package com.murmur.app

import com.murmurrelay.core.MurmurRelay
import com.murmurrelay.core.MurmurRelayResult
import com.murmurrelay.core.transport.RelayTransport

class MurmurRelayChatClient(
    private val channelId: String,
    private val channelKey: String,
    transport: RelayTransport
) {
    private val relay = MurmurRelay(transport)

    fun sendMessage(
        message: String,
        onComplete: (Boolean) -> Unit
    ) {
        relay.send(
            channelId = channelId,
            channelKey = channelKey,
            payload = message
        ) { result ->
            when (result) {
                is MurmurRelayResult.Success -> {
                    onComplete(true)
                }

                is MurmurRelayResult.Error -> {
                    android.util.Log.e("MurmurRelay", "Relay client send failed: ${result.message}")
                    onComplete(false)
                }
            }
        }
    }

    fun observeMessages(
        onMessage: (String) -> Unit
    ) {
        relay.observe(channelId, channelKey) { message ->
            onMessage(message.payload)
        }
    }
}