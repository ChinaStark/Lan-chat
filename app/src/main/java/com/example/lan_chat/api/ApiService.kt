package com.example.lan_chat.api

import com.example.lan_chat.data.Message
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Defines the REST API endpoints for the chat service using Retrofit.
 */
interface ApiService {

    /**
     * Fetches the history of messages for a given room.
     * Note: This is no longer used, as WebSocket now provides the history.
     * It's kept here for completeness.
     */
    @GET("/api/messages")
    suspend fun getMessages(): List<Message>


    /**
     * Uploads a file to the server.
     * This is a multipart request.
     *
     * @param file The file itself, wrapped in a MultipartBody.Part.
     * @param sender The sender's name, wrapped in a RequestBody.
     * @param room The room name, wrapped in a RequestBody.
     * @return The Message object created by the server for this file upload.
     */
    @Multipart
    @POST("/api/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("sender") sender: RequestBody,
        @Part("room") room: RequestBody
        // caption is omitted for simplicity for now
    ): Message
}
