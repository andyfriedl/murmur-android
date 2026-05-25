package com.murmur.app

class MurmurRelayChatClient(
    private val channelId: String,
    private val channelKey: String
) {
    fun getChannelId(): String = channelId

    fun getChannelKeyLength(): Int = channelKey.length
}