package com.persona.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V04Test {
    private val c = Character(id = "l", name = "Лира")
    private val s = Settings(userName = "Эйвин")

    @Test fun authorNoteGoesToLastTurn() {
        val msgs = listOf(Msg(fromUser = true, variants = listOf("Привет")))
        val r = Prompt.reply(c, Chat(charId = "l", messages = msgs, authorNote = "сейчас ночь"), s, emptyList(), msgs, local = true)
        assertTrue(r.history.last().text.startsWith("Привет"))
        assertTrue(r.history.last().text.contains("Заметка автора"))
        assertTrue(r.history.last().text.contains("сейчас ночь"))
    }

    @Test fun picturesSkippedPhotosMarked() {
        val msgs = listOf(
            Msg(fromUser = false, variants = listOf("prompt"), kind = PICTURE, image = "a.jpg"),
            Msg(fromUser = true, variants = listOf("смотри"), image = "b.jpg"),
        )
        val h = Prompt.history(msgs, 1000)
        assertEquals(1, h.size); assertEquals("[фото] смотри", h[0].text)
    }

    @Test fun summaryWithMoment() {
        val r = Prompt.parseSummaryFull("КРАТКО:\nПоссорились.\nФАКТЫ:\n- любит чай\nМОМЕНТ: Первая ссора из-за карты.")
        assertEquals("Поссорились.", r.summary); assertEquals(listOf("любит чай"), r.facts); assertEquals("Первая ссора из-за карты", r.moment)
        assertNull(Prompt.parseSummaryFull("КРАТКО: x\nФАКТЫ:\n- нет\nМОМЕНТ: нет").moment)
    }

    @Test fun russianTagsInTemplate() {
        val sys = Prompt.system(c, Chat(charId = "l"), s, emptyList(), "")
        assertTrue(sys.startsWith("Ты — Лира. Это ролевая переписка, твой собеседник — Эйвин."))
        assertFalse(sys.contains("{{"))
        val old = Prompt.system(c, Chat(charId = "l"), s.copy(systemTemplate = "Я {{char}}, ты {{user}}. {{card}} {{rules}}"), emptyList(), "")
        assertTrue(old.startsWith("Я Лира, ты Эйвин."))
        assertTrue(OLD_TEMPLATES.none { it.contains("{{персонаж}}") })
    }
}
