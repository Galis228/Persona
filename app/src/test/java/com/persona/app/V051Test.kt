package com.persona.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V051Test {
    private val or = Settings(provider = Provider.OPENROUTER, apiModel = "x/y:free")

    @Test fun classifiesApiErrors() {
        assertEquals(ErrKind.CREDITS, ApiErrors.classify(402, """{"error":{"message":"Insufficient credits"}}""", "Insufficient credits", or).kind)
        assertEquals("https://openrouter.ai/settings/credits", ApiErrors.classify(402, "", "x", or).url)
        assertEquals(ErrKind.KEY, ApiErrors.classify(401, "", "No auth", or).kind)
        assertEquals(ErrKind.PRIVACY, ApiErrors.classify(404, "No endpoints found matching your data policy", "No endpoints found matching your data policy", or).kind)
        assertEquals(ErrKind.QUOTA_DAILY, ApiErrors.classify(429, "Rate limit exceeded: free-models-per-day", "Rate limit exceeded: free-models-per-day", or).kind)
        assertEquals(ErrKind.RATE, ApiErrors.classify(429, "", "slow down", or).kind)
        val oa = Settings(provider = Provider.OPENAI, apiModel = "gpt-4o")
        assertEquals(ErrKind.CREDITS, ApiErrors.classify(429, """{"error":{"code":"insufficient_quota"}}""", "You exceeded your current quota", oa).kind)
        val cl = Settings(provider = Provider.CLAUDE)
        assertEquals(ErrKind.CREDITS, ApiErrors.classify(400, "Your credit balance is too low", "Your credit balance is too low", cl).kind)
        val gm = Settings(provider = Provider.GEMINI)
        assertEquals(ErrKind.QUOTA_DAILY, ApiErrors.classify(429, "RESOURCE_EXHAUSTED", "quota", gm).kind)
    }

    @Test fun versions() {
        assertTrue(Updates.compare("0.5.2", "0.5.1") > 0)
        assertTrue(Updates.compare("v0.6", "0.5.9") > 0)
        assertEquals(0, Updates.compare("0.5", "0.5.0"))
        assertTrue(Updates.compare("0.5.1", "0.10") < 0)
    }

    @Test fun emotionsAndReactions() {
        assertEquals("радость", Prompt.emotionFor("игривое"))
        assertEquals("злость", Prompt.emotionFor("обиженное"))
        assertEquals("смущение", Prompt.emotionFor("смущённое"))
        assertNull(Prompt.emotionFor(""))
        val h = Prompt.history(listOf(Msg(fromUser = false, variants = listOf("Шутка!"), reaction = "😂"), Msg(fromUser = true, variants = listOf("ахах"))), 1000)
        assertEquals(2, h.size); assertTrue(h[1].text.contains("😂")); assertTrue(h[1].text.endsWith("ахах"))
    }

    @Test fun backupSchedule() {
        val now = 10_000_000_000L
        assertFalse(AutoBackup.due(AutoBackupCfg(freq = BackupFreq.OFF, last = 0), now))
        assertTrue(AutoBackup.due(AutoBackupCfg(freq = BackupFreq.DAILY, last = 0), now))
        assertFalse(AutoBackup.due(AutoBackupCfg(freq = BackupFreq.WEEKLY, last = now - 86_400_000L), now))
        assertTrue(AutoBackup.due(AutoBackupCfg(freq = BackupFreq.DAILY, last = now - 86_400_000L), now))
    }
}
