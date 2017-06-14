package net.dhleong.judo.motions

import net.dhleong.judo.JudoCore
import net.dhleong.judo.TestableJudoRenderer
import net.dhleong.judo.input.keys
import net.dhleong.judo.setInput
import net.dhleong.judo.type
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

/**
 * @author dhleong
 */
class MotionIntegrationTest {

    val renderer = TestableJudoRenderer()
    lateinit var judo: JudoCore

    @Before fun setUp() {
        judo = JudoCore(renderer)
    }

    @Test fun moveFindBack() {
        judo.setInput("word word2 word3", 11)

        judo.type(keys("Fw"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word2 word3" to 5)
    }

    @Test fun moveFindForward() {
        judo.setInput("word word2 word3", 5)

        judo.type(keys("f<space>"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word2 word3" to 10)
    }

    @Test fun moveWordEmpty() {
        judo.setInput("", 0)

        judo.type(keys("w"))
        assertThat(renderer.outputLines).isEmpty() // no error
        assertThat(renderer.inputLine)
            .isEqualTo("" to 0)

        judo.type(keys("b"))
        assertThat(renderer.outputLines).isEmpty() // no error
        assertThat(renderer.inputLine)
            .isEqualTo("" to 0)
    }

    @Test fun changeBackEmpty() {
        judo.setInput("", 0)

        judo.type(keys("chl"))
        assertThat(renderer.outputLines).isEmpty() // no error
        assertThat(renderer.inputLine)
            .isEqualTo("" to 0) // no change
    }

    @Test fun changeWordForward() {
        judo.setInput("word word2 word3", 5)

        judo.type(keys("cwchanged2 "))
        assertThat(renderer.outputLines).isEmpty() // no error
        assertThat(renderer.inputLine)
            .isEqualTo("word changed2 word3" to 14)
    }

    @Test fun changeEndOfWordForward() {
        judo.setInput("word word2 word3", 5)

        judo.type(keys("cechanged2"))
        assertThat(renderer.outputLines).isEmpty() // no error
        assertThat(renderer.inputLine)
            .isEqualTo("word changed2 word3" to 13)
    }

    @Test fun deleteEndOfWordBack() {
        judo.setInput("word word2 word3", 9)

        judo.type(keys("dge"))
        assertThat(renderer.outputLines).isEmpty() // no error
        assertThat(renderer.inputLine)
            .isEqualTo("word2 word3" to 4)
    }

    @Test fun deleteFindBack() {
        judo.setInput("word word2 word3", 11)

        judo.type(keys("dFw"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word3" to 5)
    }

    @Test fun deleteFindForward() {
        judo.setInput("word word2 word3", 5)

        judo.type(keys("df<space>"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word3" to 5)
    }

    @Test fun deleteUntilBack() {
        judo.setInput("word word2 word3", 11)

        judo.type(keys("dTw"))
        assertThat(renderer.inputLine)
            .isEqualTo("word wword3" to 6)
    }

    @Test fun deleteUntilForward() {
        judo.setInput("word word2 word3", 5)

        judo.type(keys("dt<space>"))
        assertThat(renderer.inputLine)
            .isEqualTo("word  word3" to 5)
    }

    @Test fun deleteWord() {
        judo.setInput("word word2 word3", 16)

        judo.type(keys("<esc>bb"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word2 word3" to 5)

        judo.type(keys("dw"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word3" to 5)
    }

    @Test fun deleteWordBack() {
        judo.setInput("word word2 word3", 16)

        judo.type(keys("<esc>b"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word2 word3" to 11)

        judo.type(keys("db"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word3" to 5)
    }

    @Test fun replaceChar() {
        judo.setInput("word word2 word3", 5)

        // ignore cancel things
        judo.type(keys("r<esc>"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word2 word3" to 5)

        judo.type(keys("r<ctrl c>"))
        assertThat(renderer.inputLine)
            .isEqualTo("word word2 word3" to 5)

        // simple replace
        judo.type(keys("ra"))
        assertThat(renderer.inputLine)
            .isEqualTo("word aord2 word3" to 5)

        judo.type(keys("rb"))
        assertThat(renderer.inputLine)
            .isEqualTo("word bord2 word3" to 5)
    }
}

