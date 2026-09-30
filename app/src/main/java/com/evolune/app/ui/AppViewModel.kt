package com.evolune.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evolune.app.EvoluneApplication
import com.evolune.app.data.BackupManager
import com.evolune.app.data.UserPreferences
import com.evolune.app.domain.*
import com.evolune.app.notifications.NotificationChannels
import com.evolune.app.notifications.NotificationScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as EvoluneApplication
    private val repository = app.repository
    private val backup = BackupManager(app, repository)

    private val _snapshot = MutableStateFlow<AppSnapshot?>(null)
    val snapshot: StateFlow<AppSnapshot?> = _snapshot.asStateFlow()
    val preferences: StateFlow<UserPreferences> = repository.preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch { _snapshot.value = repository.snapshot() }

    fun createCharacter(name: String, alias: String, presentation: Presentation, chapter: LifeChapter, domains: List<LifeDomain>) = viewModelScope.launch {
        repository.createCharacter(name, alias, presentation, chapter, domains)
        scheduleConfiguredNotifications(preferences.value)
        _snapshot.value = repository.snapshot()
    }

    fun addQuest(quest: Quest) = viewModelScope.launch {
        val id = repository.addQuest(quest)
        quest.reminderAt?.let { NotificationScheduler.scheduleQuest(app, id, quest.title, it) }
        _snapshot.value = repository.snapshot(); _messages.tryEmit("Quest added")
    }

    fun completeQuest(id: Long) = viewModelScope.launch {
        val result = repository.completeQuest(id)
        _snapshot.value = repository.snapshot()
        if (result.applied) _messages.tryEmit(if (result.levelUp) "Level up · +${result.xp} XP" else "+${result.xp} XP · +${result.coins} Coins")
    }

    fun addRitual(ritual: Ritual) = viewModelScope.launch {
        repository.addRitual(ritual); _snapshot.value = repository.snapshot(); _messages.tryEmit("Ritual created")
    }

    fun completeRitual(id: Long, stage: String = "target") = viewModelScope.launch {
        val result = repository.completeRitual(id, stage); _snapshot.value = repository.snapshot()
        if (result.applied) _messages.tryEmit("Ritual advanced · +${result.xp} XP") else _messages.tryEmit("Already completed today")
    }

    fun addSkill(domainId: Long, name: String) = viewModelScope.launch {
        repository.addSkill(domainId, name); _snapshot.value = repository.snapshot()
    }

    fun updateSkill(id: Long, name: String) = viewModelScope.launch {
        repository.updateSkill(id, name); _snapshot.value = repository.snapshot()
    }

    fun archiveSkill(id: Long, archived: Boolean) = viewModelScope.launch {
        repository.archiveSkill(id, archived); _snapshot.value = repository.snapshot()
    }

    fun addReward(title: String, cost: Int) = viewModelScope.launch {
        repository.addReward(title, cost); _snapshot.value = repository.snapshot()
    }

    fun purchaseReward(id: Long, redeemNow: Boolean) = viewModelScope.launch {
        val success = repository.purchaseReward(id, redeemNow); _snapshot.value = repository.snapshot()
        _messages.tryEmit(if (success) "Reward unlocked" else "Not enough Coins")
    }

    fun purchaseCosmetic(key: String, title: String, costEssence: Int) = viewModelScope.launch {
        val success = repository.purchaseCosmetic(key, title, costEssence); _snapshot.value = repository.snapshot()
        _messages.tryEmit(if (success) "Cosmetic unlocked" else "Not enough Essence")
    }

    fun recordFocus(questId: Long?, minutes: Int, completed: Boolean) = viewModelScope.launch {
        repository.recordFocus(questId, minutes, completed); _snapshot.value = repository.snapshot()
    }

    fun saveCheckIn(energy: Energy, mood: Int, note: String, capacity: Energy) = viewModelScope.launch {
        repository.saveCheckIn(energy, mood, note, capacity); _messages.tryEmit("Check-in saved")
    }

    fun updateDomain(domain: LifeDomain) = viewModelScope.launch { repository.updateDomain(domain); _snapshot.value = repository.snapshot() }
    fun addDomain(name: String) = viewModelScope.launch { repository.addDomain(name); _snapshot.value = repository.snapshot() }
    fun moveDomain(id: Long, direction: Int) = viewModelScope.launch { repository.moveDomain(id, direction); _snapshot.value = repository.snapshot() }
    fun equipTitle(title: String) = viewModelScope.launch { repository.equipTitle(title); _snapshot.value = repository.snapshot() }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.preferences.setTheme(mode) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { repository.preferences.setHaptics(v) }
    fun setSounds(v: Boolean) = viewModelScope.launch { repository.preferences.setSounds(v) }
    fun setAnimations(v: Boolean) = viewModelScope.launch { repository.preferences.setAnimations(v) }
    fun setReducedMotion(v: Boolean) = viewModelScope.launch { repository.preferences.setReducedMotion(v) }
    fun setGamification(v: GamificationMode) = viewModelScope.launch { repository.preferences.setGamification(v) }
    fun setNotifications(v: Boolean) = viewModelScope.launch {
        repository.preferences.setNotificationsEnabled(v)
        if (v) scheduleConfiguredNotifications(preferences.value.copy(notificationsEnabled = true))
        else {
            NotificationScheduler.cancelDaily(app, "morning_briefing")
            NotificationScheduler.cancelDaily(app, "evening_review")
            NotificationScheduler.cancelWeekly(app)
        }
    }
    fun setMorning(enabled: Boolean, hour: Int) = viewModelScope.launch {
        repository.preferences.setMorning(enabled, hour)
        if (enabled) NotificationScheduler.scheduleDaily(app, "morning_briefing", hour, "Morning Briefing", "Choose what advances your character today.", NotificationChannels.DAILY)
        else NotificationScheduler.cancelDaily(app, "morning_briefing")
    }
    fun setEvening(enabled: Boolean, hour: Int) = viewModelScope.launch {
        repository.preferences.setEvening(enabled, hour)
        if (enabled) NotificationScheduler.scheduleDaily(app, "evening_review", hour, "Daily Advancement", "Did you move forward today?", NotificationChannels.REVIEWS)
        else NotificationScheduler.cancelDaily(app, "evening_review")
    }

    fun setWeekly(enabled: Boolean) = viewModelScope.launch {
        repository.preferences.setWeekly(enabled)
        if (enabled) NotificationScheduler.scheduleWeekly(app) else NotificationScheduler.cancelWeekly(app)
    }

    fun exportBackup(uri: Uri, password: String) = viewModelScope.launch {
        runCatching { backup.export(uri, password) }.onSuccess { _messages.tryEmit("Backup exported") }.onFailure { _messages.tryEmit(it.message ?: "Backup export failed") }
    }

    fun restoreBackup(uri: Uri, password: String) = viewModelScope.launch {
        runCatching { backup.restore(uri, password); repository.snapshot() }
            .onSuccess { _snapshot.value = it; _messages.tryEmit("Backup restored") }
            .onFailure { _messages.tryEmit(it.message ?: "Restore failed") }
    }

    fun resetAll() = viewModelScope.launch { repository.resetAll(); _snapshot.value = repository.snapshot(); _messages.tryEmit("Local data reset") }

    fun markOpened() = viewModelScope.launch { repository.preferences.setLastOpenDay(LocalDate.now().toString()) }

    private fun scheduleConfiguredNotifications(p: UserPreferences) {
        if (!p.notificationsEnabled) return
        if (p.morningEnabled) NotificationScheduler.scheduleDaily(app, "morning_briefing", p.morningHour, "Morning Briefing", "Choose what advances your character today.", NotificationChannels.DAILY)
        if (p.eveningEnabled) NotificationScheduler.scheduleDaily(app, "evening_review", p.eveningHour, "Daily Advancement", "Did you move forward today?", NotificationChannels.REVIEWS)
        if (p.weeklyEnabled) NotificationScheduler.scheduleWeekly(app)
    }
}
