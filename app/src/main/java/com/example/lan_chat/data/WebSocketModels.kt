package com.example.lan_chat.data

import com.google.gson.JsonElement

/**
 * Represents a generic event received from the WebSocket server.
 * This is the outer layer of all server-sent events.
 *
 * @property event The type of the event (e.g., "history", "message", "pong").
 * @property data The actual payload of the event, which will be parsed based on the event type.
 */
data class WebSocketEvent(
    val event: String,
    val data: JsonElement
)

/**
 * Represents an outgoing text message sent from the client to the WebSocket server.
 *
 * @property type Should always be "text".
 * @property content The message content.
 */
data class WebSocketTextMessage(
    val type: String = "text",
    val content: String
)
