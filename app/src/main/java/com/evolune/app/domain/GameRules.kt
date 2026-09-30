package com.evolune.app.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object GameRules {
    fun coinReward(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.Trivial -> 1
        Difficulty.Easy -> 2
        Difficulty.Standard -> 4
        Difficulty.Hard -> 7
        Difficulty.Epic -> 12
    }

    fun xpForCompletion(difficulty: Difficulty, repetitionCount: Int = 0, maintenance: Boolean = false): Int {
        val antiFarm = when {
            repetitionCount >= 12 -> 0.35
            repetitionCount >= 7 -> 0.55
            repetitionCount >= 4 -> 0.75
            else -> 1.0
        }
        val maintenanceFactor = if (maintenance) 0.55 else 1.0
        return max(1, (difficulty.xp * antiFarm * maintenanceFactor).toInt())
    }

    fun xpRequiredForLevel(level: Int): Long {
        if (level <= 1) return 0
        var total = 0.0
        for (l in 1 until level) total += 85.0 + 24.0 * l + 7.0 * l.toDouble().pow(1.45)
        return total.toLong()
    }

    fun levelFromXp(xp: Long): Int {
        var level = 1
        while (level < 500 && xp >= xpRequiredForLevel(level + 1)) level++
        return level
    }

    fun progressWithinLevel(xp: Long): Float {
        val level = levelFromXp(xp)
        val start = xpRequiredForLevel(level)
        val end = xpRequiredForLevel(level + 1)
        return if (end == start) 1f else ((xp - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    fun updateMomentum(current: Int, completedCore: Int, plannedCore: Int, recoveryPlanned: Boolean = false): Int {
        if (recoveryPlanned) return min(100, current + 1)
        if (plannedCore <= 0) return max(0, current - 1)
        val ratio = completedCore.toDouble() / plannedCore
        val delta = when {
            ratio >= 1.0 -> 4
            ratio >= 0.75 -> 2
            ratio >= 0.5 -> -2
            ratio > 0 -> -4
            else -> -6
        }
        return (current + delta).coerceIn(0, 100)
    }

    fun masteryStage(points: Int): MasteryStage = when {
        points >= 240 -> MasteryStage.Mastered
        points >= 120 -> MasteryStage.Strong
        points >= 60 -> MasteryStage.Stable
        points >= 20 -> MasteryStage.Developing
        else -> MasteryStage.Seed
    }

    fun ritualPoints(stageReached: String): Int = when (stageReached.lowercase()) {
        "stretch" -> 3
        "target" -> 2
        else -> 1
    }

    fun dailyAdvancement(coreDone: Int, corePlanned: Int, ritualsDone: Int, ritualsPlanned: Int, focusMinutes: Int, recoveryPlanned: Boolean): Int {
        if (recoveryPlanned && corePlanned == 0) return 100
        val core = if (corePlanned == 0) 1.0 else coreDone.toDouble() / corePlanned
        val ritual = if (ritualsPlanned == 0) 1.0 else ritualsDone.toDouble() / ritualsPlanned
        val focus = min(1.0, focusMinutes / 45.0)
        return ((core.coerceIn(0.0, 1.0) * 0.60 + ritual.coerceIn(0.0, 1.0) * 0.25 + focus * 0.15) * 100).toInt()
    }

    fun canPurchase(coins: Int, cost: Int): Boolean = cost >= 0 && coins >= cost

    fun canComplete(completedAt: Long?): Boolean = completedAt == null

    data class ProgressionDelta(val characterXp: Int, val domainXp: Int, val skillXp: Int, val coins: Int)

    fun questProgressionDelta(xp: Int, coins: Int, hasSkill: Boolean): ProgressionDelta {
        val safeXp = xp.coerceAtLeast(0)
        return ProgressionDelta(safeXp, safeXp, if (hasSkill) safeXp else 0, coins.coerceAtLeast(0))
    }

    fun essenceForLevelUp(oldLevel: Int, newLevel: Int): Int = if (newLevel <= oldLevel) 0 else (oldLevel + 1..newLevel).fold(0) { acc, level -> acc + if (level % 5 == 0) 2 else 1 }

    fun simulatedLevel(years: Int, averageXpPerDay: Int = 80): Int = levelFromXp(years * 365L * averageXpPerDay)
}
