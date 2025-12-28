package com.example.lan_chat.data

import android.content.Context

/**
 * A simple utility class to manage user data using SharedPreferences.
 * This acts as the file that stores whether a user profile exists.
 */
class UserPreferences(context: Context) {
    // The file will be named "lan_chat_user_prefs" internally in the app's private storage.
    private val prefs = context.getSharedPreferences("lan_chat_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USERNAME = "username"
    }

    /**
     * Saves the user's chosen name (sender).
     */
    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    /**
     * Retrieves the saved username.
     * @return The saved username, or null if it doesn't exist.
     */
    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }
    }
}
