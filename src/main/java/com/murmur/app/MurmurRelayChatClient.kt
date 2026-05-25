package com.murmur.app

import com.murmurrelay.core.MurmurRelay

class MurmurRelayChatClient(
    private val channelId: String,
    private val channelKey: String
) {
    fun getChannelId(): String = channelId

    fun getChannelKeyLength(): Int = channelKey.length

    companion object {
        fun createForChannel(channelId: String): MurmurRelayChatClient {
            return MurmurRelayChatClient(
                channelId = channelId,
                channelKey = MurmurRelay.createChannelKey()
            )
        }
    }
}