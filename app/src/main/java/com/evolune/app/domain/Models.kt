package com.evolune.app.domain

enum class Presentation { Male, Female }
enum class LifeChapter(val label: String) {
    Starting("Starting"), Rebuilding("Rebuilding"), FindingDirection("Finding Direction"),
    BuildingMomentum("Building Momentum"), Growing("Growing"), Performing("Performing"), Mastering("Mastering")
}
enum class Difficulty(val xp: Int) { Trivial(5), Easy(10), Standard(20), Hard(35), Epic(60) }
enum class QuestType(val label: String) {
    DailyMission("Daily Mission"), SideQuest("Side Quest"), MainQuest("Main Quest"), Campaign("Campaign"), Trial("Trial"), Boss("Boss")
}
enum class MasteryStage { Seed, Developing, Stable, Strong, Mastered }
enum class Energy { Low, Normal, High }
enum class GamificationMode { Minimal, Balanced, RPG }
enum class ThemeMode { System, Light, Dark }

data class Profile(
    val id: Long = 1,
    val name: String,
    val alias: String = "",
    val presentation: Presentation,
    val chapter: LifeChapter,
    val xp: Long = 0,
    val coins: Int = 0,
    val essence: Int = 0,
    val momentum: Int = 50,
    val equippedTitle: String = "The Beginner",
    val createdAt: Long = System.currentTimeMillis()
)

data class LifeDomain(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val satisfaction: Int = 5,
    val consistency: Int = 5,
    val priority: Int = 5,
    val xp: Long = 0
)

data class Skill(val id: Long = 0, val domainId: Long, val name: String, val archived: Boolean = false, val xp: Long = 0)

data class Quest(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val type: QuestType = QuestType.DailyMission,
    val domainId: Long,
    val skillId: Long? = null,
    val difficulty: Difficulty = Difficulty.Standard,
    val dueAt: Long? = null,
    val recurrence: String = "none",
    val notes: String = "",
    val subtasks: String = "",
    val reminderAt: Long? = null,
    val rewardXp: Int = difficulty.xp,
    val rewardCoins: Int = GameRules.coinReward(difficulty),
    val parentId: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Ritual(
    val id: Long = 0,
    val title: String,
    val domainId: Long,
    val skillId: Long? = null,
    val recurrence: String = "daily",
    val minimum: String = "",
    val target: String = "",
    val stretch: String = "",
    val masteryPoints: Int = 0,
    val completions: Int = 0,
    val lastCompletedDay: String? = null,
    val archived: Boolean = false
)

data class Reward(
    val id: Long = 0,
    val title: String,
    val costCoins: Int,
    val redeemedCount: Int = 0,
    val pendingCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class Achievement(
    val key: String,
    val name: String,
    val description: String,
    val rarity: String,
    val unlockedAt: Long? = null
)

data class TimelineEvent(val id: Long = 0, val type: String, val title: String, val detail: String = "", val at: Long = System.currentTimeMillis())

data class FocusSession(val id: Long = 0, val questId: Long?, val minutes: Int, val completed: Boolean, val at: Long = System.currentTimeMillis())
data class CosmeticUnlock(val key: String, val title: String, val costEssence: Int, val unlockedAt: Long)

data class AppSnapshot(
    val profile: Profile? = null,
    val domains: List<LifeDomain> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val quests: List<Quest> = emptyList(),
    val rituals: List<Ritual> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    val timeline: List<TimelineEvent> = emptyList(),
    val cosmetics: List<CosmeticUnlock> = emptyList(),
    val focusMinutes: Int = 0
)
