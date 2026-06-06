package com.murmur.app

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

private const val MIN_SEND_INTERVAL_MS = 500L

class StreamViewModel(
    private val context: Context,
    private val streamId: String
) : ViewModel() {

    private val appContext = context.applicationContext
    private val repository = StreamRepository(appContext, streamId)
    val shouldLeaveStream = MutableStateFlow(false)
    val messages: StateFlow<List<String>> = repository.messages
    val memberCount: StateFlow<Int> = repository.memberCount
    val isCreator: StateFlow<Boolean> = repository.isCreator
    val streamDeleted: StateFlow<Boolean> = repository.streamDeleted
    private var lastSendAtMs: Long = 0L

    fun sendMessage(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastSendAtMs < MIN_SEND_INTERVAL_MS) {
            return
        }
        lastSendAtMs = now
        repository.sendMessage(message)
    }

    fun nukeStream(onFinished: (Boolean, String?) -> Unit) {
        repository.nukeStream { success, error ->
            onFinished(success, error)
        }
    }

    fun leaveStream(onLeft: () -> Unit) {
        repository.leaveStream(onLeft)
        shouldLeaveStream.value = true
    }

    fun touchPresence() = repository.touchPresence()

    fun refreshStreamStatus() = repository.refreshStreamStatus()

    fun handleStreamDeleted() {
        StreamSession.clearStreamId(appContext)
    }

    override fun onCleared() {
        super.onCleared()
        repository.clear()
    }
}
