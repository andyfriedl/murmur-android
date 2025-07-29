package com.murmur.app

import android.content.Context

object UiPrefs {
    private const val PREF_NAME = "murmur_ui_prefs"
    private const val KEY_RED_DOT_DISMISSED = "red_dot_dismissed"

    fun shouldShowRedDot(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_RED_DOT_DISMISSED, false)
    }

    fun markRedDotDismissed(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RED_DOT_DISMISSED, true)
            .apply()
    }
}

