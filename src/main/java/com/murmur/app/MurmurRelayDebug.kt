package com.murmur.app

import com.murmurrelay.core.MurmurRelay

object MurmurRelayDebug {
    fun createTestKeyLength(): Int {
        return MurmurRelay.createChannelKey().length
    }
}