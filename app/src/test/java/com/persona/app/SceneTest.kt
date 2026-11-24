package com.persona.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTest {
    private val lira = Character(id = "l", name = "Лира", tagline = "Бард", moodEnabled = true)
    private val rein = Character(id = "r", name = "Рейн", tagline = "Капитан")
    private val sc = Scene(id = "s", name = "Таверна", world = "Портовый город, шторм", charIds = listOf("l", "r"))
    private val s = Settings(userName = "Эйвин")

    @Test fun moodTagParsedAndHidden() {
        val (t, mood, d) = Prompt.parseMood("*смеётся* Ну ты даёшь!\n[настроение: игривое; отношение: +2]")
        assertEquals("*смеётся* Ну ты даёшь!", t); assertEquals("игривое", mood); assertEquals(2, d)
        assertEquals("Привет", Prompt.hideTags("Привет\n[настроение: рад"))
        assertEquals(-3, Prompt.parseMood("ok [Настроение: злое; отношение: −5]").third)
    }

    @Test fun systemHasMoodRuleAndState() {
        val sys = Prompt.system(lira, Chat(charId = "l", mood = "обиженное", affinity = 30), s, emptyList(), "")
        assertTrue(sys.contains("обиженное")); assertTrue(sys.contains("холодное")); assertTrue(sys.contains("[настроение:"))
        assertFalse(Prompt.system(rein, Chat(charId = "r"), s, emptyList(), "").contains("[настроение:"))
    }

    @Test fun routineAndPersona() {
        val c = lira.copy(routineEnabled = true, routine = "23–7 спит")
        val sys = Prompt.system(c, Chat(charId = "l"), s.copy(userAbout = "рыжий алхимик"), emptyList(), "")
        assertTrue(sys.contains("23–7 спит")); assertTrue(sys.contains("Сейчас ")); assertTrue(sys.contains("рыжий алхимик"))
        val forChat = Settings(userName = "Я", personas = listOf(Persona("p", "Эйвин", "бард")), defaultPersonaId = "p").forChat(Chat(charId = "l"))
        assertEquals("Эйвин", forChat.user)
    }

    @Test fun respondersByNameAndRound() {
        val chat = Chat(charId = "", sceneId = "s", messages = listOf(Msg(fromUser = false, variants = listOf("Йо"), speaker = "l")))
        assertEquals(listOf("r"), Prompt.responders(sc, listOf(lira, rein), chat, "Рейну это не понравится").map { it.id })
        assertEquals(listOf("l"), Prompt.responders(sc, listOf(lira, rein), chat, "ну и что дальше?").map { it.id })
        assertEquals(listOf("r"), Prompt.responders(sc.copy(turnMode = TurnMode.ROUND), listOf(lira, rein), chat, "ну").map { it.id })
    }

    @Test fun sceneReplyViewpoint() {
        val chat = Chat(charId = "", sceneId = "s", hero = HeroStats(hp = 15), messages = listOf(
            Msg(fromUser = false, variants = listOf("Дождь стучит."), speaker = NARRATOR),
            Msg(fromUser = false, variants = listOf("Привет, новенький."), speaker = "l"),
            Msg(fromUser = true, variants = listOf("Рейн, куда плывём?")),
        ))
        val r = Prompt.sceneReply(sc, rein, listOf(lira, rein), chat, s, emptyList(), local = true)
        println(r.system); println(r.history)
        assertTrue(r.system.contains("ТОЛЬКО Рейн.")); assertTrue(r.system.contains("здоровье 15/20"))
        assertTrue(r.history.last().user)
        assertTrue(r.history.last().text.contains("Лира: Привет, новенький.")); assertTrue(r.history.last().text.contains("Эйвин: Рейн, куда плывём?"))
        val n = Prompt.narrator(sc, listOf(lira, rein), chat, s, emptyList(), true)
        assertTrue(n.system.contains("рассказчик")); assertTrue(n.history.last().user)
    }

    @Test fun heroTags() {
        val h = Prompt.applyHero("Тебя ранили. [HP -3] Зато нашёл кошель [золото +5]", HeroStats(hp = 10, gold = 1))
        assertEquals(7, h.hp); assertEquals(6, h.gold)
        assertEquals("Тебя ранили.  Зато нашёл кошель", Prompt.hideTags("Тебя ранили. [HP -3] Зато нашёл кошель [золото +5]"))
    }

    @Test fun parsesGeneratedCharacter() {
        val t = """**ИМЯ:** Мира
СЛОГАН: Ведьма-аптекарь с острым языком
ОПИСАНИЕ: Держит лавку в порту.
ХАРАКТЕР: циничная, заботливая
ОТНОШЕНИЯ: постоянный покупатель
ПРИВЕТСТВИЕ: *поднимает бровь* Опять ты?
ПРИМЕРЫ:
{{char}}: Ещё одно зелье?
{{user}}: Последнее!
ТЕГИ: фэнтези, ведьма, юмор"""
        val c = Prompt.parseCharacter(t, Character(avatar = "a.jpg"))
        assertEquals("Мира", c.name); assertEquals("*поднимает бровь* Опять ты?", c.greetings[0]); assertEquals("a.jpg", c.avatar)
        assertTrue(c.definition.contains("циничная")); assertTrue(c.definition.contains("{{user}}: Последнее!"))
        assertEquals(listOf("фэнтези", "ведьма", "юмор"), c.tags)
    }
}
