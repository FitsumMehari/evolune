package com.evolune.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evolune.app.data.UserPreferences
import com.evolune.app.domain.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun ProgressScreen(snapshot: AppSnapshot, vm: AppViewModel, onSettings: () -> Unit) {
    val profile = snapshot.profile ?: return
    var addSkill by rememberSaveable { mutableStateOf(false) }
    var weeklyReview by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PROGRESS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { weeklyReview = true }) { Text("Weekly review") }
                TextButton(onClick = onSettings) { Text("Settings") }
            }
            CharacterHeader(profile)
        }
        item {
            val completed = snapshot.quests.count { it.completedAt != null }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Lifetime XP", profile.xp.toString())
                Metric("Quests", completed.toString())
                Metric("Focus", formatMinutes(snapshot.focusMinutes))
                Metric("Momentum", profile.momentum.toString())
            }
        }
        item { SectionTitle("Life Domains", "Broad areas of development") }
        items(snapshot.domains.filter { it.enabled }, key = { "domain_${it.id}" }) { domain -> DomainProgressRow(domain) }
        item { SectionTitle("Skills", "Specific capabilities inside your domains", action = { TextButton(onClick = { addSkill = true }) { Text("+ Add") } }) }
        if (snapshot.skills.filterNot { it.archived }.isEmpty()) {
            item { EmptyState("No skills yet", "Add a skill when you want more specific progression.", "Add skill") { addSkill = true } }
        } else {
            items(snapshot.skills.filterNot { it.archived }, key = { "skill_${it.id}" }) { skill ->
                val domain = snapshot.domains.firstOrNull { it.id == skill.domainId }?.name.orEmpty()
                ListItem(
                    headlineContent = { Text(skill.name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("$domain · Level ${GameRules.levelFromXp(skill.xp)} · ${skill.xp} XP") },
                    trailingContent = { TextButton(onClick = { vm.archiveSkill(skill.id, true) }) { Text("Archive") } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        }
        item { SectionTitle("Journey", "Milestones, main quests and campaigns") }
        val journey = snapshot.quests.filter { it.type == QuestType.MainQuest || it.type == QuestType.Campaign || it.type == QuestType.Boss }.takeLast(8)
        if (journey.isEmpty()) item { EmptyState("Your path is open", "Main Quests and Campaigns will form a visible journey here.") }
        else items(journey, key = { "journey_${it.id}" }) { quest -> JourneyNode(quest) }
        item { SectionTitle("Achievements", "Meaningful progress, never random loot") }
        items(snapshot.achievements, key = { "achievement_${it.key}" }) { achievement ->
            val unlocked = achievement.unlockedAt != null
            ListItem(
                headlineContent = { Text(achievement.name, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(achievement.description) },
                leadingContent = { Text(if (unlocked) "◆" else "◇", color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
                trailingContent = { Text(if (unlocked) achievement.rarity else "Locked", style = MaterialTheme.typography.labelMedium) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
        item { SectionTitle("Timeline", "Your private history") }
        if (snapshot.timeline.isEmpty()) item { EmptyState("No milestones yet", "Important moments will appear here over time.") }
        else items(snapshot.timeline.take(12), key = { "timeline_${it.id}" }) { event ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(event.title, fontWeight = FontWeight.Medium)
                    if (event.detail.isNotBlank()) Text(event.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatDate(event.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (addSkill) AddSkillDialog(snapshot, onDismiss = { addSkill = false }) { domainId, name ->
        vm.addSkill(domainId, name); addSkill = false
    }
    if (weeklyReview) WeeklyReviewDialog(snapshot, onDismiss = { weeklyReview = false })
}

@Composable
private fun DomainProgressRow(domain: LifeDomain) {
    val progress = GameRules.progressWithinLevel(domain.xp)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(GameRules.levelFromXp(domain.xp).toString(), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth()) {
                Text(domain.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text("${domain.xp} XP", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun JourneyNode(quest: Quest) {
    val done = quest.completedAt != null
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (quest.type == QuestType.Boss) "♛" else if (done) "●" else "○", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(quest.title, fontWeight = FontWeight.Medium)
            Text("${quest.type.label} · ${if (done) "Completed" else quest.difficulty.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RewardsScreen(snapshot: AppSnapshot, vm: AppViewModel, onSettings: () -> Unit) {
    val profile = snapshot.profile ?: return
    var addReward by rememberSaveable { mutableStateOf(false) }
    val cosmetics = listOf(
        Triple("sigil_aurora", "Aurora Sigil", 6),
        Triple("sigil_zenith", "Zenith Sigil", 12),
        Triple("sigil_orbit", "Orbit Sigil", 20)
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rewards", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("Earned through real effort", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSettings) { Text("Settings") }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Coins", profile.coins.toString())
                Metric("Essence", profile.essence.toString())
                Metric("Redeemed", snapshot.rewards.sumOf { it.redeemedCount }.toString())
            }
        }
        item { SectionTitle("Real-world rewards", "You decide what feels rewarding", action = { FilledTonalButton(onClick = { addReward = true }) { Text("+ Add") } }) }
        if (snapshot.rewards.isEmpty()) item { EmptyState("Create your reward shop", "Coins are a permission system for self-defined rewards, not money.", "Add reward") { addReward = true } }
        else items(snapshot.rewards, key = { "reward_${it.id}" }) { reward ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(reward.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("${reward.costCoins} Coins · ${reward.pendingCount} pending · ${reward.redeemedCount} redeemed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("◈ ${reward.costCoins}", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.purchaseReward(reward.id, true) }, enabled = profile.coins >= reward.costCoins) { Text("Redeem now") }
                        OutlinedButton(onClick = { vm.purchaseReward(reward.id, false) }, enabled = profile.coins >= reward.costCoins) { Text("Save for later") }
                    }
                }
            }
        }
        item { SectionTitle("Essence cosmetics", "Digital progression only; no loot boxes") }
        items(cosmetics, key = { it.first }) { cosmetic ->
            val unlocked = snapshot.cosmetics.any { it.key == cosmetic.first }
            ListItem(
                headlineContent = { Text(cosmetic.second, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(if (unlocked) "Unlocked" else "A visual emblem variant") },
                leadingContent = { EvoluneEmblem(Modifier.size(44.dp)) },
                trailingContent = {
                    if (unlocked) Text("Owned")
                    else TextButton(onClick = { vm.purchaseCosmetic(cosmetic.first, cosmetic.second, cosmetic.third) }, enabled = profile.essence >= cosmetic.third) { Text("${cosmetic.third} Essence") }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (addReward) AddRewardDialog(onDismiss = { addReward = false }) { title, cost -> vm.addReward(title, cost); addReward = false }
}

@Composable
fun SettingsScreen(snapshot: AppSnapshot, prefs: UserPreferences, vm: AppViewModel, onBack: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var deleteConfirm by rememberSaveable { mutableStateOf(false) }
    var manageDomains by rememberSaveable { mutableStateOf(false) }
    var manageSkills by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let { vm.exportBackup(it, password) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.restoreBackup(it, password) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back") }
                Spacer(Modifier.width(8.dp))
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        item { SettingsHeader("Appearance") }
        item { EnumSetting("Theme", prefs.theme, ThemeMode.entries) { vm.setTheme(it) } }
        item { EnumSetting("Game presentation", prefs.gamification, GamificationMode.entries) { vm.setGamification(it) } }
        item { SwitchSetting("Animations", "Progress and completion motion", prefs.animations, vm::setAnimations) }
        item { SwitchSetting("Reduced motion", "Prefer restrained animation", prefs.reducedMotion, vm::setReducedMotion) }
        item { SettingsHeader("Feedback") }
        item { SwitchSetting("Haptics", "Tactile completion feedback", prefs.haptics, vm::setHaptics) }
        item { SwitchSetting("Sound effects", "Local-only game feedback", prefs.sounds, vm::setSounds) }
        item { SettingsHeader("Notifications") }
        item { SwitchSetting("Master notifications", "All Evolune reminders", prefs.notificationsEnabled, vm::setNotifications) }
        item { NotificationTimeSetting("Morning briefing", prefs.morningEnabled, prefs.morningHour) { enabled, hour -> vm.setMorning(enabled, hour) } }
        item { NotificationTimeSetting("Evening review", prefs.eveningEnabled, prefs.eveningHour) { enabled, hour -> vm.setEvening(enabled, hour) } }
        item { SwitchSetting("Weekly review", "Sunday reflection reminder", prefs.weeklyEnabled, vm::setWeekly) }
        item { SettingsHeader("Life") }
        item {
            SettingsAction("Manage domains", "Enable, disable, rename, reorder, or add domains") { manageDomains = true }
            SettingsAction("Manage skills", "Add, rename, or archive specific skills") { manageSkills = true }
        }
        item { SettingsHeader("Data") }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128) },
                label = { Text("Backup password (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text("Leave blank for an unencrypted backup. A password uses AES-GCM encryption.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { exportLauncher.launch("Evolune-backup.evolune") }) { Text("Export backup") }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }) { Text("Restore") }
            }
        }
        item {
            SettingsAction("Reset all local data", "Permanently clears this device's Evolune history") { deleteConfirm = true }
        }
        item { SettingsHeader("About") }
        item {
            Text("Evolune 1.0.0", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Your life stays on your device.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text("Evolune has no account, remote analytics, ads, cloud sync, or INTERNET permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Android 10+ (minSdk 29)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Spacer(Modifier.height(30.dp)) }
    }

    if (deleteConfirm) AlertDialog(
        onDismissRequest = { deleteConfirm = false },
        title = { Text("Reset Evolune?") },
        text = { Text("This deletes all local character history on this device. Export a backup first if you want to keep it.") },
        confirmButton = { Button(onClick = { vm.resetAll(); deleteConfirm = false }) { Text("Delete all data") } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } }
    )
    if (manageDomains) DomainManagerDialog(snapshot, vm, onDismiss = { manageDomains = false })
    if (manageSkills) SkillManagerDialog(snapshot, vm, onDismiss = { manageSkills = false })
}

@Composable
private fun SettingsHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun <T : Enum<T>> EnumSetting(title: String, selected: T, options: List<T>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, fontWeight = FontWeight.Medium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.name) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { onSelected(option); expanded = false }) }
            }
        }
    }
}

@Composable
private fun NotificationTimeSetting(title: String, enabled: Boolean, hour: Int, onChange: (Boolean, Int) -> Unit) {
    var hourText by remember(hour) { mutableStateOf(hour.toString()) }
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text("${hour.toString().padStart(2, '0')}:00 local time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        OutlinedTextField(value = hourText, onValueChange = { text -> hourText = text.filter(Char::isDigit).take(2); hourText.toIntOrNull()?.let { onChange(enabled, it.coerceIn(0, 23)) } }, modifier = Modifier.width(76.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Spacer(Modifier.width(8.dp)); Switch(checked = enabled, onCheckedChange = { onChange(it, hourText.toIntOrNull()?.coerceIn(0,23) ?: hour) })
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun FocusModeScreen(quest: Quest, reducedMotion: Boolean, onFinish: (Int, Boolean) -> Unit, onClose: () -> Unit) {
    var running by rememberSaveable { mutableStateOf(true) }
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            elapsedSeconds++
        }
    }
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) { Text("Cancel") }
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EvoluneEmblem(Modifier.size(90.dp), progress = ((elapsedSeconds % 1500) / 1500f).coerceIn(0f, 1f))
            Spacer(Modifier.height(26.dp))
            Text(quest.type.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp)); Text(quest.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(30.dp)); Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(18.dp)); LinearProgressIndicator(progress = { ((elapsedSeconds % 1500) / 1500f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { running = !running }) { Text(if (running) "Pause" else "Resume") }
                Button(onClick = { onFinish(max(1, (elapsedSeconds + 59) / 60), true) }) { Text("Finish focus") }
            }
            if (reducedMotion) { Spacer(Modifier.height(16.dp)); Text("Reduced motion is enabled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun AddQuestDialog(snapshot: AppSnapshot, defaultType: QuestType, onDismiss: () -> Unit, onSave: (Quest) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var subtasks by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(defaultType) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.Standard) }
    var domainId by rememberSaveable { mutableLongStateOf(snapshot.domains.firstOrNull { it.enabled }?.id ?: 0L) }
    var skillId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dueDate by rememberSaveable { mutableStateOf("") }
    var dueTime by rememberSaveable { mutableStateOf("") }
    var recurrence by rememberSaveable { mutableStateOf("none") }
    var typeExpanded by remember { mutableStateOf(false) }
    var difficultyExpanded by remember { mutableStateOf(false) }
    var domainExpanded by remember { mutableStateOf(false) }
    var skillExpanded by remember { mutableStateOf(false) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    val dueAt = parseDue(dueDate, dueTime)
    val activeDomains = snapshot.domains.filter { it.enabled }
    val matchingSkills = snapshot.skills.filter { !it.archived && it.domainId == domainId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create quest") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); OutlinedTextField(description, { description = it.take(280) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceField("Type", type.label, Modifier.weight(1f), typeExpanded, { typeExpanded = it }) {
                        QuestType.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { type = option; typeExpanded = false }) }
                    }
                    ChoiceField("Difficulty", difficulty.name, Modifier.weight(1f), difficultyExpanded, { difficultyExpanded = it }) {
                        Difficulty.entries.forEach { option -> DropdownMenuItem(text = { Text("${option.name} · ${option.xp} XP") }, onClick = { difficulty = option; difficultyExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ChoiceField("Domain", activeDomains.firstOrNull { it.id == domainId }?.name ?: "Choose", Modifier.fillMaxWidth(), domainExpanded, { domainExpanded = it }) {
                    activeDomains.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { domainId = d.id; skillId = null; domainExpanded = false }) }
                }
                Spacer(Modifier.height(8.dp))
                ChoiceField("Skill (optional)", matchingSkills.firstOrNull { it.id == skillId }?.name ?: "None", Modifier.fillMaxWidth(), skillExpanded, { skillExpanded = it }) {
                    DropdownMenuItem(text = { Text("None") }, onClick = { skillId = null; skillExpanded = false })
                    matchingSkills.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { skillId = s.id; skillExpanded = false }) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(dueDate, { dueDate = it.take(10) }, label = { Text("Due YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(dueTime, { dueTime = it.take(5) }, label = { Text("HH:MM") }, modifier = Modifier.width(105.dp), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                ChoiceField("Recurrence", recurrence, Modifier.fillMaxWidth(), recurrenceExpanded, { recurrenceExpanded = it }) {
                    listOf("none", "daily", "weekdays", "weekly", "monthly").forEach { option -> DropdownMenuItem(text = { Text(option.replaceFirstChar(Char::uppercase)) }, onClick = { recurrence = option; recurrenceExpanded = false }) }
                }
                Spacer(Modifier.height(8.dp)); OutlinedTextField(subtasks, { subtasks = it.take(500) }, label = { Text("Subtasks (one per line)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); OutlinedTextField(notes, { notes = it.take(600) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                if (dueDate.isNotBlank() && dueAt == null) Text("Use a valid date and optional 24-hour time.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(Quest(title = title.trim(), description = description.trim(), type = type, domainId = domainId, skillId = skillId, difficulty = difficulty, dueAt = dueAt, recurrence = recurrence, notes = notes.trim(), rewardXp = difficulty.xp, rewardCoins = GameRules.coinReward(difficulty), subtasks = subtasks.trim(), reminderAt = dueAt))
                },
                enabled = title.isNotBlank() && domainId != 0L && (dueDate.isBlank() || dueAt != null)
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddRitualDialog(snapshot: AppSnapshot, onDismiss: () -> Unit, onSave: (Ritual) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var domainId by rememberSaveable { mutableLongStateOf(snapshot.domains.firstOrNull { it.enabled }?.id ?: 0L) }
    var skillId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recurrence by rememberSaveable { mutableStateOf("daily") }
    var minimum by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var stretch by rememberSaveable { mutableStateOf("") }
    var domainExpanded by remember { mutableStateOf(false) }
    var skillExpanded by remember { mutableStateOf(false) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    val activeDomains = snapshot.domains.filter { it.enabled }
    val matchingSkills = snapshot.skills.filter { !it.archived && it.domainId == domainId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create ritual") },
        text = {
            Column {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Ritual") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); ChoiceField("Domain", activeDomains.firstOrNull { it.id == domainId }?.name ?: "Choose", Modifier.fillMaxWidth(), domainExpanded, { domainExpanded = it }) {
                    activeDomains.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { domainId = d.id; skillId = null; domainExpanded = false }) }
                }
                Spacer(Modifier.height(8.dp)); ChoiceField("Skill (optional)", matchingSkills.firstOrNull { it.id == skillId }?.name ?: "None", Modifier.fillMaxWidth(), skillExpanded, { skillExpanded = it }) {
                    DropdownMenuItem(text = { Text("None") }, onClick = { skillId = null; skillExpanded = false })
                    matchingSkills.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { skillId = s.id; skillExpanded = false }) }
                }
                Spacer(Modifier.height(8.dp)); ChoiceField("Recurrence", recurrence, Modifier.fillMaxWidth(), recurrenceExpanded, { recurrenceExpanded = it }) {
                    listOf("daily", "weekdays", "weekly", "interval:2d", "custom").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { recurrence = option; recurrenceExpanded = false }) }
                }
                Spacer(Modifier.height(8.dp)); OutlinedTextField(minimum, { minimum = it.take(60) }, label = { Text("Minimum (e.g. 10 min)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); OutlinedTextField(target, { target = it.take(60) }, label = { Text("Target (e.g. 30 min)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); OutlinedTextField(stretch, { stretch = it.take(60) }, label = { Text("Stretch (e.g. 60 min)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(Ritual(title = title.trim(), domainId = domainId, skillId = skillId, recurrence = recurrence, minimum = minimum.trim(), target = target.trim(), stretch = stretch.trim())) }, enabled = title.isNotBlank() && domainId != 0L) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CheckInDialog(onDismiss: () -> Unit, onSave: (Energy, Int, String, Energy) -> Unit) {
    var energy by rememberSaveable { mutableStateOf(Energy.Normal) }
    var capacity by rememberSaveable { mutableStateOf(Energy.Normal) }
    var mood by rememberSaveable { mutableIntStateOf(3) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily check-in") },
        text = {
            Column {
                Text("Energy", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Energy.entries.forEach { e -> FilterChip(selected = energy == e, onClick = { energy = e }, label = { Text(e.name) }) } }
                Spacer(Modifier.height(14.dp)); Text("Mood", fontWeight = FontWeight.Medium)
                Slider(value = mood.toFloat(), onValueChange = { mood = it.toInt().coerceIn(1,5) }, valueRange = 1f..5f, steps = 3)
                Text("$mood / 5", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp)); Text("Capacity today", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Energy.entries.forEach { e -> FilterChip(selected = capacity == e, onClick = { capacity = e }, label = { Text(e.name) }) } }
                Spacer(Modifier.height(10.dp)); OutlinedTextField(note, { note = it.take(300) }, label = { Text("Optional note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(energy, mood, note, capacity) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddSkillDialog(snapshot: AppSnapshot, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var domainId by rememberSaveable { mutableLongStateOf(snapshot.domains.firstOrNull { it.enabled }?.id ?: 0L) }
    var expanded by remember { mutableStateOf(false) }
    val domains = snapshot.domains.filter { it.enabled }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add skill") },
        text = { Column { OutlinedTextField(name, { name = it.take(60) }, label = { Text("Skill name") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); ChoiceField("Domain", domains.firstOrNull { it.id == domainId }?.name ?: "Choose", Modifier.fillMaxWidth(), expanded, { expanded = it }) { domains.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { domainId = d.id; expanded = false }) } } } },
        confirmButton = { Button(onClick = { onSave(domainId, name.trim()) }, enabled = name.isNotBlank() && domainId != 0L) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddRewardDialog(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("25") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add reward") },
        text = { Column { OutlinedTextField(title, { title = it.take(80) }, label = { Text("Reward") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(5) }, label = { Text("Coin cost") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { onSave(title.trim(), cost.toIntOrNull()?.coerceAtLeast(1) ?: 1) }, enabled = title.isNotBlank() && (cost.toIntOrNull() ?: 0) > 0) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WeeklyReviewDialog(snapshot: AppSnapshot, onDismiss: () -> Unit) {
    var feeling by rememberSaveable { mutableStateOf("Balanced") }
    val start = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val completed = snapshot.quests.count { (it.completedAt ?: 0L) >= start }
    val strongest = snapshot.domains.maxByOrNull { it.xp }
    val neglected = snapshot.domains.filter { it.enabled }.minByOrNull { it.xp }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly review") },
        text = {
            Column {
                Text("Last 7 days", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp)); Text("$completed quests completed")
                Text("${formatMinutes(snapshot.focusMinutes)} lifetime focus tracked")
                strongest?.let { Text("Strongest domain: ${it.name}") }
                neglected?.let { Text("Needs attention: ${it.name}") }
                snapshot.profile?.let { Text("Momentum: ${it.momentum}") }
                Spacer(Modifier.height(18.dp)); Text("How did this week feel?", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Light", "Balanced", "Heavy").forEach { option -> FilterChip(selected = feeling == option, onClick = { feeling = option }, label = { Text(option) }) } }
                Spacer(Modifier.height(10.dp)); Text("Use this reflection to shape next week's capacity. Evolune never treats recovery as failure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun DomainManagerDialog(snapshot: AppSnapshot, vm: AppViewModel, onDismiss: () -> Unit) {
    var newDomain by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Life domains") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(snapshot.domains.sortedBy { it.sortOrder }, key = { it.id }) { domain ->
                    var name by remember(domain.id, domain.name) { mutableStateOf(domain.name) }
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(name, { name = it.take(70) }, modifier = Modifier.weight(1f), singleLine = true)
                            Spacer(Modifier.width(6.dp)); Switch(checked = domain.enabled, onCheckedChange = { vm.updateDomain(domain.copy(enabled = it)) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.updateDomain(domain.copy(name = name.trim().ifBlank { domain.name })) }, enabled = name.trim() != domain.name && name.isNotBlank()) { Text("Save name") }
                            TextButton(onClick = { vm.moveDomain(domain.id, -1) }) { Text("Move up") }
                            TextButton(onClick = { vm.moveDomain(domain.id, 1) }) { Text("Move down") }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp)); OutlinedTextField(newDomain, { newDomain = it.take(70) }, label = { Text("Custom domain") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextButton(onClick = { vm.addDomain(newDomain.trim()); newDomain = "" }, enabled = newDomain.isNotBlank()) { Text("+ Add domain") }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SkillManagerDialog(snapshot: AppSnapshot, vm: AppViewModel, onDismiss: () -> Unit) {
    var addSkill by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skills") },
        text = {
            LazyColumn(Modifier.heightIn(max = 500.dp)) {
                items(snapshot.skills, key = { it.id }) { skill ->
                    var name by remember(skill.id, skill.name) { mutableStateOf(skill.name) }
                    Column(Modifier.padding(vertical = 6.dp)) {
                        OutlinedTextField(name, { name = it.take(60) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !skill.archived)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { vm.updateSkill(skill.id, name.trim()) }, enabled = !skill.archived && name.isNotBlank() && name.trim() != skill.name) { Text("Rename") }
                            TextButton(onClick = { vm.archiveSkill(skill.id, !skill.archived) }) { Text(if (skill.archived) "Restore" else "Archive") }
                        }
                    }
                }
                item { TextButton(onClick = { addSkill = true }) { Text("+ Add skill") } }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
    if (addSkill) AddSkillDialog(snapshot, onDismiss = { addSkill = false }) { domainId, name -> vm.addSkill(domainId, name); addSkill = false }
}

@Composable
private fun ChoiceField(label: String, value: String, modifier: Modifier, expanded: Boolean, setExpanded: (Boolean) -> Unit, menu: @Composable ColumnScope.() -> Unit) {
    Box(modifier) {
        OutlinedButton(onClick = { setExpanded(true) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, maxLines = 1)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }, content = menu)
    }
}

private fun parseDue(date: String, time: String): Long? {
    if (date.isBlank()) return null
    return runCatching {
        val d = LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        val t = if (time.isBlank()) LocalTime.of(23, 59) else LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern("HH:mm"))
        LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

fun isToday(epochMillis: Long): Boolean = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
fun formatDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d"))
fun dueText(epochMillis: Long?): String = epochMillis?.let { "Due ${formatDate(it)}" } ?: "No due date"
fun formatMinutes(minutes: Int): String = if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
