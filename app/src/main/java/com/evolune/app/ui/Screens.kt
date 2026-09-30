package com.evolune.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.evolune.app.data.EvoluneRepository
import com.evolune.app.domain.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private enum class MainScreen(val label: String, val glyph: String) {
    Home("Home", "⌂"), Quests("Quests", "◇"), Progress("Progress", "◈"), Rewards("Rewards", "✦")
}

@Composable
fun EvoluneRoot(vm: AppViewModel) {
    val snapshot by vm.snapshot.collectAsState()
    val prefs by vm.preferences.collectAsState()
    EvoluneTheme(prefs.theme) {
        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            vm.messages.collect { snackbar.showSnackbar(it) }
        }
        LaunchedEffect(snapshot?.profile?.id) {
            if (snapshot?.profile != null && Build.VERSION.SDK_INT >= 33 && prefs.notificationsEnabled &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (snapshot?.profile != null) vm.markOpened()
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                snapshot?.profile == null -> OnboardingScreen(vm)
                else -> MainShell(snapshot!!, prefs, vm, snackbar)
            }
        }
    }
}

@Composable
private fun OnboardingScreen(vm: AppViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var presentation by rememberSaveable { mutableStateOf(Presentation.Male) }
    var name by rememberSaveable { mutableStateOf("") }
    var alias by rememberSaveable { mutableStateOf("") }
    var chapter by rememberSaveable { mutableStateOf(LifeChapter.Starting) }
    val domains = remember { mutableStateListOf<LifeDomain>().apply { addAll(EvoluneRepository.DEFAULT_DOMAINS.mapIndexed { i, n -> LifeDomain(name = n, sortOrder = i) }) } }
    var calibrationIndex by rememberSaveable { mutableIntStateOf(0) }

    val active = domains.filter { it.enabled }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
        when (step) {
            0 -> Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                EvoluneEmblem(Modifier.size(118.dp))
                Spacer(Modifier.height(28.dp)); Text("EVOLUNE", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(12.dp)); Text("Every life builds a character.\nThis one is yours.", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(44.dp)); Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("BEGIN") }
                Spacer(Modifier.height(20.dp)); Text("Your life stays on your device.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            1 -> OnboardingPage("Choose your character presentation", "This changes the visual presentation only. It never changes your goals.", onBack = { step-- }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Presentation.entries.forEach { option ->
                        val selected = presentation == option
                        Surface(
                            modifier = Modifier.weight(1f).height(150.dp).clickable(role = Role.RadioButton) { presentation = option },
                            shape = RoundedCornerShape(24.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = if (selected) 3.dp else 0.dp
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                EvoluneEmblem(Modifier.size(64.dp)); Spacer(Modifier.height(12.dp)); Text(option.name, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp)); Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("CONTINUE") }
            }
            2 -> OnboardingPage("Name your character", "Use your real name or the name you want this journey to carry.", onBack = { step-- }) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp)); OutlinedTextField(value = alias, onValueChange = { alias = it.take(40) }, label = { Text("Alias (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp)); Text("Current life chapter", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(LifeChapter.entries) { option ->
                        Row(Modifier.fillMaxWidth().clickable { chapter = option }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chapter == option, onClick = { chapter = option }); Spacer(Modifier.width(8.dp)); Text(option.label)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp)); Button(onClick = { step++ }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("CONTINUE") }
            }
            3 -> OnboardingPage("Shape your life map", "Keep what matters now. You can rename, reorder, disable, or add domains later.", onBack = { step-- }) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    domains.forEachIndexed { index, domain ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(domain.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = domain.enabled, onCheckedChange = { domains[index] = domain.copy(enabled = it) })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp)); Button(onClick = { calibrationIndex = 0; step++ }, enabled = active.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("CALIBRATE ${active.size} DOMAINS") }
            }
            else -> {
                val currentActive = domains.withIndex().filter { it.value.enabled }
                if (currentActive.isEmpty()) { step = 3; return@Box }
                val currentPair = currentActive[calibrationIndex.coerceAtMost(currentActive.lastIndex)]
                val originalIndex = currentPair.index
                val domain = domains[originalIndex]
                OnboardingPage("Life Calibration", "${calibrationIndex + 1} of ${currentActive.size} · ${domain.name}", onBack = {
                    if (calibrationIndex > 0) calibrationIndex-- else step--
                }) {
                    Text("These numbers are context, not a score of your worth.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(30.dp))
                    CalibrationSlider("Current satisfaction", domain.satisfaction) { domains[originalIndex] = domain.copy(satisfaction = it) }
                    CalibrationSlider("Consistency", domains[originalIndex].consistency) { domains[originalIndex] = domains[originalIndex].copy(consistency = it) }
                    CalibrationSlider("Improvement priority", domains[originalIndex].priority) { domains[originalIndex] = domains[originalIndex].copy(priority = it) }
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = {
                        val refreshed = domains.withIndex().filter { it.value.enabled }
                        if (calibrationIndex < refreshed.lastIndex) calibrationIndex++
                        else vm.createCharacter(name, alias, presentation, chapter, domains.toList())
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(if (calibrationIndex < currentActive.lastIndex) "NEXT DOMAIN" else "ENTER EVOLUNE")
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 12.dp, bottom = 24.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ Back") }
        Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp)); Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp)); content()
    }
}

