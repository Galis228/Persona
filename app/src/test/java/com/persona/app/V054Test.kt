package com.persona.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V054Test {
    private val openai = listOf("gpt-4o", "gpt-4o-mini", "gpt-4o-mini-2024-07-18", "gpt-4o-mini-tts", "gpt-4o-mini-search-preview", "gpt-4.1-mini", "gpt-5-mini", "o3")

    @Test fun findsModelFromSloppyName() {
        assertEquals("gpt-4o-mini", ModelPick.best("4.o mini gpt", openai))
        assertEquals("gpt-4o-mini", ModelPick.best("4o mini", openai))
        assertEquals("gpt-4o-mini", ModelPick.best("GPT-4o-mini", openai))
        assertEquals("gpt-4.1-mini", ModelPick.best("4.1 mini", openai))
    }

    @Test fun recommendsCheapChatModel() {
        assertEquals("gpt-4o-mini", ModelPick.recommend(Provider.OPENAI, openai))
        assertFalse(ModelPick.isChat("gpt-4o-mini-tts"))
    }

    @Test fun knowsPricesAndTitles() {
        assertEquals("GPT-4o mini", KnownModels.title("gpt-4o-mini-2024-07-18"))
        assertEquals("Claude Haiku 4.5", KnownModels.title("anthropic/claude-haiku-4.5"))
        assertEquals(0.0, KnownModels.price("x/y:free")!!.inPerM, 0.0)
        val p = KnownModels.price("gpt-4o-mini")!!
        // 1 млн входа без кэша + 1 млн выхода = 0.15 + 0.60
        assertEquals(0.75, KnownModels.cost(p, 1_000_000, 0, 1_000_000), 1e-9)
        // кэш вдвое дешевле
        assertEquals(0.075, KnownModels.cost(p, 1_000_000, 1_000_000, 0), 1e-9)
        val per = KnownModels.perReply(p, Settings())
        assertTrue(per in 0.0003..0.0015)
    }

    @Test fun stablePromptMovesDynamicPartsToTail() {
        val c = Character(id = "l", name = "Лира", moodEnabled = true, routineEnabled = true, routine = "утром спит", lorebookIds = listOf("b"))
        val book = Lorebook(id = "b", entries = listOf(LoreEntry(keys = listOf("гильдия"), content = "Гильдия воров правит портом")))
        val chat = Chat(charId = "l", mood = "задумчивое", messages = listOf(Msg(fromUser = true, variants = listOf("что с гильдией?"))))
        val s = Settings()
        val r = Prompt.reply(c, chat, s, listOf(book), chat.messages, local = false)
        assertFalse(r.system.contains("задумчивое"))
        assertFalse(r.system.contains("Гильдия воров"))
        assertTrue(r.tail.contains("задумчивое"))
        assertTrue(r.tail.contains("Гильдия воров"))
        assertTrue(r.folded().last().text.contains("Гильдия воров"))
        // Модель на телефоне получает всё в системном промпте, как раньше
        val local = Prompt.reply(c, chat, s, listOf(book), chat.messages, local = true)
        assertTrue(local.system.contains("Гильдия воров"))
        assertEquals("", local.tail)
        // Без кэша — тоже по-старому
        assertEquals("", Prompt.reply(c, chat, s.copy(promptCache = false), listOf(book), chat.messages, local = false).tail)
    }

    @Test fun stableHistoryDropsInChunks() {
        val msgs = (0 until 40).map { Msg(fromUser = it % 2 == 0, variants = listOf("сообщение номер $it " + "х".repeat(60))) }
        val one = Prompt.estimateTokens(msgs[0].text)
        val h1 = Prompt.stableHistory(msgs, one * 35)
        val h2 = Prompt.stableHistory(msgs + Msg(fromUser = false, variants = listOf("ещё " + "х".repeat(60))), one * 35)
        // Начало истории не сдвинулось от нового сообщения
        assertEquals(h1.first().text, h2.first().text)
        assertNotNull(h1.first().text.takeIf { it.contains("номер 10") })
    }
}

class ImageTest {
    @Test fun imagePrices() {
        org.junit.Assert.assertEquals(0.006, ImageModels.price("gpt-image-2", ImageQuality.LOW)!!, 1e-9)
        org.junit.Assert.assertEquals(0.211, ImageModels.price("gpt-image-2", ImageQuality.HIGH)!!, 1e-9)
        // У Gemini одна цена при любом качестве
        org.junit.Assert.assertEquals(0.039, ImageModels.price("google/gemini-2.5-flash-image", ImageQuality.HIGH)!!, 1e-9)
        org.junit.Assert.assertTrue(ImageModels.hasQuality("gpt-image-2"))
        org.junit.Assert.assertFalse(ImageModels.hasQuality("gemini-2.5-flash-image"))
        org.junit.Assert.assertEquals("gpt-image-2", ImageModels.effective(Settings(provider = Provider.OPENAI)))
        org.junit.Assert.assertEquals("Gemini 3 Pro Image", ImageModels.title("gemini-3-pro-image-preview"))
    }
}
