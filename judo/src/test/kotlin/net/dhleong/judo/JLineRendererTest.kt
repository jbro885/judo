package net.dhleong.judo

import net.dhleong.judo.util.IStringBuilder
import net.dhleong.judo.util.ansi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * @author dhleong
 */
class JLineRendererTest {
    // TODO test scrollback

    @Test fun appendOutput_empty() {
        val renderer = JLineRenderer()
        renderer.windowWidth = 42

        // basically make sure it doesn't crash
        renderer.appendOutput(IStringBuilder.from(""), isPartialLine = true)
        renderer.appendOutput(IStringBuilder.from(""), isPartialLine = false)

        assertThat(renderer.getOutputLines())
            .containsExactly("")
    }

    @Test fun appendOutput_resumePartial() {
        // append without a line end and continue that line
        // in a separate append. It's TCP so stuff happens
        val renderer = JLineRenderer()
        renderer.windowWidth = 42

        renderer.appendOutput("", isPartialLine = false)
        renderer.appendOutput("Take my love,", isPartialLine = false)
        renderer.appendOutput("Take my", isPartialLine = true)
        renderer.appendOutput(" land,", isPartialLine = false)
        renderer.appendOutput("Take me where...", isPartialLine = false)
        renderer.appendOutput("I don't care, I'm still free", isPartialLine = false)

        assertThat(renderer.getOutputLines())
            .containsExactly(
                "",
                "Take my love,",
                "Take my land,",
                "Take me where...",
                "I don't care, I'm still free"
            )
        assertThat(renderer.getScrollback()).isEqualTo(0)
    }

    @Test fun appendOutput_resumePartial_continueAnsi() {
        // append without a line end and continue that line
        // in a separate append. It's TCP so stuff happens
        val renderer = JLineRenderer()
        renderer.windowWidth = 42

        renderer.appendOutput("", isPartialLine = false)
        renderer.appendOutput("${ansi(1,6)}Take my ", isPartialLine = true)
        renderer.appendOutput("love", isPartialLine = false)

        assertThat(renderer.getOutputLines())
            .containsExactly(
                "",
                "${ansi(1,6)}Take my love${ansi(0)}"
            )
        assertThat(renderer.getScrollback()).isEqualTo(0)
    }

    @Test fun appendOutput_resumePartial_continueAnsi2() {
        val renderer = JLineRenderer()
        renderer.windowWidth = 42

        // continuation of the partial line has its own ansi;
        // use previous line's ansi to start, but don't stomp
        // on the new ansi
        val first = IStringBuilder.from("${ansi(1,6)}Take my ")
        val second = IStringBuilder.from("lo${ansi(1,7)}ve")
        renderer.appendOutput(first, isPartialLine = true)
        renderer.appendOutput(second, isPartialLine = false)

        assertThat(renderer.getOutputLines())
            .containsExactly(
                "${ansi(1,6)}Take my lo${ansi(fg=7)}ve${ansi(0)}"
            )
        assertThat(renderer.getScrollback()).isEqualTo(0)
    }

    @Test fun appendOutput_resumePartial_trailingAnsi() {
        // append without a line end and continue that line
        // in a separate append. It's TCP so stuff happens
        val renderer = JLineRenderer()
        renderer.windowWidth = 42

        val trailingAnsiLine = IStringBuilder.from("${ansi(1,6)}Take my ${ansi(1,2)}")
        renderer.appendOutput("", isPartialLine = false)
        renderer.appendOutput(trailingAnsiLine, isPartialLine = true)
        renderer.appendOutput("love", isPartialLine = false)

        assertThat(renderer.getOutputLines())
            .containsExactly(
                "",
                "${ansi(1,6)}Take my ${ansi(fg=2)}love${ansi(0)}"
            )
        assertThat(renderer.getScrollback()).isEqualTo(0)
    }

    @Test fun appendOutput_resumePartial_splitAnsi() {
        // append without a line end and continue that line
        // in a separate append. It's TCP so stuff happens
        val renderer = JLineRenderer()
        renderer.windowWidth = 42

        val ansi = ansi(1,2)
        val firstHalf = ansi.slice(0..3)
        val secondHalf = ansi.slice(4..ansi.lastIndex)
        assertThat("$firstHalf$secondHalf").isEqualTo(ansi.toString())

        renderer.appendOutput(
            IStringBuilder.from("${ansi(1,6)}Take my $firstHalf"),
            isPartialLine = true)
        renderer.appendOutput(
            IStringBuilder.from("${secondHalf}love"),
            isPartialLine = false)

        assertThat(renderer.getOutputLines())
            .containsExactly(
                "${ansi(1,6)}Take my ${ansi(fg=2)}love${ansi(0)}"
            )
        assertThat(renderer.getScrollback()).isEqualTo(0)
    }

