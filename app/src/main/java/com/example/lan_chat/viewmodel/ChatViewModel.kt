package com.example.lan_chat.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lan_chat.data.Message
import com.example.lan_chat.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ChatViewModel"

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    val messages: StateFlow<List<Message>> = repository.messages

    val isConnected: StateFlow<Boolean> = repository.connectionStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val error: StateFlow<String?> = isConnected
        .map { connected ->
            if (connected) null else "Disconnected. Trying to reconnect..."
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Connecting...")

    private var currentUsername: String? = null
    private var currentRoom: String? = null
    
    // --- NEW: State for the manual reconnect button ---
    private val _showManualReconnectButton = MutableStateFlow(false)
    val showManualReconnectButton: StateFlow<Boolean> = _showManualReconnectButton.asStateFlow()

    private var reconnectWaiterJob: Job? = null

    init {
        // This will ensure that if connection drops while app is in foreground,
        // it will also trigger the reconnect process.
        viewModelScope.launch {
            isConnected.collect { connected ->
                if (!connected) {
                    onAppForegrounded() // Re-trigger the logic if we disconnect spontaneously
                }
            }
        }
    }

    fun onAppForegrounded() {
        Log.d(TAG, "[Butler] Event: onAppForegrounded")
        // If we are already connected or a job is running, do nothing.
        if (isConnected.value || reconnectWaiterJob?.isActive == true) {
            Log.d(TAG, "[Butler] Already connected or a job is running. No action needed.")
            return
        }

        reconnectWaiterJob = viewModelScope.launch {
            Log.i(TAG, "[Butler] Starting 2-minute reconnect attempt window.")
            _showManualReconnectButton.value = false // Hide button at the start of any attempt

            // This job will persistently try to reconnect every 5 seconds.
            val attemptJob = launch {
                while (isActive) {
                    reconnect()
                    delay(5000) // Wait 5 seconds between attempts
                }
            }

            // We wait for a maximum of 2 minutes for the isConnected state to become true.
            val success = withTimeoutOrNull(120_000L) { // 2 minutes in milliseconds
                isConnected.first { it } // This will suspend until isConnected is true
            }

            // Once the timeout is over or we succeed, we must stop the attempts.
            attemptJob.cancel()

            if (success == null) {
                Log.e(TAG, "[Butler] Reconnect timed out after 2 minutes. Showing manual reconnect button.")
                _showManualReconnectButton.value = true
            } else {
                Log.i(TAG, "[Butler] Successfully reconnected within the 2-minute window.")
                _showManualReconnectButton.value = false
            }
        }
    }

    fun onAppBackgrounded() {
        Log.d(TAG, "[Butler] Event: onAppBackgrounded. Cancelling any active reconnect job.")
        reconnectWaiterJob?.cancel()
        _showManualReconnectButton.value = false
    }

    // --- NEW: Public function for the UI button to call ---
    fun manualReconnect() {
        Log.d(TAG, "[Butler] Manual reconnect triggered by user.")
        // Simply re-run the same logic as if the app just came to the foreground.
        onAppForegrounded()
    }

    fun connect(username: String, room: String = "main") {
        Log.i(TAG, "Initial connect() called for username: $username in room: $room")
        this.currentUsername = username
        this.currentRoom = room
        repository.connect(username, room, clearHistory = true)
    }

    private fun reconnect() {
        val username = currentUsername
        val room = currentRoom
        if (username == null || room == null) {
            Log.e(TAG, "Cannot reconnect: user or room is not set.")
            return
        }
        Log.i(TAG, "reconnect() attempt for username: $username in room: $room")
        repository.connect(username, room, clearHistory = false)
    }

    fun sendMessage(content: String) {
        repository.sendTextMessage(content)
    }

    fun uploadFile(context: Context, uri: Uri) {
        val username = currentUsername
        val room = currentRoom
        if (username == null || room == null) {
            Log.e(TAG, "Cannot upload file: user or room is not set.")
            return
        }
        viewModelScope.launch {
            try {
                repository.uploadFile(context, uri, username, room)
            } catch (e: Exception) {
                Log.e(TAG, "File upload failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}

class ChatViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(ChatRepository()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
