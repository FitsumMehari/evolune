package com.evolune.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.evolune.app.domain.GamificationMode
import com.evolune.app.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.evoluneDataStore by preferencesDataStore(name = "evolune_preferences")

data class UserPreferences(
    val theme: ThemeMode = ThemeMode.System,
    val haptics: Boolean = true,
    val sounds: Boolean = true,
    val animations: Boolean = true,
    val reducedMotion: Boolean = false,
    val gamification: GamificationMode = GamificationMode.Balanced,
    val notificationsEnabled: Boolean = true,
    val morningEnabled: Boolean = true,
    val morningHour: Int = 8,
    val eveningEnabled: Boolean = true,
    val eveningHour: Int = 20,
    val weeklyEnabled: Boolean = true,
    val quietStart: Int = 22,
    val quietEnd: Int = 7,
    val lastOpenDay: String = ""
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics")
        val sounds = booleanPreferencesKey("sounds")
        val animations = booleanPreferencesKey("animations")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val gamification = stringPreferencesKey("gamification")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val morningEnabled = booleanPreferencesKey("morning_enabled")
        val morningHour = intPreferencesKey("morning_hour")
        val eveningEnabled = booleanPreferencesKey("evening_enabled")
        val eveningHour = intPreferencesKey("evening_hour")
        val weeklyEnabled = booleanPreferencesKey("weekly_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val lastOpenDay = stringPreferencesKey("last_open_day")
    }

    val flow: Flow<UserPreferences> = context.evoluneDataStore.data.map { p ->
        UserPreferences(
            theme = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: ThemeMode.System.name) }.getOrDefault(ThemeMode.System),
            haptics = p[Keys.haptics] ?: true,
            sounds = p[Keys.sounds] ?: true,
            animations = p[Keys.animations] ?: true,
            reducedMotion = p[Keys.reducedMotion] ?: false,
            gamification = runCatching { GamificationMode.valueOf(p[Keys.gamification] ?: GamificationMode.Balanced.name) }.getOrDefault(GamificationMode.Balanced),
            notificationsEnabled = p[Keys.notificationsEnabled] ?: true,
            morningEnabled = p[Keys.morningEnabled] ?: true,
            morningHour = p[Keys.morningHour] ?: 8,
            eveningEnabled = p[Keys.eveningEnabled] ?: true,
            eveningHour = p[Keys.eveningHour] ?: 20,
            weeklyEnabled = p[Keys.weeklyEnabled] ?: true,
            quietStart = p[Keys.quietStart] ?: 22,
            quietEnd = p[Keys.quietEnd] ?: 7,
            lastOpenDay = p[Keys.lastOpenDay] ?: ""
        )
    }

    suspend fun current(): UserPreferences = flow.first()

    suspend fun setTheme(value: ThemeMode) = edit { it[Keys.theme] = value.name }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.haptics] = value }
    suspend fun setSounds(value: Boolean) = edit { it[Keys.sounds] = value }
    suspend fun setAnimations(value: Boolean) = edit { it[Keys.animations] = value }
    suspend fun setReducedMotion(value: Boolean) = edit { it[Keys.reducedMotion] = value }
    suspend fun setGamification(value: GamificationMode) = edit { it[Keys.gamification] = value.name }
    suspend fun setNotificationsEnabled(value: Boolean) = edit { it[Keys.notificationsEnabled] = value }
    suspend fun setMorning(enabled: Boolean, hour: Int) = edit { it[Keys.morningEnabled] = enabled; it[Keys.morningHour] = hour.coerceIn(0, 23) }
    suspend fun setEvening(enabled: Boolean, hour: Int) = edit { it[Keys.eveningEnabled] = enabled; it[Keys.eveningHour] = hour.coerceIn(0, 23) }
    suspend fun setWeekly(value: Boolean) = edit { it[Keys.weeklyEnabled] = value }
    suspend fun setQuietHours(start: Int, end: Int) = edit { it[Keys.quietStart] = start.coerceIn(0,23); it[Keys.quietEnd] = end.coerceIn(0,23) }
    suspend fun setLastOpenDay(value: String) = edit { it[Keys.lastOpenDay] = value }

    suspend fun exportJson(): JSONObject {
        val p = current()
        return JSONObject().apply {
            put("theme", p.theme.name); put("haptics", p.haptics); put("sounds", p.sounds)
            put("animations", p.animations); put("reducedMotion", p.reducedMotion); put("gamification", p.gamification.name)
            put("notificationsEnabled", p.notificationsEnabled); put("morningEnabled", p.morningEnabled); put("morningHour", p.morningHour)
            put("eveningEnabled", p.eveningEnabled); put("eveningHour", p.eveningHour); put("weeklyEnabled", p.weeklyEnabled)
            put("quietStart", p.quietStart); put("quietEnd", p.quietEnd); put("lastOpenDay", p.lastOpenDay)
        }
    }

    suspend fun restoreJson(json: JSONObject) {
        context.evoluneDataStore.edit { p ->
            p[Keys.theme] = json.optString("theme", ThemeMode.System.name)
            p[Keys.haptics] = json.optBoolean("haptics", true); p[Keys.sounds] = json.optBoolean("sounds", true)
            p[Keys.animations] = json.optBoolean("animations", true); p[Keys.reducedMotion] = json.optBoolean("reducedMotion", false)
            p[Keys.gamification] = json.optString("gamification", GamificationMode.Balanced.name)
            p[Keys.notificationsEnabled] = json.optBoolean("notificationsEnabled", true)
            p[Keys.morningEnabled] = json.optBoolean("morningEnabled", true); p[Keys.morningHour] = json.optInt("morningHour", 8)
            p[Keys.eveningEnabled] = json.optBoolean("eveningEnabled", true); p[Keys.eveningHour] = json.optInt("eveningHour", 20)
            p[Keys.weeklyEnabled] = json.optBoolean("weeklyEnabled", true)
            p[Keys.quietStart] = json.optInt("quietStart", 22); p[Keys.quietEnd] = json.optInt("quietEnd", 7)
            p[Keys.lastOpenDay] = json.optString("lastOpenDay", "")
        }
    }

    private suspend inline fun edit(crossinline block: (MutablePreferences) -> Unit) {
        context.evoluneDataStore.edit { block(it) }
    }
}