@Composable
private fun CalibrationSlider(label: String, value: Int, onValue: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium); Text("$value / 10") }
        Slider(value = value.toFloat(), onValueChange = { onValue(it.toInt().coerceIn(1, 10)) }, valueRange = 1f..10f, steps = 8)
    }
}

@Composable
private fun MainShell(snapshot: AppSnapshot, prefs: com.evolune.app.data.UserPreferences, vm: AppViewModel, snackbar: SnackbarHostState) {
    var screen by rememberSaveable { mutableStateOf(MainScreen.Home) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var focusQuestId by rememberSaveable { mutableStateOf<Long?>(null) }
    val focusQuest = snapshot.quests.firstOrNull { it.id == focusQuestId }

    if (settingsOpen) {
        SettingsScreen(snapshot, prefs, vm, onBack = { settingsOpen = false })
        return
    }
    if (focusQuest != null) {
        FocusModeScreen(focusQuest, prefs.reducedMotion, onFinish = { minutes, completed -> vm.recordFocus(focusQuest.id, minutes, completed); focusQuestId = null }, onClose = { focusQuestId = null })
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(12.dp)); EvoluneEmblem(Modifier.size(44.dp).padding(5.dp)); Spacer(Modifier.height(18.dp))
                    MainScreen.entries.forEach { item -> NavigationRailItem(selected = screen == item, onClick = { screen = item }, icon = { Text(item.glyph) }, label = { Text(item.label) }) }
                    Spacer(Modifier.weight(1f)); NavigationRailItem(selected = false, onClick = { settingsOpen = true }, icon = { Text("⚙") }, label = { Text("Settings") })
                }
                Box(Modifier.weight(1f)) { MainDestination(screen, snapshot, prefs, vm, { focusQuestId = it }, { settingsOpen = true }, snackbar) }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar {
                        MainScreen.entries.forEach { item -> NavigationBarItem(selected = screen == item, onClick = { screen = item }, icon = { Text(item.glyph) }, label = { Text(item.label) }) }
                    }
                }
            ) { padding -> Box(Modifier.padding(padding)) { MainDestination(screen, snapshot, prefs, vm, { focusQuestId = it }, { settingsOpen = true }, snackbar) } }
        }
    }
}

@Composable
private fun MainDestination(screen: MainScreen, snapshot: AppSnapshot, prefs: com.evolune.app.data.UserPreferences, vm: AppViewModel, onFocus: (Long) -> Unit, onSettings: () -> Unit, snackbar: SnackbarHostState) {
    when (screen) {
        MainScreen.Home -> HomeScreen(snapshot, prefs, vm, onFocus, onSettings)
        MainScreen.Quests -> QuestsScreen(snapshot, vm, onFocus, onSettings)
        MainScreen.Progress -> ProgressScreen(snapshot, vm, onSettings)
        MainScreen.Rewards -> RewardsScreen(snapshot, vm, onSettings)
    }
}

