package com.mine.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mineplayer_settings")

/** All persisted user settings. */
data class Settings(
    val followSystemTheme: Boolean = true, // follow system dark/light; overrides darkTheme when on
    val darkTheme: Boolean = false,
    val accentFollowSystem: Boolean = false, // use the system dynamic color (Android 12+) as accent
    val accentColor: Int = 0xFF00F5D4.toInt(), // ARGB of the chosen accent (preset or custom)
    val defaultPreset: Int = 0,     // 0 silk, 1 vinyl, 2 wallpaper
    val defaultLyricMode: Int = 0,  // 0 single, 1 full, 2 off
    val sensitivity: Float = 9f,    // audio reactivity
    val minDurationSec: Int = 10,   // hide tracks shorter than this
    val outputSampleRate: Int = 0,  // 0 = follow source, else forced Hz
    val immersiveMode: Boolean = false, // hide status bar app-wide
    val exclusiveFocus: Boolean = true, // request audio focus (don't mix with other apps)
    val replayGain: Boolean = false,    // normalize volume via ReplayGain tags
    val fadeInOut: Boolean = false,     // fade volume on play/pause
    val gapless: Boolean = false,       // trim inter-track silence for seamless albums
    val useMediaStore: Boolean = true,  // true = whole media library; false = only custom folders
    val customFolders: Set<String> = emptySet(),  // whitelist folders (used when media library off)
    val blockedFolders: Set<String> = emptySet(), // folders always excluded from the library
    val showNotification: Boolean = true, // show the media / lock-screen playback notification
    val showCloseButton: Boolean = false, // add a close button to the media notification
    val sortKey: Int = 0,           // library sort column (0 title, 1 artist, 2 duration, 3 date)
    val sortAscending: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val followSystem = booleanPreferencesKey("follow_system_theme")
        val dark = booleanPreferencesKey("dark_theme")
        val accentFollowSystem = booleanPreferencesKey("accent_follow_system")
        val accentColor = intPreferencesKey("accent_color")
        val preset = intPreferencesKey("default_preset")
        val lyric = intPreferencesKey("default_lyric_mode")
        val sens = floatPreferencesKey("sensitivity")
        val minDur = intPreferencesKey("min_duration_sec")
        val sampleRate = intPreferencesKey("output_sample_rate")
        val immersive = booleanPreferencesKey("immersive_mode")
        val exclusiveFocus = booleanPreferencesKey("exclusive_focus")
        val replayGain = booleanPreferencesKey("replay_gain")
        val fadeInOut = booleanPreferencesKey("fade_in_out")
        val gapless = booleanPreferencesKey("gapless")
        val useMediaStore = booleanPreferencesKey("use_media_store")
        val customFolders = stringSetPreferencesKey("custom_folders")
        val blockedFolders = stringSetPreferencesKey("blocked_folders")
        val showNotification = booleanPreferencesKey("show_notification")
        val showCloseButton = booleanPreferencesKey("show_close_button")
        val sortKey = intPreferencesKey("library_sort_key")
        val sortAscending = booleanPreferencesKey("library_sort_asc")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            followSystemTheme = p[Keys.followSystem] ?: true,
            darkTheme = p[Keys.dark] ?: false,
            accentFollowSystem = p[Keys.accentFollowSystem] ?: false,
            accentColor = p[Keys.accentColor] ?: 0xFF00F5D4.toInt(),
            defaultPreset = p[Keys.preset] ?: 0,
            defaultLyricMode = p[Keys.lyric] ?: 0,
            sensitivity = p[Keys.sens] ?: 9f,
            minDurationSec = p[Keys.minDur] ?: 10,
            outputSampleRate = p[Keys.sampleRate] ?: 0,
            immersiveMode = p[Keys.immersive] ?: false,
            exclusiveFocus = p[Keys.exclusiveFocus] ?: true,
            replayGain = p[Keys.replayGain] ?: false,
            fadeInOut = p[Keys.fadeInOut] ?: false,
            gapless = p[Keys.gapless] ?: false,
            useMediaStore = p[Keys.useMediaStore] ?: true,
            customFolders = p[Keys.customFolders] ?: emptySet(),
            blockedFolders = p[Keys.blockedFolders] ?: emptySet(),
            showNotification = p[Keys.showNotification] ?: true,
            showCloseButton = p[Keys.showCloseButton] ?: false,
            sortKey = p[Keys.sortKey] ?: 0,
            sortAscending = p[Keys.sortAscending] ?: true,
        )
    }

    suspend fun setFollowSystemTheme(v: Boolean) = context.dataStore.edit { it[Keys.followSystem] = v }.let {}
    suspend fun setDark(v: Boolean) = context.dataStore.edit { it[Keys.dark] = v }.let {}
    suspend fun setAccentFollowSystem(v: Boolean) = context.dataStore.edit { it[Keys.accentFollowSystem] = v }.let {}
    suspend fun setAccentColor(v: Int) = context.dataStore.edit { it[Keys.accentColor] = v }.let {}
    suspend fun setDefaultPreset(v: Int) = context.dataStore.edit { it[Keys.preset] = v }.let {}
    suspend fun setDefaultLyricMode(v: Int) = context.dataStore.edit { it[Keys.lyric] = v }.let {}
    suspend fun setSensitivity(v: Float) = context.dataStore.edit { it[Keys.sens] = v }.let {}
    suspend fun setMinDuration(v: Int) = context.dataStore.edit { it[Keys.minDur] = v }.let {}
    suspend fun setOutputSampleRate(v: Int) = context.dataStore.edit { it[Keys.sampleRate] = v }.let {}
    suspend fun setImmersiveMode(v: Boolean) = context.dataStore.edit { it[Keys.immersive] = v }.let {}
    suspend fun setExclusiveFocus(v: Boolean) = context.dataStore.edit { it[Keys.exclusiveFocus] = v }.let {}
    suspend fun setReplayGain(v: Boolean) = context.dataStore.edit { it[Keys.replayGain] = v }.let {}
    suspend fun setFadeInOut(v: Boolean) = context.dataStore.edit { it[Keys.fadeInOut] = v }.let {}
    suspend fun setGapless(v: Boolean) = context.dataStore.edit { it[Keys.gapless] = v }.let {}
    suspend fun setUseMediaStore(v: Boolean) = context.dataStore.edit { it[Keys.useMediaStore] = v }.let {}

    suspend fun addCustomFolder(path: String) = context.dataStore.edit {
        it[Keys.customFolders] = (it[Keys.customFolders] ?: emptySet()) + path
    }.let {}

    suspend fun removeCustomFolder(path: String) = context.dataStore.edit {
        it[Keys.customFolders] = (it[Keys.customFolders] ?: emptySet()) - path
    }.let {}

    suspend fun addBlockedFolder(path: String) = context.dataStore.edit {
        it[Keys.blockedFolders] = (it[Keys.blockedFolders] ?: emptySet()) + path
    }.let {}

    suspend fun removeBlockedFolder(path: String) = context.dataStore.edit {
        it[Keys.blockedFolders] = (it[Keys.blockedFolders] ?: emptySet()) - path
    }.let {}

    suspend fun setShowNotification(v: Boolean) = context.dataStore.edit { it[Keys.showNotification] = v }.let {}
    suspend fun setShowCloseButton(v: Boolean) = context.dataStore.edit { it[Keys.showCloseButton] = v }.let {}

    suspend fun setSort(key: Int, ascending: Boolean) = context.dataStore.edit {
        it[Keys.sortKey] = key
        it[Keys.sortAscending] = ascending
    }.let {}
}
