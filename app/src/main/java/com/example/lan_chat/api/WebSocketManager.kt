package com.example.lan_chat.api

import android.util.Log
import com.example.lan_chat.data.Message
import com.example.lan_chat.data.WebSocketEvent
import com.example.lan_chat.data.WebSocketTextMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

private const val TAG = "WebSocketManager"

class WebSocketManager {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var webSocket: WebSocket? = null
    private val gson = Gson()

    private val _messages = MutableSharedFlow<List<Message>>(replay = 1)
    val messages: SharedFlow<List<Message>> = _messages

    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus

    private val socketListener = object : WebSocketListener() {
        private fun getSocketId(webSocket: WebSocket) = System.identityHashCode(webSocket)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            Log.i(TAG, "[DEBUG] onOpen triggered for socket ${getSocketId(webSocket)}. Setting connection status to TRUE.")
            _connectionStatus.value = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            // Log.d(TAG, "Received message: $text")
            handleIncomingMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            Log.i(TAG, "[DEBUG] onClosing triggered for socket ${getSocketId(webSocket)}: $code / $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            Log.i(TAG, "[DEBUG] onClosed triggered for socket ${getSocketId(webSocket)}: $code / $reason")
            val currentSocketId = System.identityHashCode(this@WebSocketManager.webSocket)
            Log.d(TAG, "[DEBUG] Current active socket is ${currentSocketId}. Comparing with closed socket ${getSocketId(webSocket)}.")
            if (this@WebSocketManager.webSocket === webSocket) {
                Log.w(TAG, "[DEBUG] The closed socket IS the active one. Setting connection status to FALSE and clearing socket reference.")
                _connectionStatus.value = false
                this@WebSocketManager.webSocket = null
            } else {
                Log.w(TAG, "[DEBUG] The closed socket is NOT the active one. Ignoring.")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            Log.e(TAG, "[DEBUG] onFailure triggered for socket ${getSocketId(webSocket)}: ${t.message}", t)
            val currentSocketId = System.identityHashCode(this@WebSocketManager.webSocket)
            Log.d(TAG, "[DEBUG] Current active socket is ${currentSocketId}. Comparing with failed socket ${getSocketId(webSocket)}.")
            if (this@WebSocketManager.webSocket === webSocket) {
                Log.e(TAG, "[DEBUG] The failed socket IS the active one. Setting connection status to FALSE and clearing socket reference.")
                _connectionStatus.value = false
                this@WebSocketManager.webSocket = null
            } else {
                Log.e(TAG, "[DEBUG] The failed socket is NOT the active one. Ignoring.")
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val event = gson.fromJson(text, WebSocketEvent::class.java)
            when (event.event) {
                "history" -> {
                    val historyType = object : TypeToken<List<Message>>() {}.type
                    val historyMessages: List<Message> = gson.fromJson(event.data, historyType)
                    // Log.i(TAG, "Received history with ${historyMessages.size} messages.")
                    _messages.tryEmit(historyMessages)
                }
                "message" -> {
                    val message = gson.fromJson(event.data, Message::class.java)
                    // Log.i(TAG, "Received new message: ${message.id}")
                    _messages.tryEmit(listOf(message))
                }
                "pong" -> Log.d(TAG, "Received pong")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: $text", e)
        }
    }

    fun connect(username: String, room: String) {
        val currentSocketId = System.identityHashCode(webSocket)
        Log.i(TAG, "[DEBUG] connect() called. Current socket is ${currentSocketId}.")
        
        webSocket?.close(1001, "Client-initiated reconnect")
        if (webSocket != null) {
             Log.d(TAG, "[DEBUG] Existing socket ${currentSocketId} was found and close() was called on it.")
        }

        val url = "ws://172.31.72.156:8000/ws/chat?username=$username&room=$room"
        Log.i(TAG, "[DEBUG] Attempting to establish a new connection to: $url")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, socketListener)
        Log.i(TAG, "[DEBUG] newWebSocket() called. New socket instance is ${System.identityHashCode(webSocket)}.")
    }

    fun sendTextMessage(content: String) {
        val message = WebSocketTextMessage(content = content)
        val jsonMessage = gson.toJson(message)
        val sent = webSocket?.send(jsonMessage)
        if (sent == false) {
            Log.w(TAG, "Failed to send message; WebSocket may be closed.")
        }
    }

    fun disconnect() {
        val currentSocketId = System.identityHashCode(webSocket)
        Log.i(TAG, "[DEBUG] disconnect() called. Closing socket ${currentSocketId}.")
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionStatus.value = false
    }
}
