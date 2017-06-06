package net.dhleong.judo.modes

import net.dhleong.judo.IJudoCore
import net.dhleong.judo.InputBufferProvider
import net.dhleong.judo.OperatorFunc
import net.dhleong.judo.input.InputBuffer
import net.dhleong.judo.input.KeyMapping
import net.dhleong.judo.input.MutableKeys
import net.dhleong.judo.input.keys
import net.dhleong.judo.motions.ALL_MOTIONS
import net.dhleong.judo.motions.charMotion
import net.dhleong.judo.motions.toEndMotion
import net.dhleong.judo.motions.toStartMotion
import net.dhleong.judo.motions.xCharMotion
import net.dhleong.judo.util.InputHistory
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/**
 * @author dhleong
 */

class NormalMode(
        judo: IJudoCore,
        buffer: InputBuffer,
        val history: InputHistory,
        val opMode: OperatorPendingMode
) : BaseModeWithBuffer(judo, buffer),
    MappableMode,
    InputBufferProvider {

    override val userMappings = KeyMapping()
    override val name = "normal"

    private val mapping = KeyMapping(listOf(
        keys(":") to { core -> core.enterMode("cmd") },

        keys("a") to { core ->
            applyMotion(charMotion(1))
            core.enterMode("insert")
        },
        keys("A") to { core ->
            applyMotion(toEndMotion())
            core.enterMode("insert")
        },

        keys("c") to { core ->
            opMode.fullLineMotionKey = 'c'
            withOperator { range ->
                buffer.deleteWithCursor(range)
                core.enterMode("insert")
            }
        },
        keys("C") to { core ->
            buffer.delete(rangeOf(toEndMotion()))
            core.enterMode("insert")
        },

        keys("d") to { _ ->
            opMode.fullLineMotionKey = 'd'
            withOperator(buffer::deleteWithCursor)
        },
        keys("D") to { _ ->
            buffer.delete(rangeOf(toEndMotion()))
        },

        keys("G") to { core -> core.scrollToBottom() },

        keys("i") to { core -> core.enterMode("insert") },
        keys("I") to { core ->
            applyMotion(toStartMotion())
            core.enterMode("insert")
        },

        // browse history
        keys("j") to { _ -> history.scroll(1) },
        keys("k") to { _ -> history.scroll(-1) },

        keys("x") to actionOn(xCharMotion(1)) { _, range ->
            buffer.delete(range)
        },
        keys("X") to actionOn(xCharMotion(-1)) { _, range ->
            buffer.delete(range)
            buffer.cursor = range.endInclusive
        },

        keys("<ctrl b>") to { core -> core.scrollPages(1) },
        keys("<ctrl f>") to { core -> core.scrollPages(-1) },
        keys("<ctrl c>") to { _ -> clearBuffer() },
        keys("<ctrl r>") to { core -> core.enterMode("rsearch") }

    ) + ALL_MOTIONS.map { (keys, motion) ->
        keys to motionAction(motion)
    })

    private fun withOperator(action: OperatorFunc) {
        judo.opfunc = action
        fromOpMode = true
        judo.enterMode("op")
    }

    private val input = MutableKeys()

    private var fromOpMode = false

    override fun onEnter() {
        input.clear()

        if (!fromOpMode) {
            buffer.cursor = maxOf(0, buffer.cursor - 1)
        }

        fromOpMode = false
    }

    override fun feedKey(key: KeyStroke, remap: Boolean) {
        if (key.keyCode == KeyEvent.VK_ENTER) {
            judo.send(buffer.toString(), false)
            clearBuffer()
            return
        }

        input.push(key)

        if (remap) {
            userMappings.match(input)?.let {
                input.clear()
                it.invoke(judo)
                return
            }

            if (userMappings.couldMatch(input)) {
                return
            }
        }

        mapping.match(input)?.let {
            input.clear()
            it.invoke(judo)
            return
        }

        if (mapping.couldMatch(input)) {
            return
        }

        input.clear()
    }

    override fun renderInputBuffer(): String = buffer.toString()
    override fun getCursor(): Int = buffer.cursor

    private fun clearBuffer() {
        input.clear()
        buffer.clear()
        history.resetHistoryOffset()
    }
}


