package com.example.zhidao.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SessionManager(private val context: Context) {
    companion object {
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val SESSION_ID_KEY = stringPreferencesKey("session_id")
    }

    val baseUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[BASE_URL_KEY]
    }

    val sessionId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SESSION_ID_KEY]
    }

    suspend fun setBaseUrl(url: String?) {
        context.dataStore.edit { preferences ->
            if (url == null) {
                preferences.remove(BASE_URL_KEY)
            } else {
                preferences[BASE_URL_KEY] = url
            }
        }
    }

    suspend fun setSessionId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id == null) {
                preferences.remove(SESSION_ID_KEY)
            } else {
                preferences[SESSION_ID_KEY] = id
            }
        }
    }
}
