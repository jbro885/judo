package net.dhleong.judo.util

import org.jline.utils.AttributedCharSequence
import org.jline.utils.AttributedStringBuilder

private val ESCAPE_CODE_SEARCH_LIMIT = 8

/**
 * @author dhleong
 */
class ReplaceableAttributedStringBuilder(capacity: Int)
        : AttributedStringBuilder(capacity), IStringBuilder {

    internal var postEscapePartials: StringBuilder? = null

    constructor(ansiString: String) : this(ansiString.length) {
        appendAnsi(ansiString)

        preserveHangingAnsi(ansiString)
    }

    fun appendAndAdoptStyle(string: AttributedCharSequence) {
        // quick shortcut
        if (string.isEmpty()) return

        if (postEscapePartials != null) {
            append(joinTrailingPartialsTo(string))
        } else {
            // FIXME: we seem to need to do it this way for now
            // see: JLineRendererTest.appendOutput_resumePartial_continueAnsi2
            appendAnsi(string.toAnsi())
        }

        if (string is ReplaceableAttributedStringBuilder && string.postEscapePartials != null) {
            // adopt its trailing partials; if we had any,
            // they were already joined above
            postEscapePartials = string.postEscapePartials
        }

        style(string.styleAt(string.lastIndex))
    }

    /**
     * Persist the current style through the given count,
     *  then restore length
     */
    fun persistStyle(count: Int) {
        val len = length

        append(FakeCharSequence(count))

        setLength(len)
    }

    override fun replace(start: Int, end: Int, str: String) {
        var actualEnd = end

        if (start < 0)
            throw StringIndexOutOfBoundsException(start)
        if (start > length)
            throw StringIndexOutOfBoundsException("start > length()")
        if (start > actualEnd)
            throw StringIndexOutOfBoundsException("start > end")

        if (actualEnd > length)
            actualEnd = length
        val len = str.length
        val newCount = length + len - (actualEnd - start)
        ensureCapacity(newCount)
        val buffer = buffer()

        System.arraycopy(buffer, actualEnd, buffer, start + len, length - actualEnd)
        str.toCharArray(buffer, start)
        setLength(newCount)
    }

    override fun toAnsiString(): String =
        toAnsi()

    private fun preserveHangingAnsi(ansiString: String) {
        if (this.isEmpty() && !ansiString.isEmpty()) {
            // all ansi?
            postEscapePartials = StringBuilder(ansiString)
            return
        } else if (this.isEmpty()) {
            // both empty...?
            return
        }

        // as long as the string char != this[lastIndex], it
        // was stripped as a partial ansi code.
        val lastNonAnsi = this.last()
        var i = ansiString.length
        while (i > 0 && ansiString[i - 1] != lastNonAnsi) {
            --i
        }

        if (i < ansiString.length) {
            postEscapePartials = StringBuilder(ESCAPE_CODE_SEARCH_LIMIT)
                .append(ansiString, i, ansiString.length)
        }
    }

    private fun joinTrailingPartialsTo(string: AttributedCharSequence): AttributedCharSequence {
        val partials = postEscapePartials

        // we just copy over the new strings partials (if it has them) anyway,
        // so no point keeping this around
        postEscapePartials = null

        partials?.let {
            val partialEndedAt = (0 until minOf(string.length, ESCAPE_CODE_SEARCH_LIMIT))
                .firstOrNull { string[it] == 'm' }
                ?: -1

            if (partialEndedAt != -1) {
                it.append(string, 0, partialEndedAt + 1)
                appendAnsi(it.toString())
                return string.subSequence(partialEndedAt + 1, string.length)
            } else if (it.last() == 'm') {
                // full, trailing ansi
                appendAnsi(it.toString())
            }
        }

        return string
    }

}

private class FakeCharSequence(override val length: Int) : CharSequence {
    override fun get(index: Int): Char = 0.toChar()

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        FakeCharSequence(endIndex - startIndex)
}

