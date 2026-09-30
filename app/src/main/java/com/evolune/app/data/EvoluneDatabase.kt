package com.evolune.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EvoluneDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE profile(
                id INTEGER PRIMARY KEY CHECK(id = 1), name TEXT NOT NULL, alias TEXT NOT NULL DEFAULT '',
                presentation TEXT NOT NULL, chapter TEXT NOT NULL, xp INTEGER NOT NULL DEFAULT 0,
                coins INTEGER NOT NULL DEFAULT 0, essence INTEGER NOT NULL DEFAULT 0,
                momentum INTEGER NOT NULL DEFAULT 50, equipped_title TEXT NOT NULL DEFAULT 'The Beginner',
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE domains(
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1,
                sort_order INTEGER NOT NULL, satisfaction INTEGER NOT NULL DEFAULT 5,
                consistency INTEGER NOT NULL DEFAULT 5, priority INTEGER NOT NULL DEFAULT 5,
                xp INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE skills(
                id INTEGER PRIMARY KEY AUTOINCREMENT, domain_id INTEGER NOT NULL, name TEXT NOT NULL,
                archived INTEGER NOT NULL DEFAULT 0, xp INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(domain_id) REFERENCES domains(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE quests(
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL, domain_id INTEGER NOT NULL, skill_id INTEGER, difficulty TEXT NOT NULL,
                due_at INTEGER, recurrence TEXT NOT NULL DEFAULT 'none', notes TEXT NOT NULL DEFAULT '',
                subtasks TEXT NOT NULL DEFAULT '', reminder_at INTEGER, reward_xp INTEGER NOT NULL, reward_coins INTEGER NOT NULL, parent_id INTEGER,
                completed_at INTEGER, created_at INTEGER NOT NULL,
                FOREIGN KEY(domain_id) REFERENCES domains(id) ON DELETE RESTRICT,
                FOREIGN KEY(skill_id) REFERENCES skills(id) ON DELETE SET NULL,
                FOREIGN KEY(parent_id) REFERENCES quests(id) ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_quests_due ON quests(due_at)")
        db.execSQL("CREATE INDEX idx_quests_completed ON quests(completed_at)")
        db.execSQL("""
            CREATE TABLE rituals(
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, domain_id INTEGER NOT NULL,
                skill_id INTEGER, recurrence TEXT NOT NULL DEFAULT 'daily', minimum_value TEXT NOT NULL DEFAULT '',
                target_value TEXT NOT NULL DEFAULT '', stretch_value TEXT NOT NULL DEFAULT '',
                mastery_points INTEGER NOT NULL DEFAULT 0, completions INTEGER NOT NULL DEFAULT 0,
                last_completed_day TEXT, archived INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(domain_id) REFERENCES domains(id) ON DELETE RESTRICT,
                FOREIGN KEY(skill_id) REFERENCES skills(id) ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE ritual_log(
                id INTEGER PRIMARY KEY AUTOINCREMENT, ritual_id INTEGER NOT NULL, day TEXT NOT NULL,
                stage TEXT NOT NULL, xp_awarded INTEGER NOT NULL, created_at INTEGER NOT NULL,
                UNIQUE(ritual_id, day), FOREIGN KEY(ritual_id) REFERENCES rituals(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE rewards(
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, cost_coins INTEGER NOT NULL,
                redeemed_count INTEGER NOT NULL DEFAULT 0, pending_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE reward_log(
                id INTEGER PRIMARY KEY AUTOINCREMENT, reward_id INTEGER NOT NULL, cost_coins INTEGER NOT NULL,
                status TEXT NOT NULL, created_at INTEGER NOT NULL,
                FOREIGN KEY(reward_id) REFERENCES rewards(id) ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE achievements(
                achievement_key TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL,
                rarity TEXT NOT NULL, unlocked_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE timeline(
                id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, title TEXT NOT NULL,
                detail TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE focus_sessions(
                id INTEGER PRIMARY KEY AUTOINCREMENT, quest_id INTEGER, minutes INTEGER NOT NULL,
                completed INTEGER NOT NULL, at INTEGER NOT NULL,
                FOREIGN KEY(quest_id) REFERENCES quests(id) ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE cosmetics(
                cosmetic_key TEXT PRIMARY KEY, title TEXT NOT NULL, cost_essence INTEGER NOT NULL, unlocked_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE checkins(
                day TEXT PRIMARY KEY, energy TEXT NOT NULL, mood INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '', capacity TEXT NOT NULL DEFAULT 'Normal'
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE seasons(
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, start_day TEXT NOT NULL,
                end_day TEXT NOT NULL, priorities TEXT NOT NULL DEFAULT '', completed_at INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var version = oldVersion
        if (version < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS checkins(day TEXT PRIMARY KEY, energy TEXT NOT NULL, mood INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', capacity TEXT NOT NULL DEFAULT 'Normal')")
            db.execSQL("CREATE TABLE IF NOT EXISTS seasons(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, start_day TEXT NOT NULL, end_day TEXT NOT NULL, priorities TEXT NOT NULL DEFAULT '', completed_at INTEGER)")
            version = 2
        }
        if (version < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS cosmetics(cosmetic_key TEXT PRIMARY KEY, title TEXT NOT NULL, cost_essence INTEGER NOT NULL, unlocked_at INTEGER NOT NULL)")
            version = 3
        }
        if (version < 4) {
            db.execSQL("ALTER TABLE quests ADD COLUMN subtasks TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE quests ADD COLUMN reminder_at INTEGER")
            version = 4
        }
        if (version != newVersion) error("Missing migration from $version to $newVersion")
    }

    companion object {
        private const val DB_NAME = "evolune.db"
        private const val DB_VERSION = 4
    }
}
