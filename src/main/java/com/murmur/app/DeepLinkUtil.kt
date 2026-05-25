package com.murmur.app

import java.net.URLEncoder

object DeepLinkUtil {
    /**
     * Build the Intent URI we encode into the QR.
     * If the app is installed, Android deep-links into Murmur.
     * If not installed, Chrome uses browser_fallback_url (Play Store listing).
     *
     * @param nonce Optional cache-buster (e.g., inviteId) so each QR render differs.
     *              Our scanner ignores it because we only parse the "sid" param.
     */
    fun buildJoinQrPayload(streamId: String, nonce: String? = null): String {
        val playUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        val encodedPlay = URLEncoder.encode(playUrl, "UTF-8")
        val extra = nonce?.let { "&n=" + URLEncoder.encode(it, "UTF-8") } ?: ""

        return "intent://join?sid=$streamId$extra#Intent;" +
                "scheme=murmur;" +
                "package=${BuildConfig.APPLICATION_ID};" +
                "S.browser_fallback_url=$encodedPlay;" +
                "end"


    }
}