@Composable
private fun HomeScreen(snapshot: AppSnapshot, prefs: com.evolune.app.data.UserPreferences, vm: AppViewModel, onFocus: (Long) -> Unit, onSettings: () -> Unit) {
    val profile = snapshot.profile ?: return
    var addQuest by rememberSaveable { mutableStateOf(false) }
    var checkIn by rememberSaveable { mutableStateOf(false) }
    var capacity by rememberSaveable { mutableStateOf(Energy.Normal) }
    val priorOpen = prefs.lastOpenDay
    var showRecovery by rememberSaveable(priorOpen) {
        mutableStateOf(runCatching { priorOpen.isNotBlank() && java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(priorOpen), LocalDate.now()) >= 3 }.getOrDefault(false))
    }
    val missions = snapshot.quests.filter { it.type == QuestType.DailyMission && it.completedAt == null }
    val completedToday = snapshot.quests.count { it.completedAt?.let(::isToday) == true }
    val currentMain = snapshot.quests.firstOrNull { it.type == QuestType.MainQuest && it.completedAt == null }
    val activeRituals = snapshot.rituals.filter { !it.archived }
    val ritualDoneToday = activeRituals.count { it.lastCompletedDay == LocalDate.now().toString() }
    val corePlanned = minOf(3, missions.size + snapshot.quests.count { it.type == QuestType.DailyMission && it.completedAt?.let(::isToday) == true })
    val coreDone = minOf(corePlanned, completedToday)
    val advancement = GameRules.dailyAdvancement(coreDone, corePlanned, ritualDoneToday, activeRituals.size, snapshot.focusMinutes, false)

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("TODAY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { checkIn = true }) { Text("Check-in") }
                TextButton(onClick = onSettings) { Text("Settings") }
            }
            CharacterHeader(profile)
        }
        if (showRecovery) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(20.dp)) {
                    Text("Welcome back.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp)); Text("Your permanent progression is intact. Choose how you want today to feel.")
                    Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { showRecovery = false }, label = { Text("Resume") })
                        AssistChip(onClick = { capacity = Energy.Low; showRecovery = false }, label = { Text("Replan week") })
                        AssistChip(onClick = { showRecovery = false }, label = { Text("Fresh today") })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Capacity", style = MaterialTheme.typography.labelLarge)
                Energy.entries.forEach { e -> FilterChip(selected = capacity == e, onClick = { capacity = e }, label = { Text(e.name) }) }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Daily Advancement", style = MaterialTheme.typography.titleMedium); Text("Did I move forward today?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("$advancement", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { advancement / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Momentum", profile.momentum.toString()); Metric("Coins", profile.coins.toString()); Metric("Essence", profile.essence.toString()); Metric("Focus", formatMinutes(snapshot.focusMinutes))
            }
        }
        currentMain?.let { quest ->
            item {
                SectionTitle("Main Quest", "Your current long-horizon objective")
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp, color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp)) {
                        Text(quest.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (quest.description.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(quest.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onFocus(quest.id) }) { Text("Focus") }
                            Button(onClick = { vm.completeQuest(quest.id) }) { Text("Complete · +${quest.rewardXp} XP") }
                        }
                    }
                }
            }
        }
        item { SectionTitle("Daily Missions", if (capacity == Energy.Low) "Core first. Optional work can wait." else "What does your character need to do today?", action = { FilledTonalButton(onClick = { addQuest = true }) { Text("+ Add") } }) }
        if (missions.isEmpty()) item { EmptyState("Today is clear", "Add a mission or intentionally recover.", "Add mission") { addQuest = true } }
        else items(missions.take(if (capacity == Energy.Low) 3 else 6), key = { it.id }) { quest -> QuestRow(quest, snapshot, prefs, vm, onFocus) }
        item { SectionTitle("Rituals", "Consistency without destructive streaks") }
        if (activeRituals.isEmpty()) item { EmptyState("No rituals yet", "Build a recurring practice when you are ready.") }
        else items(activeRituals, key = { "ritual_${it.id}" }) { ritual -> RitualRow(ritual, snapshot, vm) }
        item {
            val focusDomain = snapshot.domains.filter { it.enabled }.maxByOrNull { it.priority }
            focusDomain?.let {
                SectionTitle("Life Domain", "Current priority")
                Spacer(Modifier.height(8.dp)); DomainPulse(it)
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }

    if (addQuest) AddQuestDialog(snapshot, defaultType = QuestType.DailyMission, onDismiss = { addQuest = false }) { vm.addQuest(it); addQuest = false }
    if (checkIn) CheckInDialog(onDismiss = { checkIn = false }) { energy, mood, note, dayCapacity -> vm.saveCheckIn(energy, mood, note, dayCapacity); capacity = dayCapacity; checkIn = false }
}

