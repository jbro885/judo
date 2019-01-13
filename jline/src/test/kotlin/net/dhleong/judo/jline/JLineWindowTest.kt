package net.dhleong.judo.jline

import assertk.Assert
import assertk.assert
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.support.expected
import assertk.assertions.support.show
import com.nhaarman.mockito_kotlin.mock
import net.dhleong.judo.StateMap
import net.dhleong.judo.WORD_WRAP
import net.dhleong.judo.hasSize
import net.dhleong.judo.render.FlavorableStringBuilder
import net.dhleong.judo.render.IJudoWindow
import net.dhleong.judo.render.IdManager
import net.dhleong.judo.render.JudoBuffer
import net.dhleong.judo.util.ansi
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.junit.Test

class JLineWindowTest {

    @Test fun `Simple Window rendering`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("""
            mreynolds
            zoe
        """.trimIndent())

        windowOf(buffer, 10, 3)
            .render(display, 0, 0)

        assert(display).linesEqual("""
            |__________
            |mreynolds_
            |zoe_______
        """.trimMargin())
    }

    @Test fun `Render empty buffer`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("")

        windowOf(buffer, 10, 3)
            .render(display, 0, 0)

        assert(display).linesEqual("""
            |__________
            |_        _
            |__________
        """.trimMargin())
    }

    @Test fun `Hard buffer line breaks`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("""
            captain mreynolds
        """.trimIndent())

        windowOf(buffer, 10, 3)
            .render(display, 0, 0)

        assert(display).linesEqual("""
            |__________
            |captain mr
            |eynolds___
        """.trimMargin())
    }

    @Test fun `Word wrap buffers`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("""
            captain mreynolds
        """.trimIndent())

        windowOf(buffer, 10, 3, wrap = true)
            .render(display, 0, 0)

        assert(display).linesEqual("""
            |__________
            |captain___
            |mreynolds_
        """.trimMargin())
    }

    @Test fun `Max scroll`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("""
            captain
            mreynolds
            first
            mate
            zoe
        """.trimIndent())
        assert(buffer).hasSize(5)

        val w = windowOf(buffer, 10, 3, wrap = true)

        w.scrollLines(4)
        w.render(display, 0, 0)
        assert(w).hasScrollback(4)
        assert(display).linesEqual("""
            |__________
            |__________
            |captain___
        """.trimMargin())

        // now scroll back
        w.scrollLines(-4)
        w.render(display, 0, 0)
        assert(w).hasScrollback(0)
        assert(display).linesEqual("""
            |first_____
            |mate______
            |zoe_______
        """.trimMargin())
    }

    @Test fun `Scrolling in wrapped buffers`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("""
            captain mreynolds
            first mate zoe
        """.trimIndent())
        val w = windowOf(buffer, 10, 3, wrap = true)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |mreynolds_
            |first mate
            |zoe_______
        """.trimMargin())

        w.scrollLines(1)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |captain___
            |mreynolds_
            |first mate
        """.trimMargin())

        w.scrollLines(1)
        assert(w).hasScrollback(1) // should be at line 1 now
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |captain___
            |mreynolds_
        """.trimMargin())

        w.scrollLines(1)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |__________
            |captain___
        """.trimMargin())

        // don't allow scrolling further
        w.scrollLines(1)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |__________
            |captain___
        """.trimMargin())
    }

    @Test fun `Prevent scrolling behind end of buffer`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("""
            captain
            reynolds
        """.trimIndent())
        assert(buffer).hasSize(2)

        val w = windowOf(buffer, 10, 3)
        w.scrollPages(1)
        assert(w).hasScrollback(1)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |__________
            |captain___
        """.trimMargin())

        // scroll back
        w.scrollPages(-1)
        assert(w).hasScrollback(0)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |captain___
            |reynolds__
        """.trimMargin())

        // prevent scrolling any further (at least one line of content on screen)
        w.scrollPages(-1)
        assert(w).hasScrollback(0)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |captain___
            |reynolds__
        """.trimMargin())
    }

    @Test fun `Prevent scrolling past visible buffer contents`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("""
            captain
            reynolds
        """.trimIndent())
        assert(buffer).hasSize(2)

        val w = windowOf(buffer, 10, 3)
        assert(w).hasScrollback(0)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |captain___
            |reynolds__
        """.trimMargin())

        w.scrollPages(1)
        assert(w).hasScrollback(1)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |__________
            |captain___
        """.trimMargin())

        // prevent scrolling any further (at least one line of content on screen)
        w.scrollPages(1)
        assert(w).hasScrollback(1)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |__________
            |captain___
        """.trimMargin())
    }

    @Test fun `Prevent scrolling past visible buffer content when wrapped`() {
        val display = JLineDisplay(10, 3)
        val buffer = bufferOf("""
            captain reynolds
        """.trimIndent())

        val w = windowOf(buffer, 10, 3, wrap = true)
        assert(w).hasScrollback(0)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |captain___
            |reynolds__
        """.trimMargin())

        w.scrollPages(1)
        assert(w).hasScrollback(0)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |__________
            |captain___
        """.trimMargin())

        // prevent scrolling any further (at least one line of content on screen)
        w.scrollPages(1)
        assert(w).hasScrollback(0)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________
            |__________
            |captain___
        """.trimMargin())
    }

    @Test fun `scrollToLine in wrapped buffer`() {
        val display = JLineDisplay(10, 1)
        val buffer = bufferOf("""
            captain mreynolds
            first mate zoe
        """.trimIndent())
        val w = windowOf(buffer, 10, 1, wrap = true)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |zoe_______
        """.trimMargin())

        w.scrollToBufferLine(line = 1)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |first mate
        """.trimMargin())

        w.scrollToBufferLine(line = 0, offsetOnLine = 0)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |captain___
        """.trimMargin())

        w.scrollToBufferLine(line = 0, offsetOnLine = 8)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |mreynolds_
        """.trimMargin())
    }

    @Test fun `Search in buffer`() {
        val display = JLineDisplay(12, 2)
        val buffer = bufferOf("""
            Take My love
            Take my land
            Take me where I cannot stand
        """.trimIndent())
        val w = windowOf(buffer, 12, 2)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |e I cannot s
            |tand________
        """.trimMargin())

        w.searchForKeyword("m", direction = 1)
        w.render(display, 0, 0)
        assert(display).ansiLinesEqual("""
            |Take my land
            |Take ${ansi(inverse = true)}m${ansi(0)}e wher
        """.trimMargin())

        w.searchForKeyword("m", direction = 1)
        w.render(display, 0, 0)
        // NOTE: future work could make search avoid scrolling...
        assert(display).ansiLinesEqual("""
            |Take My love
            |Take ${ansi(inverse = true)}m${ansi(0)}y land
        """.trimMargin())

        // step back
        w.searchForKeyword("m", direction = -1)
        w.render(display, 0, 0)
        assert(display).ansiLinesEqual("""
            |Take my land
            |Take ${ansi(inverse = true)}m${ansi(0)}e wher
        """.trimMargin())

        // go to next page
        w.searchForKeyword("m", direction = 1)
        w.searchForKeyword("m", direction = 1)
        w.render(display, 0, 0)
        assert(display).ansiLinesEqual("""
            |____________
            |Take ${ansi(inverse = true)}M${ansi(0)}y love
        """.trimMargin())
    }

    @Test fun `highlight search result in wrapped buffer`() {
        val display = JLineDisplay(10, 1)
        val buffer = bufferOf("""
            captain mreynolds
            first mate zoe
        """.trimIndent())
        val w = windowOf(buffer, 10, 1, wrap = true)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |zoe_______
        """.trimMargin())

        w.searchForKeyword("rey", 1)
        w.render(display, 0, 0)
        assert(w).hasScrollback(1)
        assert(display).linesEqual("""
            |mreynolds_
        """.trimMargin())

        val displayed = display.toAttributedStrings()
        assert(displayed).hasSize(1)
        assert(displayed[0].toAnsi()).isEqualTo(
            AttributedStringBuilder().apply {
                append("m")
                append("rey", AttributedStyle.INVERSE)
                append("nolds ")
            }.toAttributedString().toAnsi()
        )
    }

    @Test fun `handle search with no result`() {
        val display = JLineDisplay(24, 1)
        val buffer = bufferOf("""
            |captain mreynolds_______
            |first mate zoe__________
        """.trimMargin())
        val w = windowOf(buffer, 24, 1, wrap = true)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |first mate zoe__________
        """.trimMargin())

        w.searchForKeyword("none", 1)
        w.render(display, 0, 0)
        assert(w).hasScrollback(0)
        assert(display).linesEqual("""
            |Pattern not found: none_
        """.trimMargin())
    }

    @Test fun `Maintain scrollback on append`() {
        val display = JLineDisplay(42, 2)
        val buffer = bufferOf("""
            |Take My love
            |Take my land
            |Take me where I cannot stand
        """.trimMargin())
        val w = windowOf(buffer, 42, 2)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |Take my land______________________________
            |Take me where I cannot stand______________
        """.trimMargin())

        // now resize the primary to force wrapping
        display.resize(6, 2)
        w.resize(6, 2)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |nnot s
            |tand__
        """.trimMargin())

        w.scrollPages(1)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |e wher
            |e I ca
        """.trimMargin())

        w.append(FlavorableStringBuilder.withDefaultFlavor("PAR"))
        w.append(FlavorableStringBuilder.withDefaultFlavor("TS\n"))
        w.appendLine("LINES")

        // since we're scrolled, we should stay
        // where we are
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |e wher
            |e I ca
        """.trimMargin())
    }

    @Test fun `Maintain scrollback in offset on append`() {
        val display = JLineDisplay(42, 2)
        val buffer = emptyBuffer().apply {
            append(FlavorableStringBuilder.withDefaultFlavor("Take me where"))
        }
        assert(buffer.size).isEqualTo(1)
        val w = windowOf(buffer, 42, 2)

        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |__________________________________________
            |Take me where_____________________________
        """.trimMargin())

        // now resize the primary to force wrapping
        display.resize(7, 2)
        w.resize(7, 2)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |Take me
            | where_
        """.trimMargin())

        w.scrollLines(1)
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |_______
            |Take me
        """.trimMargin())

        w.append(FlavorableStringBuilder.withDefaultFlavor(" I cannot "))
        w.append(FlavorableStringBuilder.withDefaultFlavor("stand\n"))

        // since we're scrolled, we should stay
        // where we are
        w.render(display, 0, 0)
        assert(display).linesEqual("""
            |_______
            |Take me
        """.trimMargin())
    }
}

private fun Assert<IJudoWindow>.hasScrollback(lines: Int) {
    if (actual.getScrollback() == lines) return
    expected("scrollback=${show(lines)} but was ${show(actual.getScrollback())}")
}

private fun emptyBuffer() = JudoBuffer(IdManager())
private fun bufferOf(contents: String) = emptyBuffer().apply {
    contents.split("\n").forEach {
        appendLine(FlavorableStringBuilder.withDefaultFlavor(it))
    }
}

private fun windowOf(
    buffer: JudoBuffer,
    width: Int,
    height: Int,
    focusable: Boolean = false,
    wrap: Boolean = false
) = JLineWindow(
    mock {},
    IdManager(),
    StateMap().apply {
        this[WORD_WRAP] = wrap
    },
    width,
    height,
    buffer,
    isFocusable = focusable
)