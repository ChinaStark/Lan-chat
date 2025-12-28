package com.example.lan_chat.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.lan_chat.api.RetrofitClient
import com.example.lan_chat.api.WebSocketManager
import com.example.lan_chat.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository {

    private val webSocketManager = WebSocketManager()
    private val apiService = RetrofitClient.apiService
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    val connectionStatus: Flow<Boolean> = webSocketManager.connectionStatus

    init {
        webSocketManager.messages
            .onEach { newMessages ->
                if (newMessages.size == 1 && _messages.value.isNotEmpty()) {
                    _messages.value += newMessages
                } else {
                    _messages.value = newMessages
                }
            }
            .launchIn(repositoryScope)
    }

    // --- MODIFIED FUNCTION ---
    fun connect(username: String, room: String, clearHistory: Boolean) {
        // Force clear the message cache on every new connection attempt
        // to ensure fresh data is loaded.
        if (clearHistory) {
            _messages.value = emptyList()
        }
        // Also reset the replay cache in the WebSocketManager to avoid emitting stale history.
        (webSocketManager.messages as? kotlinx.coroutines.flow.MutableSharedFlow)?.resetReplayCache()
        
        webSocketManager.connect(username, room)
    }
    // --- END MODIFICATION ---

    fun disconnect() {
        webSocketManager.disconnect()
    }

    fun sendTextMessage(content: String) {
        webSocketManager.sendTextMessage(content)
    }

    suspend fun uploadFile(context: Context, uri: Uri, sender: String, room: String) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val fileBytes = inputStream.readBytes()
            var fileName = "unknown_file"
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            val requestFile = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestFile)
            val senderPart = sender.toRequestBody("text/plain".toMediaTypeOrNull())
            val roomPart = room.toRequestBody("text/plain".toMediaTypeOrNull())

            apiService.uploadFile(filePart, senderPart, roomPart)
        }
    }
}