@Composable
private fun QuestRow(quest: Quest, snapshot: AppSnapshot, prefs: com.evolune.app.data.UserPreferences, vm: AppViewModel, onFocus: (Long) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val domain = snapshot.domains.firstOrNull { it.id == quest.domainId }?.name ?: "Domain"
    ListItem(
        headlineContent = { Text(quest.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("$domain · ${quest.difficulty.name} · +${quest.rewardXp} XP") },
        leadingContent = {
            FilledTonalIconButton(onClick = { if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.completeQuest(quest.id) }, modifier = Modifier.semantics { contentDescription = "Complete ${quest.title}" }) { Text("✓") }
        },
        trailingContent = { TextButton(onClick = { onFocus(quest.id) }) { Text("Focus") } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
}

@Composable
private fun RitualRow(ritual: Ritual, snapshot: AppSnapshot, vm: AppViewModel) {
    val done = ritual.lastCompletedDay == LocalDate.now().toString()
    val stage = GameRules.masteryStage(ritual.masteryPoints)
    val domain = snapshot.domains.firstOrNull { it.id == ritual.domainId }?.name ?: "Domain"
    ListItem(
        headlineContent = { Text(ritual.title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("$domain · ${stage.name} · ${ritual.completions} completions") },
        leadingContent = { Box(Modifier.size(40.dp).clip(CircleShape).background(if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(if (done) "✓" else "◌") } },
        trailingContent = { if (!done) TextButton(onClick = { vm.completeRitual(ritual.id, "target") }) { Text(if (ritual.target.isBlank()) "Done" else "Target") } else Text("Done", style = MaterialTheme.typography.labelMedium) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun DomainPulse(domain: LifeDomain) {
    val level = GameRules.levelFromXp(domain.xp)
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text(level.toString(), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
            Text(domain.name, fontWeight = FontWeight.SemiBold); Text("Domain level $level · Priority ${domain.priority}/10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { GameRules.progressWithinLevel(domain.xp) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun QuestsScreen(snapshot: AppSnapshot, vm: AppViewModel, onFocus: (Long) -> Unit, onSettings: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<QuestType?>(null) }
    var addQuest by rememberSaveable { mutableStateOf(false) }
    var addRitual by rememberSaveable { mutableStateOf(false) }
    val list = snapshot.quests.filter { it.completedAt == null && (filter == null || it.type == filter) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Quests", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold); Text("Missions, quests, campaigns and trials", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Active") }) }
            items(QuestType.entries) { type -> FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(type.label) }) }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { addQuest = true }) { Text("+ Quest") }
            OutlinedButton(onClick = { addRitual = true }) { Text("+ Ritual") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (list.isEmpty()) item { EmptyState("No active quests here", "Create something meaningful or change the filter.") }
            items(list, key = { it.id }) { q ->
                val domain = snapshot.domains.firstOrNull { it.id == q.domainId }?.name ?: "Domain"
                ListItem(
                    headlineContent = { Text(q.title, fontWeight = FontWeight.Medium) },
                    overlineContent = { Text(q.type.label.uppercase()) },
                    supportingContent = { Text("$domain · ${q.difficulty.name} · ${dueText(q.dueAt)}") },
                    trailingContent = { Column(horizontalAlignment = Alignment.End) { Text("+${q.rewardXp} XP", style = MaterialTheme.typography.labelMedium); TextButton(onClick = { onFocus(q.id) }) { Text("Focus") } } },
                    leadingContent = { FilledTonalIconButton(onClick = { vm.completeQuest(q.id) }) { Text("✓") } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
            }
            if (snapshot.quests.any { it.completedAt != null }) {
                item { Spacer(Modifier.height(24.dp)); SectionTitle("Recent completions", "Permanent progress stays in your history") }
                items(snapshot.quests.filter { it.completedAt != null }.take(8), key = { "done_${it.id}" }) { q ->
                    ListItem(headlineContent = { Text(q.title) }, supportingContent = { Text("${q.type.label} · ${q.completedAt?.let(::formatDate) ?: ""}") }, leadingContent = { Text("✓") }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
                }
            }
        }
    }
    if (addQuest) AddQuestDialog(snapshot, filter ?: QuestType.SideQuest, onDismiss = { addQuest = false }) { vm.addQuest(it); addQuest = false }
    if (addRitual) AddRitualDialog(snapshot, onDismiss = { addRitual = false }) { vm.addRitual(it); addRitual = false }
}
