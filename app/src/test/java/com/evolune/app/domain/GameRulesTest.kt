package com.evolune.app.domain

import org.junit.Assert.*
import org.junit.Test

class GameRulesTest {
    @Test fun difficultyXpUsesBalancedDefaults() {
        assertEquals(5, Difficulty.Trivial.xp)
        assertEquals(10, Difficulty.Easy.xp)
        assertEquals(20, Difficulty.Standard.xp)
        assertEquals(35, Difficulty.Hard.xp)
        assertEquals(60, Difficulty.Epic.xp)
    }

    @Test fun antiFarmingReducesRepeatedTrivialWork() {
        val fresh = GameRules.xpForCompletion(Difficulty.Trivial, repetitionCount = 0)
        val repeated = GameRules.xpForCompletion(Difficulty.Trivial, repetitionCount = 12)
        assertTrue(repeated < fresh)
        assertTrue(repeated >= 1)
    }

    @Test fun levelsAreMonotonicAndPermanent() {
        assertEquals(1, GameRules.levelFromXp(0))
        var prior = 1
        for (xp in 0L..200_000L step 1_000) {
            val level = GameRules.levelFromXp(xp)
            assertTrue(level >= prior)
            prior = level
        }
    }

    @Test fun progressionRemainsMeaningfulAcrossYears() {
        val one = GameRules.simulatedLevel(1)
        val three = GameRules.simulatedLevel(3)
        val five = GameRules.simulatedLevel(5)
        val ten = GameRules.simulatedLevel(10)
        assertTrue(one > 1)
        assertTrue(three > one)
        assertTrue(five > three)
        assertTrue(ten > five)
    }

    @Test fun missingOneDayDecreasesMomentumWithoutResettingIt() {
        assertEquals(88, GameRules.updateMomentum(92, completedCore = 1, plannedCore = 3))
        assertEquals(86, GameRules.updateMomentum(92, completedCore = 0, plannedCore = 3))
        assertTrue(GameRules.updateMomentum(92, 0, 3) > 0)
    }

    @Test fun recoveryDayIsNotPunished() {
        assertEquals(100, GameRules.dailyAdvancement(0, 0, 0, 0, 0, recoveryPlanned = true))
        assertEquals(51, GameRules.updateMomentum(50, 0, 0, recoveryPlanned = true))
    }

    @Test fun ritualMasteryStagesAdvanceAtStableThresholds() {
        assertEquals(MasteryStage.Seed, GameRules.masteryStage(0))
        assertEquals(MasteryStage.Developing, GameRules.masteryStage(20))
        assertEquals(MasteryStage.Stable, GameRules.masteryStage(60))
        assertEquals(MasteryStage.Strong, GameRules.masteryStage(120))
        assertEquals(MasteryStage.Mastered, GameRules.masteryStage(240))
    }

    @Test fun dailyAdvancementUsesCoreRitualAndFocusSignals() {
        assertEquals(100, GameRules.dailyAdvancement(3, 3, 2, 2, 45, false))
        assertTrue(GameRules.dailyAdvancement(1, 3, 0, 2, 0, false) < 50)
    }

    @Test fun coinEconomyProtectsAgainstInsufficientBalance() {
        assertTrue(GameRules.canPurchase(25, 25))
        assertFalse(GameRules.canPurchase(24, 25))
        assertFalse(GameRules.canPurchase(100, -1))
    }

    @Test fun duplicateCompletionGuardRejectsCompletedQuest() {
        assertTrue(GameRules.canComplete(null))
        assertFalse(GameRules.canComplete(1_700_000_000_000L))
    }

    @Test fun questProgressionAppliesXpToCharacterDomainAndOptionalSkill() {
        val withSkill = GameRules.questProgressionDelta(35, 7, hasSkill = true)
        assertEquals(35, withSkill.characterXp)
        assertEquals(35, withSkill.domainXp)
        assertEquals(35, withSkill.skillXp)
        assertEquals(7, withSkill.coins)
        val withoutSkill = GameRules.questProgressionDelta(20, 4, hasSkill = false)
        assertEquals(0, withoutSkill.skillXp)
    }
}
