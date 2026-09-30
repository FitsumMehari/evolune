package com.evolune.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.evolune.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class EvoluneRepository(
    private val helper: EvoluneDatabase,
    val preferences: AppPreferences
) {
    suspend fun snapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        AppSnapshot(
            profile = readProfile(db),
            domains = readDomains(db),
            skills = readSkills(db),
            quests = readQuests(db),
            rituals = readRituals(db),
            rewards = readRewards(db),
            achievements = readAchievements(db),
            timeline = readTimeline(db),
            cosmetics = readCosmetics(db),
            focusMinutes = scalarInt(db, "SELECT COALESCE(SUM(minutes),0) FROM focus_sessions WHERE completed=1")
        )
    }

    suspend fun createCharacter(
        name: String,
        alias: String,
        presentation: Presentation,
        chapter: LifeChapter,
        calibratedDomains: List<LifeDomain>
    ) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.inTransaction {
            db.delete("profile", null, null)
            val now = System.currentTimeMillis()
            db.insertOrThrow("profile", null, ContentValues().apply {
                put("id", 1); put("name", name.trim().ifBlank { "Traveler" }); put("alias", alias.trim())
                put("presentation", presentation.name); put("chapter", chapter.name); put("xp", 0L); put("coins", 0)
                put("essence", 0); put("momentum", 50); put("equipped_title", "The Beginner"); put("created_at", now)
            })
            db.delete("domains", null, null)
            calibratedDomains.forEachIndexed { index, d ->
                db.insertOrThrow("domains", null, ContentValues().apply {
                    put("name", d.name); put("enabled", d.enabled.int); put("sort_order", index)
                    put("satisfaction", d.satisfaction.coerceIn(1, 10)); put("consistency", d.consistency.coerceIn(1, 10))
                    put("priority", d.priority.coerceIn(1, 10)); put("xp", 0L)
                })
            }
            db.insert("timeline", null, ContentValues().apply {
                put("type", "character"); put("title", "Character created"); put("detail", "The journey began."); put("at", now)
            })
            unlockAchievement(db, "first_profile")
        }
    }

    suspend fun addQuest(quest: Quest): Long = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertOrThrow("quests", null, ContentValues().apply {
            put("title", quest.title.trim()); put("description", quest.description.trim()); put("type", quest.type.name)
            put("domain_id", quest.domainId); quest.skillId?.let { put("skill_id", it) }; put("difficulty", quest.difficulty.name)
            quest.dueAt?.let { put("due_at", it) }; put("recurrence", quest.recurrence); put("notes", quest.notes)
            put("subtasks", quest.subtasks); quest.reminderAt?.let { put("reminder_at", it) }
            put("reward_xp", quest.rewardXp.coerceIn(1, 200)); put("reward_coins", quest.rewardCoins.coerceIn(0, 50))
            quest.parentId?.let { put("parent_id", it) }; put("created_at", System.currentTimeMillis())
        })
    }

    data class CompletionResult(val applied: Boolean, val xp: Int = 0, val coins: Int = 0, val levelUp: Boolean = false)

    suspend fun completeQuest(id: Long): CompletionResult = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        var result = CompletionResult(false)
        db.inTransaction {
            val quest = queryOneQuest(db, id) ?: return@inTransaction
            if (!GameRules.canComplete(quest.completedAt)) return@inTransaction
            val oldProfile = readProfile(db) ?: return@inTransaction
            val repeatCount = scalarInt(
                db,
                "SELECT COUNT(*) FROM quests WHERE title=? AND completed_at IS NOT NULL AND completed_at>?",
                arrayOf(quest.title, (System.currentTimeMillis() - 30L * 86_400_000L).toString())
            )
            val xp = if (quest.rewardXp == quest.difficulty.xp) GameRules.xpForCompletion(quest.difficulty, repeatCount) else quest.rewardXp.coerceIn(1, 200)
            val coins = quest.rewardCoins.coerceIn(0, 50)
            val delta = GameRules.questProgressionDelta(xp, coins, quest.skillId != null)
            val now = System.currentTimeMillis()
            val updated = db.update("quests", ContentValues().apply { put("completed_at", now) }, "id=? AND completed_at IS NULL", arrayOf(id.toString()))
            if (updated != 1) return@inTransaction
            db.execSQL("UPDATE profile SET xp=xp+?, coins=coins+? WHERE id=1", arrayOf(delta.characterXp, delta.coins))
            db.execSQL("UPDATE domains SET xp=xp+? WHERE id=?", arrayOf<Any>(delta.domainXp, quest.domainId))
            quest.skillId?.let { db.execSQL("UPDATE skills SET xp=xp+? WHERE id=?", arrayOf<Any>(delta.skillXp, it)) }
            val newLevel = GameRules.levelFromXp(oldProfile.xp + xp)
            val oldLevel = GameRules.levelFromXp(oldProfile.xp)
            if (newLevel > oldLevel) {
                val essence = GameRules.essenceForLevelUp(oldLevel, newLevel)
                db.execSQL("UPDATE profile SET essence=essence+? WHERE id=1", arrayOf(essence))
                db.insert("timeline", null, ContentValues().apply {
                    put("type", "level"); put("title", "Reached Level $newLevel"); put("detail", "+$essence Essence"); put("at", now)
                })
                if (newLevel >= 10) unlockAchievement(db, "level_10")
            }
            db.insert("timeline", null, ContentValues().apply {
                put("type", "quest"); put("title", quest.title); put("detail", "+$xp XP · +$coins Coins"); put("at", now)
            })
            unlockAchievement(db, "first_step")
            when (quest.type) {
                QuestType.MainQuest -> unlockAchievement(db, "builder")
                QuestType.Boss -> unlockAchievement(db, "boss_slayer")
                QuestType.Campaign -> unlockAchievement(db, "campaigner")
                else -> Unit
            }
            result = CompletionResult(true, xp, coins, newLevel > oldLevel)
        }
        result
    }

    suspend fun addSkill(domainId: Long, name: String): Long = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertOrThrow("skills", null, ContentValues().apply {
            put("domain_id", domainId); put("name", name.trim()); put("archived", 0); put("xp", 0L)
        })
    }

    suspend fun archiveSkill(id: Long, archived: Boolean) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update("skills", ContentValues().apply { put("archived", archived.int) }, "id=?", arrayOf(id.toString()))
        Unit
    }

    suspend fun updateSkill(id: Long, name: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Skill name cannot be blank" }
        helper.writableDatabase.update("skills", ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf(id.toString()))
        Unit
    }

    suspend fun addRitual(ritual: Ritual): Long = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertOrThrow("rituals", null, ContentValues().apply {
            put("title", ritual.title.trim()); put("domain_id", ritual.domainId); ritual.skillId?.let { put("skill_id", it) }
            put("recurrence", ritual.recurrence); put("minimum_value", ritual.minimum); put("target_value", ritual.target)
            put("stretch_value", ritual.stretch); put("mastery_points", 0); put("completions", 0); put("archived", 0)
        })
    }

    suspend fun completeRitual(id: Long, stage: String): CompletionResult = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        var result = CompletionResult(false)
        db.inTransaction {
            val ritual = queryOneRitual(db, id) ?: return@inTransaction
            val day = LocalDate.now().toString()
            if (ritual.lastCompletedDay == day) return@inTransaction
            val points = GameRules.ritualPoints(stage)
            val currentStage = GameRules.masteryStage(ritual.masteryPoints)
            val base = when (stage.lowercase()) { "stretch" -> 16; "target" -> 11; else -> 6 }
            val xp = if (currentStage == MasteryStage.Mastered) (base * .55).toInt() else base
            val now = System.currentTimeMillis()
            db.insertOrThrow("ritual_log", null, ContentValues().apply {
                put("ritual_id", id); put("day", day); put("stage", stage.lowercase()); put("xp_awarded", xp); put("created_at", now)
            })
            val newPoints = ritual.masteryPoints + points
            db.update("rituals", ContentValues().apply {
                put("mastery_points", newPoints); put("completions", ritual.completions + 1); put("last_completed_day", day)
            }, "id=?", arrayOf(id.toString()))
            db.execSQL("UPDATE profile SET xp=xp+?, coins=coins+1 WHERE id=1", arrayOf(xp))
            db.execSQL("UPDATE domains SET xp=xp+? WHERE id=?", arrayOf<Any>(xp, ritual.domainId))
            ritual.skillId?.let { db.execSQL("UPDATE skills SET xp=xp+? WHERE id=?", arrayOf<Any>(xp, it)) }
            if (GameRules.masteryStage(newPoints) == MasteryStage.Mastered && currentStage != MasteryStage.Mastered) {
                unlockAchievement(db, "mastery")
                db.insert("timeline", null, ContentValues().apply {
                    put("type", "mastery"); put("title", "${ritual.title} mastered"); put("detail", "The ritual moved into maintenance."); put("at", now)
                })
            }
            result = CompletionResult(true, xp, 1, false)
        }
        result
    }

    suspend fun addReward(title: String, costCoins: Int): Long = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertOrThrow("rewards", null, ContentValues().apply {
            put("title", title.trim()); put("cost_coins", costCoins.coerceAtLeast(1)); put("redeemed_count", 0)
            put("pending_count", 0); put("created_at", System.currentTimeMillis())
        })
    }

    suspend fun purchaseReward(id: Long, redeemNow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        var success = false
        db.inTransaction {
            val reward = readRewards(db).firstOrNull { it.id == id } ?: return@inTransaction
            val changed = db.compileStatement("UPDATE profile SET coins=coins-? WHERE id=1 AND coins>=?").apply {
                bindLong(1, reward.costCoins.toLong()); bindLong(2, reward.costCoins.toLong())
            }.executeUpdateDelete()
            if (changed != 1) return@inTransaction
            val col = if (redeemNow) "redeemed_count" else "pending_count"
            db.execSQL("UPDATE rewards SET $col=$col+1 WHERE id=?", arrayOf(id))
            db.insert("reward_log", null, ContentValues().apply {
                put("reward_id", id); put("cost_coins", reward.costCoins); put("status", if (redeemNow) "redeemed" else "pending")
                put("created_at", System.currentTimeMillis())
            })
            success = true
        }
        success
    }

    suspend fun purchaseCosmetic(key: String, title: String, costEssence: Int): Boolean = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        var success = false
        db.inTransaction {
            if (scalarInt(db, "SELECT COUNT(*) FROM cosmetics WHERE cosmetic_key=?", arrayOf(key)) > 0) { success = true; return@inTransaction }
            val changed = db.compileStatement("UPDATE profile SET essence=essence-? WHERE id=1 AND essence>=?").apply {
                bindLong(1, costEssence.toLong()); bindLong(2, costEssence.toLong())
            }.executeUpdateDelete()
            if (changed != 1) return@inTransaction
            db.insertOrThrow("cosmetics", null, ContentValues().apply {
                put("cosmetic_key", key); put("title", title); put("cost_essence", costEssence); put("unlocked_at", System.currentTimeMillis())
            })
            success = true
        }
        success
    }

    suspend fun recordFocus(questId: Long?, minutes: Int, completed: Boolean) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.inTransaction {
            db.insert("focus_sessions", null, ContentValues().apply {
                questId?.let { put("quest_id", it) }; put("minutes", minutes.coerceAtLeast(0)); put("completed", completed.int); put("at", System.currentTimeMillis())
            })
            if (completed && minutes >= 1) {
                val xp = (minutes / 10).coerceIn(1, 20)
                db.execSQL("UPDATE profile SET xp=xp+? WHERE id=1", arrayOf(xp))
                if (scalarInt(db, "SELECT COALESCE(SUM(minutes),0) FROM focus_sessions WHERE completed=1") >= 600) unlockAchievement(db, "deep_worker")
            }
        }
    }

    suspend fun saveCheckIn(energy: Energy, mood: Int, note: String, capacity: Energy) = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertWithOnConflict("checkins", null, ContentValues().apply {
            put("day", LocalDate.now().toString()); put("energy", energy.name); put("mood", mood.coerceIn(1,5)); put("note", note.trim()); put("capacity", capacity.name)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun updateMomentum(completedCore: Int, plannedCore: Int, recoveryPlanned: Boolean) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val p = readProfile(db) ?: return@withContext
        val next = GameRules.updateMomentum(p.momentum, completedCore, plannedCore, recoveryPlanned)
        db.update("profile", ContentValues().apply { put("momentum", next) }, "id=1", null)
    }

    suspend fun updateDomain(domain: LifeDomain) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update("domains", ContentValues().apply {
            put("name", domain.name.trim()); put("enabled", domain.enabled.int); put("sort_order", domain.sortOrder)
            put("satisfaction", domain.satisfaction.coerceIn(1,10)); put("consistency", domain.consistency.coerceIn(1,10)); put("priority", domain.priority.coerceIn(1,10))
        }, "id=?", arrayOf(domain.id.toString()))
        Unit
    }

    suspend fun addDomain(name: String): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val order = scalarInt(db, "SELECT COALESCE(MAX(sort_order),-1)+1 FROM domains")
        db.insertOrThrow("domains", null, ContentValues().apply {
            put("name", name.trim()); put("enabled", 1); put("sort_order", order); put("satisfaction", 5); put("consistency", 5); put("priority", 5); put("xp", 0L)
        })
    }

    suspend fun moveDomain(id: Long, direction: Int) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.inTransaction {
            val domains = readDomains(db)
            val index = domains.indexOfFirst { it.id == id }
            if (index < 0 || domains.isEmpty()) return@inTransaction
            val target = (index + direction).coerceIn(0, domains.lastIndex)
            if (target == index) return@inTransaction
            val a = domains[index]
            val b = domains[target]
            db.update("domains", ContentValues().apply { put("sort_order", b.sortOrder) }, "id=?", arrayOf(a.id.toString()))
            db.update("domains", ContentValues().apply { put("sort_order", a.sortOrder) }, "id=?", arrayOf(b.id.toString()))
        }
    }

    suspend fun equipTitle(title: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update("profile", ContentValues().apply { put("equipped_title", title) }, "id=1", null)
        Unit
    }

    suspend fun resetAll() = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.inTransaction { clearTables(db) }
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        JSONObject().apply {
            put("schemaVersion", 2); put("exportedAt", System.currentTimeMillis())
            put("preferences", preferences.exportJson())
            for (table in BACKUP_TABLES) put(table, tableToJson(db, table))
        }.toString()
    }

    suspend fun restoreJson(raw: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion", 0) in 1..2) { "Unsupported Evolune backup version" }
        val db = helper.writableDatabase
        db.inTransaction {
            clearTables(db)
            for (table in RESTORE_ORDER) {
                val array = root.optJSONArray(table) ?: JSONArray()
                for (i in 0 until array.length()) insertJsonRow(db, table, array.getJSONObject(i))
            }
        }
        root.optJSONObject("preferences")?.let { preferences.restoreJson(it) }
    }

    private fun clearTables(db: SQLiteDatabase) {
        listOf("reward_log", "ritual_log", "focus_sessions", "achievements", "timeline", "cosmetics", "quests", "rituals", "skills", "rewards", "checkins", "seasons", "domains", "profile").forEach { db.delete(it, null, null) }
    }

    private fun readProfile(db: SQLiteDatabase): Profile? = db.rawQuery("SELECT * FROM profile WHERE id=1", null).use { c ->
        if (!c.moveToFirst()) null else Profile(
            id = c.long("id"), name = c.string("name"), alias = c.string("alias"),
            presentation = Presentation.valueOf(c.string("presentation")), chapter = LifeChapter.valueOf(c.string("chapter")),
            xp = c.long("xp"), coins = c.int("coins"), essence = c.int("essence"), momentum = c.int("momentum"),
            equippedTitle = c.string("equipped_title"), createdAt = c.long("created_at")
        )
    }

    private fun readDomains(db: SQLiteDatabase): List<LifeDomain> = queryList(db, "SELECT * FROM domains ORDER BY sort_order,id") { c ->
        LifeDomain(c.long("id"), c.string("name"), c.int("enabled") == 1, c.int("sort_order"), c.int("satisfaction"), c.int("consistency"), c.int("priority"), c.long("xp"))
    }

    private fun readSkills(db: SQLiteDatabase): List<Skill> = queryList(db, "SELECT * FROM skills ORDER BY archived,name") { c ->
        Skill(c.long("id"), c.long("domain_id"), c.string("name"), c.int("archived") == 1, c.long("xp"))
    }

    private fun readQuests(db: SQLiteDatabase): List<Quest> = queryList(db, "SELECT * FROM quests ORDER BY completed_at IS NOT NULL, COALESCE(due_at, 9223372036854775807), created_at DESC") { c -> c.toQuest() }

    private fun queryOneQuest(db: SQLiteDatabase, id: Long): Quest? = db.rawQuery("SELECT * FROM quests WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.toQuest() else null }

    private fun Cursor.toQuest() = Quest(
        id = long("id"), title = string("title"), description = string("description"), type = QuestType.valueOf(string("type")),
        domainId = long("domain_id"), skillId = nullableLong("skill_id"), difficulty = Difficulty.valueOf(string("difficulty")),
        dueAt = nullableLong("due_at"), recurrence = string("recurrence"), notes = string("notes"), subtasks = string("subtasks"), reminderAt = nullableLong("reminder_at"), rewardXp = int("reward_xp"),
        rewardCoins = int("reward_coins"), parentId = nullableLong("parent_id"), completedAt = nullableLong("completed_at"), createdAt = long("created_at")
    )

    private fun readRituals(db: SQLiteDatabase): List<Ritual> = queryList(db, "SELECT * FROM rituals ORDER BY archived,title") { c -> c.toRitual() }
    private fun queryOneRitual(db: SQLiteDatabase, id: Long): Ritual? = db.rawQuery("SELECT * FROM rituals WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.toRitual() else null }
    private fun Cursor.toRitual() = Ritual(
        id = long("id"), title = string("title"), domainId = long("domain_id"), skillId = nullableLong("skill_id"), recurrence = string("recurrence"),
        minimum = string("minimum_value"), target = string("target_value"), stretch = string("stretch_value"), masteryPoints = int("mastery_points"),
        completions = int("completions"), lastCompletedDay = nullableString("last_completed_day"), archived = int("archived") == 1
    )

    private fun readRewards(db: SQLiteDatabase): List<Reward> = queryList(db, "SELECT * FROM rewards ORDER BY cost_coins,title") { c ->
        Reward(c.long("id"), c.string("title"), c.int("cost_coins"), c.int("redeemed_count"), c.int("pending_count"), c.long("created_at"))
    }

    private fun readCosmetics(db: SQLiteDatabase): List<CosmeticUnlock> = queryList(db, "SELECT * FROM cosmetics ORDER BY unlocked_at") { c ->
        CosmeticUnlock(c.string("cosmetic_key"), c.string("title"), c.int("cost_essence"), c.long("unlocked_at"))
    }

    private fun readTimeline(db: SQLiteDatabase): List<TimelineEvent> = queryList(db, "SELECT * FROM timeline ORDER BY at DESC LIMIT 100") { c ->
        TimelineEvent(c.long("id"), c.string("type"), c.string("title"), c.string("detail"), c.long("at"))
    }

    private fun readAchievements(db: SQLiteDatabase): List<Achievement> {
        val unlocked = mutableMapOf<String, Long>()
        db.rawQuery("SELECT achievement_key, unlocked_at FROM achievements", null).use { c -> while (c.moveToNext()) unlocked[c.getString(0)] = c.getLong(1) }
        return ACHIEVEMENTS.map { it.copy(unlockedAt = unlocked[it.key]) }
    }

    private fun unlockAchievement(db: SQLiteDatabase, key: String) {
        val a = ACHIEVEMENTS.firstOrNull { it.key == key } ?: return
        val now = System.currentTimeMillis()
        val id = db.insertWithOnConflict("achievements", null, ContentValues().apply {
            put("achievement_key", a.key); put("name", a.name); put("description", a.description); put("rarity", a.rarity); put("unlocked_at", now)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        if (id != -1L) db.insert("timeline", null, ContentValues().apply {
            put("type", "achievement"); put("title", "Achievement: ${a.name}"); put("detail", a.description); put("at", now)
        })
    }

    private fun tableToJson(db: SQLiteDatabase, table: String): JSONArray = JSONArray().apply {
        db.rawQuery("SELECT * FROM $table", null).use { c ->
            while (c.moveToNext()) {
                put(JSONObject().apply {
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> put(name, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> put(name, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> put(name, c.getDouble(i))
                            Cursor.FIELD_TYPE_BLOB -> put(name, android.util.Base64.encodeToString(c.getBlob(i), android.util.Base64.NO_WRAP))
                            else -> put(name, c.getString(i))
                        }
                    }
                })
            }
        }
    }

    private fun insertJsonRow(db: SQLiteDatabase, table: String, obj: JSONObject) {
        val values = ContentValues()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (obj.isNull(key)) values.putNull(key) else when (val v = obj.get(key)) {
                is Int -> values.put(key, v); is Long -> values.put(key, v); is Double -> values.put(key, v)
                is Boolean -> values.put(key, v.int); else -> values.put(key, v.toString())
            }
        }
        db.insertOrThrow(table, null, values)
    }

    private inline fun <T> queryList(db: SQLiteDatabase, sql: String, args: Array<String>? = null, map: (Cursor) -> T): List<T> = buildList {
        db.rawQuery(sql, args).use { c -> while (c.moveToNext()) add(map(c)) }
    }

    private fun scalarInt(db: SQLiteDatabase, sql: String, args: Array<String>? = null): Int = db.rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private inline fun <T> SQLiteDatabase.inTransaction(block: () -> T): T {
        beginTransaction()
        try { val result = block(); setTransactionSuccessful(); return result } finally { endTransaction() }
    }

    private fun Cursor.index(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.string(name: String) = getString(index(name))
    private fun Cursor.nullableString(name: String): String? = index(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(name: String) = getLong(index(name))
    private fun Cursor.int(name: String) = getInt(index(name))
    private fun Cursor.nullableLong(name: String): Long? = index(name).let { if (isNull(it)) null else getLong(it) }
    private val Boolean.int get() = if (this) 1 else 0

    companion object {
        val DEFAULT_DOMAINS = listOf(
            "Health", "Fitness / Sport", "Appearance", "Mind / Intellect", "Career / Craft", "Finance / Economy",
            "Family", "Social", "Faith / Religion / Spirituality", "Character / Discipline", "Environment / Organization", "Joy / Creativity / Recreation"
        )

        private val ACHIEVEMENTS = listOf(
            Achievement("first_profile", "Awakening", "Create your character", "Common"),
            Achievement("first_step", "First Step", "Complete your first Quest", "Common"),
            Achievement("level_10", "Ascending", "Reach character Level 10", "Uncommon"),
            Achievement("deep_worker", "Deep Worker I", "Complete 10 hours of focused work", "Rare"),
            Achievement("builder", "Builder", "Complete a Main Quest", "Uncommon"),
            Achievement("boss_slayer", "Boss Slayer", "Complete a Boss milestone", "Epic"),
            Achievement("mastery", "Mastery", "Master your first Ritual", "Epic"),
            Achievement("campaigner", "Long Horizon", "Complete a Campaign", "Legendary")
        )

        private val BACKUP_TABLES = listOf("profile", "domains", "skills", "quests", "rituals", "ritual_log", "rewards", "reward_log", "achievements", "timeline", "focus_sessions", "checkins", "seasons", "cosmetics")
        private val RESTORE_ORDER = listOf("profile", "domains", "skills", "quests", "rituals", "ritual_log", "rewards", "reward_log", "achievements", "timeline", "focus_sessions", "checkins", "seasons", "cosmetics")
    }
}
