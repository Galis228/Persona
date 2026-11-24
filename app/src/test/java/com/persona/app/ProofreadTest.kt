package com.persona.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofreadTest {
    private val orig = "*улыбается* Я так рада тебя видить! Пойдём в таверну, там сегодня играет новый бардов."

    @Test fun acceptsSmallFixes() {
        assertTrue(Prompt.acceptProofread(orig, "*улыбается* Я так рада тебя видеть! Пойдём в таверну, там сегодня играет новый бард."))
    }

    @Test fun rejectsRewritesAndJunk() {
        assertFalse(Prompt.acceptProofread(orig, orig))
        assertFalse(Prompt.acceptProofread(orig, ""))
        assertFalse(Prompt.acceptProofread(orig, "Вот исправленный текст: *улыбается* Я так рада тебя видеть! Пойдём в таверну, там сегодня играет новый бард. Ошибки: видить → видеть."))
        assertFalse(Prompt.acceptProofread(orig, "улыбается Я так рада тебя видеть! Пойдём в таверну, там сегодня играет новый бард."))
        assertFalse(Prompt.acceptProofread(orig, "*смеётся* Как же здорово встретиться! Айда в трактир, сегодня выступает незнакомый музыкант."))
    }

    @Test fun softerPresets() {
        Preset.entries.forEach { assertTrue(it.rep <= 1.06f) }
    }
}
