package net.dhleong.judo.render

import net.dhleong.judo.JudoRenderer
import net.dhleong.judo.StateMap
import net.dhleong.judo.search.BufferSearcher

/**
 * Core, non-rendering-dependent [IJudoWindow] implementations
 *
 * @author dhleong
 */
abstract class BaseJudoWindow(
    private val renderer: JudoRenderer,
    ids: IdManager,
    protected val settings: StateMap,
    initialWidth: Int,
    initialHeight: Int,
    override val isFocusable: Boolean = false,
    val statusLineOverlaysOutput: Boolean = false
) : IJudoWindow {

    override val id = ids.newWindow()
    override var width: Int = initialWidth
    override var height: Int = initialHeight

    protected val search = BufferSearcher()

    override fun append(text: FlavorableCharSequence) = currentBuffer.append(text)
    override fun appendLine(line: FlavorableCharSequence) = currentBuffer.appendLine(line)
    override fun appendLine(line: String) = appendLine(
        FlavorableStringBuilder.withDefaultFlavor(line)
    )

    override fun scrollPages(count: Int) {
        scrollLines(height * count)
    }

    override fun searchForKeyword(word: CharSequence, direction: Int) {
        val ignoreCase = true // TODO smartcase setting?

        val buffer = currentBuffer
        val found = search.searchForKeyword(
            buffer,
            getScrollback(),
            word,
            direction,
            ignoreCase
        )

        if (!found) {
            // TODO bell?
            renderer.echo("Pattern not found: $word".toFlavorable())
            return
        }

        scrollToBufferLine(search.resultLine, offsetOnLine = search.resultOffset)
    }

}