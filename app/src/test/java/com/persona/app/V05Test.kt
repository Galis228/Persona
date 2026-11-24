package com.persona.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V05Test {
    @Test fun detectsProviderByKey() {
        assertEquals(Provider.OPENROUTER, KeyLinks.detect("sk-or-v1-abc"))
        assertEquals(Provider.CLAUDE, KeyLinks.detect("sk-ant-api03-x"))
        assertEquals(Provider.GEMINI, KeyLinks.detect("AIzaSyD-123"))
        assertEquals(Provider.OPENAI, KeyLinks.detect("sk-proj-123"))
        assertNull(KeyLinks.detect("hello"))
        assertTrue(KeyLinks.looksLikeKey("sk-or-v1-0123456789abcdef"))
        assertFalse(KeyLinks.looksLikeKey("привет как дела у тебя сегодня"))
    }

    @Test fun picksSensibleModels() {
        assertEquals("deepseek/deepseek-chat-v3:free", ModelPick.recommend(Provider.OPENROUTER, listOf("openai/gpt-4o", "deepseek/deepseek-chat-v3:free", "openrouter/auto")))
        assertEquals("gemini-2.5-flash", ModelPick.recommend(Provider.GEMINI, listOf("gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite", "text-embedding-004", "gemini-2.5-flash-preview-tts")))
        assertEquals("gpt-4o-mini", ModelPick.recommend(Provider.OPENAI, listOf("gpt-4o", "gpt-4o-mini", "gpt-4o-mini-tts", "dall-e-3")))
        assertEquals("claude-sonnet-4-5", ModelPick.recommend(Provider.CLAUDE, listOf("claude-haiku-4-5", "claude-sonnet-4-5", "claude-sonnet-4")))
    }

    @Test fun parsesWorldInfo() {
        val wi = """{"entries":{"0":{"key":["гильдия","барды"],"content":"Гильдия бардов — закрытый клуб.","disable":false},"1":{"key":["порт"],"content":"","disable":false},"2":{"key":["маяк"],"content":"Старый маяк.","disable":true}},"name":"Север"}"""
        val b = Cards.parseLorebook(wi)!!
        assertEquals("Север", b.name); assertEquals(2, b.entries.size)
        assertEquals(listOf("гильдия", "барды"), b.entries[0].keys); assertFalse(b.entries[1].enabled)
        val card = """{"spec":"chara_card_v2","data":{"name":"Лира","character_book":{"entries":[{"keys":["лютня"],"content":"Лютня из клёна","enabled":true}]}}}"""
        assertEquals("Лютня из клёна", Cards.parseLorebook(card, "Мир")!!.entries[0].content)
        assertNull(Cards.parseLorebook("""{"name":"x"}"""))
    }

    @Test fun templatesAreComplete() {
        assertTrue(Templates.all.size >= 8)
        Templates.all.forEach { t ->
            assertTrue(t.c.name.isNotBlank() && t.c.name.length <= 20)
            assertTrue(t.c.tagline.length <= 50)
            assertTrue(t.c.description.length <= 500)
            assertTrue(t.c.greetings.first().isNotBlank())
        }
    }
}
