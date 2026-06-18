package com.stevanovicm32.mobilnoracunarstvo.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "game_session")

class TokenStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("auth_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val usernameKey = stringPreferencesKey("username")
    private val pointsKey = intPreferencesKey("total_points")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[tokenKey] }
    val pointsFlow: Flow<Int> = context.dataStore.data.map { it[pointsKey] ?: 0 }

    fun getTokenSync(): String? = runBlocking {
        context.dataStore.data.first()[tokenKey]
    }

    suspend fun saveSession(token: String, userId: String, username: String, points: Int) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[userIdKey] = userId
            prefs[usernameKey] = username
            prefs[pointsKey] = points
        }
    }

    suspend fun updatePoints(points: Int) {
        context.dataStore.edit { prefs ->
            prefs[pointsKey] = points
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun isLoggedIn(): Boolean = !getTokenSync().isNullOrBlank()
}
