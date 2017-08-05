package net.dhleong.judo.modes

import net.dhleong.judo.CursorType
import net.dhleong.judo.IJudoCore
import net.dhleong.judo.complete.CompletionSource
import net.dhleong.judo.complete.CompletionSuggester
import net.dhleong.judo.input.InputBuffer
import net.dhleong.judo.input.KeyMapping
import net.dhleong.judo.input.MutableKeys
import net.dhleong.judo.input.keys
import net.dhleong.judo.motions.toEndMotion
import net.dhleong.judo.motions.toStartMotion
import net.dhleong.judo.motions.wordMotion
import net.dhleong.judo.util.InputHistory
import net.dhleong.judo.util.hasCtrl
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/**
 * @author dhleong
 */
class InsertMode(
    judo: IJudoCore,
    buffer: InputBuffer,
    completions: CompletionSource,
    val history: InputHistory
) : BaseModeWithBuffer(judo, buffer),
    MappableMode,
    InputBufferProvider {

    override val userMappings = KeyMapping()
    override val name = "insert"

    private val mapping = KeyMapping(
        keys("<up>") to { _ -> history.scroll(-1, clampCursor = false) },
        keys("<down>") to { _ -> history.scroll(1, clampCursor = false) },

        keys("<alt bs>") to actionOn(wordMotion(-1, false)) { _, range ->
            buffer.deleteWithCursor(range, clampCursor = false)
        },

        // not strictly vim, but nice enough
        keys("<ctrl a>") to motionAction(toStartMotion()),
        keys("<ctrl e>") to motionAction(toEndMotion()),

        keys("<ctrl b>") to { core -> core.scrollPages(1) },
        keys("<ctrl f>") to { core -> core.scrollPages(-1) },

        keys("<ctrl r>") to { core -> core.enterMode("rsearch") }
    )
    private val input = MutableKeys()

    private val suggester = CompletionSuggester(completions)

    override fun onEnter() {
        judo.setCursorType(CursorType.PIPE)
        suggester.reset()
    }

    override fun onExit() {
        buffer.undoMan.finishChange()
    }

    override fun feedKey(key: KeyStroke, remap: Boolean, fromMap: Boolean) {
        when {
            key.keyCode == KeyEvent.VK_ENTER -> {
                judo.send(buffer.toString(), fromMap)
                clearBuffer()
                return
            }

            // NOTE typed events don't have a keyCode, apparently,
            //  so we use keyChar
            key.keyChar == 'c' && key.hasCtrl() -> {
                clearBuffer()
                return
            }

            // NOTE: ctrl+i == tab
            key.keyCode == KeyEvent.VK_TAB
                    || key.keyChar == 'i' && key.hasCtrl() -> {
                performTabCompletionFrom(key, suggester)
                return
            }
        }

        // input changed; suggestions go away
        suggester.reset()

        // handle key mappings
        if (tryMappings(key, remap, input, mapping, userMappings)) {
            // user mappings end the current change set
            buffer.undoMan.finishChange()
            buffer.beginChangeSet()
            return
        }

        if (key.hasCtrl()) {
            // ignore
            return
        }

        // no possible mapping; just update buffer
        buffer.type(key)
    }

    override fun renderInputBuffer(): String = buffer.toString()
    override fun getCursor(): Int = buffer.cursor

    private fun clearBuffer() {
        input.clear()
        buffer.clear()
        history.resetHistoryOffset()
        buffer.undoMan.clear()
    }
}

