package com.persona.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HangoutTest {
    private val a = Character(id = "a", name = "Лира")
    private val b = Character(id = "b", name = "Рейн")
    private val c = Character(id = "c", name = "Мира")
    private val sc = Scene(id = "s", name = "Болталка", charIds = listOf("a", "b", "c"), mode = SceneMode.HANGOUT)
    private val s = Settings(userName = "Эйвин")

    @Test fun hangoutPromptIsCasualWithoutNarrator() {
        val chat = Chat(charId = "", sceneId = "s", messages = listOf(Msg(fromUser = true, variants = listOf("Всем привет!"))))
        val r = Prompt.sceneReply(sc, a, listOf(a, b, c), chat, s, emptyList(), local = true)
        assertTrue(r.system.contains("Общий чат «Болталка»"))
        assertTrue(r.system.contains("групповом мессенджере"))
        assertFalse(r.system.contains("здоровье"))
        assertFalse(r.system.contains("рассказчик и мастер"))
    }

    @Test fun respondersNamedAndBounded() {
        val chat = Chat(charId = "", sceneId = "s")
        assertEquals("b", Prompt.responders(sc, listOf(a, b, c), chat, "Рейн, как дела?", Random(1)).first().id)
        repeat(50) { seed ->
            val r = Prompt.responders(sc, listOf(a, b, c), chat, "ну что там", Random(seed))
            assertTrue(r.size in 1..2); assertEquals(r.size, r.map { it.id }.distinct().size)
        }
    }

    @Test fun mentionsFindOthers() {
        assertEquals(listOf("c"), Prompt.mentioned("Мира, а ты что думаешь?", listOf(a, b, c), except = "a").map { it.id })
        assertTrue(Prompt.mentioned("Привет всем", listOf(a, b, c)).isEmpty())
    }
}
