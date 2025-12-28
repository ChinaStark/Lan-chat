package com.example.lan_chat.data

import com.google.gson.annotations.SerializedName

/**
 * Represents a single message object, directly mapping the structure from the API.
 */
data class Message(
    @SerializedName("id")
    val id: Long,

    @SerializedName("room")
    val room: String,

    @SerializedName("sender")
    val sender: String,

    @SerializedName("type")
    val type: MessageType, // Using an enum for type safety

    @SerializedName("content")
    val content: String? = null,

    @SerializedName("file")
    val attachment: Attachment? = null,

    @SerializedName("created_at")
    val createdAt: String // ISO 8601 string
)

/**
 * Enum for the different types of messages.
 * The @SerializedName annotation ensures that Gson correctly maps the JSON string to the enum constant.
 */
enum class MessageType {
    @SerializedName("text")
    TEXT,

    @SerializedName("image")
    IMAGE,

    @SerializedName("file")
    FILE
}

/**
 * Represents the file attachment in a message.
 */
data class Attachment(
    @SerializedName("url")
    val url: String,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("mime")
    val mime: String? = null,

    @SerializedName("size")
    val size: Long? = null
)

/**
 * Data class for the response from the GET /api/messages endpoint.
 */
data class MessageHistoryResponse(
    @SerializedName("room")
    val room: String,

    @SerializedName("count")
    val count: Int,

    @SerializedName("messages")
    val messages: List<Message>
)

/**
 * Data class for the request body when sending a text message.
 */
data class SendTextRequestBody(
    @SerializedName("sender")
    val sender: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("room")
    val room: String = "main"
)
