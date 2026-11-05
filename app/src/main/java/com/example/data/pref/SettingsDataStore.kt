package com.example.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bluelink_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_ASK_BEFORE_RECEIVING = booleanPreferencesKey("ask_before_receiving")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_CHUNK_SIZE_KB = intPreferencesKey("chunk_size_kb")
    }

    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_RECONNECT] ?: true
    }

    val theme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "SYSTEM"
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: true
    }

    val askBeforeReceiving: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASK_BEFORE_RECEIVING] ?: true
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val chunkSizeKb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHUNK_SIZE_KB] ?: 32
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_RECONNECT] = enabled }
    }

    suspend fun setTheme(themeStr: String) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME] = themeStr }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAskBeforeReceiving(ask: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ASK_BEFORE_RECEIVING] = ask }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setChunkSizeKb(sizeKb: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_CHUNK_SIZE_KB] = sizeKb }
    }
}
