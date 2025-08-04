package com.murmur.app

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

object StreamSession {
    private const val PREF_NAME = "stream_prefs"
    private const val KEY_STREAM_ID = "stream_id"
    private const val KEY_DEVICE_ID = "device_id"

    fun getOrCreateStreamId(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_STREAM_ID, null)

        return if (existing != null) {
            existing
        } else {
            val newId = UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_STREAM_ID, newId).apply()
            newId
        }
    }

    fun getStreamId(context: Context): String? {
        val prefs = context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
        return prefs.getString("stream_id", null)
    }

    fun setStreamId(context: Context, id: String) {
        val prefs: SharedPreferences = context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("stream_id", id).apply()
    }

    fun setIsCreator(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_creator", value).apply()
    }

    fun getIsCreator(context: Context): Boolean {
        val prefs = context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_creator", false)
    }

    fun setCreatorId(context: Context, streamId: String) {
        val prefs = context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("creator_stream", streamId).apply()
    }

    fun isCreator(context: Context): Boolean {
        val prefs = context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_creator", false)
    }

    fun getDeviceId(context: Context): String {
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
    }
}