    @Test fun appendOutput_resumePartial_splitAnsi_integration() {
        // we've tested the core handling above, so let's make sure
        // JudoCore integrates into it correctly
        val renderer = JLineRenderer()
        renderer.windowWidth = 42
        val core = JudoCore(renderer)

        val ansi = ansi(fg=2)
        val firstHalf = ansi.slice(0..ansi.lastIndex-1)
        val secondHalf = ansi.slice(ansi.lastIndex..ansi.lastIndex)
        assertThat("$firstHalf$secondHalf").isEqualTo(ansi.toString())
        assertThat("$secondHalf").isEqualTo("m")

        val lineOne = "${ansi(1,1)}Take my $firstHalf"
        val lineTwo = "${secondHalf}l${ansi(1,6)}ove"

        core.onIncomingBuffer(lineOne.toCharArray(), lineOne.length)
        core.onIncomingBuffer(lineTwo.toCharArray(), lineTwo.length)

        if (renderer.getOutputLines()[0].startsWith("ERROR")) {
            throw AssertionError(renderer.getOutputLines().joinToString("\n"))
        }

        assertThat(renderer.getOutputLines()[0])
            .isEqualTo("${ansi(1,1)}Take my ${ansi(fg=2)}l${ansi(fg=6)}ove${ansi(0)}")
    }

    @Test fun fitInputLineToWindow() {
        val renderer = JLineRenderer()
        renderer.windowWidth = 12

        renderer.updateInputLine("Take my love, Take my land... ", 0)
        renderer.fitInputLineToWindow().let {
            val (line, cursor) = it
            assertThat(line.toString()).isEqualTo("Take my lov…")
            assertThat(cursor).isEqualTo(0)
        }

        renderer.updateInputLine("Take my love, Take my land... ", 14)
        renderer.fitInputLineToWindow().let {
            val (line, cursor) = it
            assertThat(line.toString()).isEqualTo("… love, Tak…")
            assertThat(cursor).isEqualTo(8)
        }

        renderer.updateInputLine("Take my love, Take my land... ", 19)
        renderer.fitInputLineToWindow().let {
            val (line, cursor) = it
            assertThat(line.toString()).isEqualTo("… Take my l…")
            assertThat(cursor).isEqualTo(7)
        }

        renderer.updateInputLine("Take my love, Take my land... ", 30)
        renderer.fitInputLineToWindow().let {
            val (line, cursor) = it
            assertThat(line.toString()).isEqualTo("…d... ")
            assertThat(cursor).isEqualTo(6)
        }
    }

    @Test fun fitInputLineToWindow_type() {
        val renderer = JLineRenderer()
        renderer.windowWidth = 12

        renderer.typeAndFit("Take my love",
            expected = "… love" to 6)

        renderer.typeAndFit("Take my love,",
            expected = "… love," to 7)
    }

    @Test fun fitInputLineToWindow_type_page3() {
        val renderer = JLineRenderer()
        renderer.windowWidth = 12

        renderer.typeAndFit("Take my love, take my lan",
            expected = "…my lan" to 7)

        renderer.typeAndFit("Take my love, take my land",
            expected = "…my land" to 8)

        renderer.typeAndFit("Take my love, take my land,",
            expected = "…my land," to 9)

        renderer.typeAndFit("Take my love, take my land, ",
            expected = "…my land, " to 10)

        renderer.typeAndFit("Take my love, take my land, t",
            expected = "…my land, t" to 11)

        renderer.typeAndFit("Take my love, take my land, ta",
            expected = "…d, ta" to 6)

        renderer.typeAndFit("Take my love, take my land, tak",
            expected = "…d, tak" to 7)
    }

    private fun JLineRenderer.typeAndFit(text: String, expected: Pair<String, Int>) {
        updateInputLine(text, text.length)

        val (line, cursor) = fitInputLineToWindow()
        assertThat(line.toString() to cursor).isEqualTo(expected)
    }
}

