package com.murmur.app

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow

class StreamViewModel(
    private val context: Context,
    private val streamId: String
) : ViewModel() {

    private val repository = StreamRepository(context, streamId)

    val shouldLeaveStream = MutableStateFlow(false)
    val messages: StateFlow<List<String>> = repository.messages
    val memberCount: StateFlow<Int> = repository.memberCount
    val isCreator: StateFlow<Boolean> = repository.isCreator
    val streamDeleted: StateFlow<Boolean> = repository.streamDeleted

    fun sendMessage(message: String) {
        repository.sendMessage(message)
    }

    fun nukeStream(onFinished: () -> Unit) {
        repository.nukeStream {
            repository.clear()
            onFinished()
        }
    }

    fun leaveStream(onLeft: () -> Unit) {
        repository.leaveStream(onLeft)
        shouldLeaveStream.value = true
    }

    override fun onCleared() {
        super.onCleared()
        repository.clear()
    }
}

class StreamViewModelFactory(
    private val context: Context,
    private val streamId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StreamViewModel(context, streamId) as T
    }
}