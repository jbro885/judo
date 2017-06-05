package net.dhleong.judo

import net.dhleong.judo.util.ansi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import javax.swing.KeyStroke

/**
 * @author dhleong
 */
class JudoCoreTest {

    var windowWidth = 90
    val outputLines = mutableListOf<Pair<String, Boolean>>()

    val renderer: JudoRenderer = Proxy.newProxyInstance(
        ClassLoader.getSystemClassLoader(),
        arrayOf(JudoRenderer::class.java)
    ) { _, method, args ->
        when (method.name) {
            "appendOutput" -> {
                val line = args[0] as String
                val isPartial = args[1] as Boolean

                outputLines.add(line to isPartial)
            }

            "getWindowWidth" -> windowWidth

            "inTransaction" -> {
                @Suppress("UNCHECKED_CAST")
                val block = args[0] as () -> Unit
                block()
            }

            else -> null // ignore
        }
    } as JudoRenderer

    lateinit var judo: JudoCore

    @Before fun setUp() {
        outputLines.clear()
        judo = JudoCore(renderer)
    }

    @Test fun appendOutput() {
        judo.appendOutput("\r\nTake my love,\r\n\r\nTake my land,\r\nTake me")

        assertThat(outputLines)
            .containsExactly(
                ""              to false,
                "Take my love," to false,
                ""              to false,
                "Take my land," to false,
                "Take me"       to true
            )
    }

    @Test fun appendOutput_newlineOnly() {
        judo.appendOutput("\nTake my love,\nTake my land,\n\nTake me")

        assertThat(outputLines)
            .containsExactly(
                ""              to false,
                "Take my love," to false,
                "Take my land," to false,
                ""              to false,
                "Take me"       to true
            )
    }

    @Test fun appendOutput_fancy() {

        judo.appendOutput(
            "\n\r${0x27}[1;30m${0x27}[1;37mTake my love," +
                "\n\r${0x27}[1;30m${0x27}[1;37mTake my land,")

        assertThat(outputLines)
            .containsExactly(
                ""                                          to false,
                "${0x27}[1;30m${0x27}[1;37mTake my love,"   to false,
                "${0x27}[1;30m${0x27}[1;37mTake my land,"   to true
            )
    }

    @Test fun appendOutput_midPartial() {
        judo.appendOutput("\n\rTake my love,\n\rTake my")
        judo.appendOutput(" land,\n\rTake me where...\n\r")
        judo.appendOutput("I don't care, I'm still free")

        assertThat(outputLines)
            .containsExactly(
                ""                              to false,
                "Take my love,"                 to false,
                "Take my"                       to true,
                " land,"                        to false,
                "Take me where..."              to false,
                "I don't care, I'm still free"  to true
            )
    }

    @Test fun buildPromptWithAnsi() {
        val prompt = "${ansi(1,3)}HP: ${ansi(1,6)}42"
        judo.onPrompt(0, prompt)
        windowWidth = 12

        val status = judo.buildStatusLine(object : Mode {
            override val name = "Test"

            override fun feedKey(key: KeyStroke, remap: Boolean) {
                TODO("not implemented")
            }

            override fun onEnter() {
                TODO("not implemented")
            }
        })

        assertThat(status.toAnsiString()).isEqualTo(
            "${ansi(1,3)}HP: ${ansi(fg = 6)}42${ansi(attr = 0)}[TEST]"
        )
    }
}
