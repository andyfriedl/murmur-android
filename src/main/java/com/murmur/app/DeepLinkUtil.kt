package com.murmur.app

import java.net.URLEncoder

object DeepLinkUtil {
    fun buildJoinQrPayload(
        streamId: String,
        relayKey: String? = null,
        nonce: String? = null
    ): String {
        val playUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        val encodedPlay = URLEncoder.encode(playUrl, "UTF-8")

        val relayKeyParam = relayKey?.let {
            "&rk=" + URLEncoder.encode(it, "UTF-8")
        } ?: ""

        val nonceParam = nonce?.let {
            "&n=" + URLEncoder.encode(it, "UTF-8")
        } ?: ""

        return "intent://join?sid=$streamId$relayKeyParam$nonceParam#Intent;" +
                "scheme=murmur;" +
                "package=${BuildConfig.APPLICATION_ID};" +
                "S.browser_fallback_url=$encodedPlay;" +
                "end"
    }
}