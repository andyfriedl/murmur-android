package com.murmur.app

import android.content.Context
import android.content.Context.MODE_PRIVATE

object UpgradeConfig {

    const val FREE_STREAM_MESSAGE_LIMIT = 100
    const val PRO_STREAM_MESSAGE_LIMIT = Int.MAX_VALUE   // treat as “unlimited”
    const val FREE_STREAM_MAX_INACTIVITY_DAYS = 30

    const val FREE_STREAM_MEMBER_LIMIT = 8
}


object Upgrade {
    private const val PREFS = "murmur_prefs"
    private const val KEY_PRO = "murmur_pro"

    fun isPro(context: Context): Boolean =
        context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_PRO, false)

    // For testing now; later this will be set after a real purchase
    fun setPro(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_PRO, value).apply()
    }
}
