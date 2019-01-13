package net.dhleong.judo.jline

import net.dhleong.judo.BlockingKeySource
import net.dhleong.judo.CursorType
import net.dhleong.judo.JudoRendererInfo
import net.dhleong.judo.OnResizedEvent
import net.dhleong.judo.StateMap
import net.dhleong.judo.inTransaction
import net.dhleong.judo.input.Key
import net.dhleong.judo.render.FlavorableCharSequence
import net.dhleong.judo.render.FlavorableStringBuilder
import net.dhleong.judo.render.IJudoBuffer
import net.dhleong.judo.render.IJudoTabpage
import net.dhleong.judo.render.IdManager
import org.jline.terminal.Attributes
import org.jline.terminal.Size
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.terminal.impl.DumbTerminal
import org.jline.utils.Curses
import org.jline.utils.Display
import org.jline.utils.InfoCmp
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicInteger

// ascii codes:
const val KEY_ESCAPE = 27
const val KEY_DELETE = 127

// this is returned by read() when it times out
const val KEY_TIMEOUT = -2

internal const val ELLIPSIS = "…"

/**
 * @author dhleong
 */
class JLineRenderer(
    private val ids: IdManager,
    override var settings: StateMap,
    private val enableMouse: Boolean = false,
    private val renderSurface: JLineDisplay = JLineDisplay(0, 0)
) : IJLineRenderer, BlockingKeySource {

    private val terminal = TerminalBuilder.terminal()!!
    private val window = Display(terminal, true)
    private val originalAttributes: Attributes
    private val keySource: BlockingKeySource = JLineKeySource(terminal)

    internal val windowSize = Size(0, 0)

    override val terminalType: String
        get() = terminal.type

    override val capabilities: EnumSet<JudoRendererInfo.Capabilities>

    override var windowWidth: Int = terminal.width
    override var windowHeight: Int = terminal.height

    override var onResized: OnResizedEvent? = null

    override lateinit var currentTabpage: IJudoTabpage

    private var isLoading: Boolean = true
    private val input = InputLine()
    private val inputHelper = TerminalInputLineHelper(settings)
    private val renderedInput = mutableListOf<FlavorableCharSequence>()
    private var cursorType: CursorType = CursorType.BLOCK

    private val transactionDepth = AtomicInteger(0)

    init {
        terminal.handle(Terminal.Signal.WINCH, this::handleSignal)
        terminal.handle(Terminal.Signal.CONT, this::handleSignal)

        terminal.enterRawMode()

        originalAttributes = terminal.attributes
        val newAttr = Attributes(originalAttributes)
        newAttr.setLocalFlags(EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO, Attributes.LocalFlag.IEXTEN), false)
        newAttr.setInputFlags(EnumSet.of(Attributes.InputFlag.IXON, Attributes.InputFlag.ICRNL, Attributes.InputFlag.INLCR), false)
        newAttr.setControlChar(Attributes.ControlChar.VMIN, 1)
        newAttr.setControlChar(Attributes.ControlChar.VTIME, 0)
        newAttr.setControlChar(Attributes.ControlChar.VINTR, 0)
        terminal.attributes = newAttr

        terminal.puts(InfoCmp.Capability.enter_ca_mode)
        terminal.puts(InfoCmp.Capability.keypad_xmit)

        if (enableMouse) {
            terminal.trackMouse(Terminal.MouseTracking.Normal)
        }

        terminal.flush()

        // now, prepare the initial tabpage
        val tabpageWidth = windowWidth
        val tabpageHeight = windowHeight - 1 // save room for input line
        val initialBuffer = createBuffer()
        val window = JLinePrimaryWindow(this, ids, settings, initialBuffer, tabpageWidth, tabpageHeight)
        window.isFocused = true
        currentTabpage = JLineTabpage(this, ids, settings, window)

        // resize *after* initializing the tabpage
        resize()

        // determine capabilities
        capabilities = EnumSet.of(JudoRendererInfo.Capabilities.UTF8)
        terminal.getNumericCapability(InfoCmp.Capability.max_colors)?.let {
            if (it >= 256) {
                capabilities.add(JudoRendererInfo.Capabilities.COLOR_256)
            }
        }
    }

    override fun validate() {
        if (terminal is DumbTerminal) {
            throw IllegalArgumentException("Unsupported terminal type ${terminal.name}")
        }
    }

    override fun createBuffer(): IJudoBuffer = JLineBuffer(this, ids)

    override fun setCursorType(type: CursorType) = inTransaction {
        cursorType = type
    }

    override fun updateInputLine(line: String, cursor: Int) = inTransaction {
        input.line = FlavorableStringBuilder.withDefaultFlavor(line)
        input.cursorIndex = cursor
    }

    override fun redraw() {
        inTransaction {
            // NOTE: render() is called for us at the end
            // of inTransaction; calling it here would result
            // in double rendering!
//            render()
        }
    }

    override fun setLoading(isLoading: Boolean) {
        if (this.isLoading != isLoading) {
            this.isLoading = isLoading
            render()
        }
    }

    override fun close() {
        window.clear()

        setCursorType(CursorType.BLOCK)
        terminal.puts(InfoCmp.Capability.exit_ca_mode)
        terminal.puts(InfoCmp.Capability.keypad_local)

        if (enableMouse) {
            terminal.trackMouse(Terminal.MouseTracking.Off)
        }

        terminal.flush()
        terminal.attributes = originalAttributes
        terminal.close()
    }

    override fun readKey(): Key? = keySource.readKey()

    override fun beginUpdate() {
        transactionDepth.incrementAndGet()
    }

    override fun finishUpdate() {
        val depth = transactionDepth.decrementAndGet()
        if (depth == 0) {
            render()
        } else if (depth < 0) {
            throw IllegalStateException("Unbalanced call to finishUpdate")
        }
    }

    override fun onWindowResized(window: JLineWindow) {
        // TODO multiple tabpages?
        currentTabpage.apply {
            resize(width, height)
        }
    }

    @Synchronized
    private fun render() {
        val display = renderSurface
        if (display.width <= 0) {
            // not sized yet
            return
        }

        if (isLoading) {
            display.withLine(0, 0) {
                append("Loading...")
            }
            display.cursorRow = windowHeight - 1
            display.cursorCol = 0
        } else {
            render(display)
        }

        if (terminal.width <= 0) {
            // can't safely render to the terminal.
            // this should mostly occur in unit tests, so we
            // do the rendering into the Display above as normal
            return
        }

        window.update(display.toAttributedStrings(), windowSize.cursorPos(
            display.cursorRow,
            display.cursorCol
        ))
        terminal.flush()

        try {
            Curses.tputs(terminal.writer(), cursorType.ansiString)
            terminal.flush()
        } catch (e: NullPointerException) {
            // it's unclear what is causing this NPE (the stack trace
            // seems to indicate it's either the terminal or the cursorType,
            // but neither appears to be possible...
            // Anyway, it only happens from the JLineRenderer constructor,
            // so we can safely ignore it
        }
    }

    // NOTE: render is wrapped with [preventingTransactionRenders] to prevent
    // triggering recursive render loops as a result of a necessary resize
    internal fun render(display: JLineDisplay) = preventingTransactionRenders {
        renderedInput.clear()

        val tabpage = currentTabpage as? JLineTabpage
            ?: throw IllegalStateException("Current tabpage is not JLine: $currentTabpage")

        val win = tabpage.currentWindow
        val isCursorOnStatus = win.isFocusable && win.isFocused && win.statusCursor != -1
        val rawInputCursor = when {
            isCursorOnStatus -> 0
            tabpage.currentWindow.isFocusable -> input.cursorIndex
            else -> 0
        }
        val rawStatusCursor = when {
            isCursorOnStatus -> win.statusCursor
            else -> 0
        }

        val originalCursor = input.cursorIndex
        input.cursorIndex = rawInputCursor
        inputHelper.fitInputLinesToWindow(input, renderedInput)
        input.cursorIndex = originalCursor

        val inputY = tabpage.height
        for (i in renderedInput.indices) {
            val line = renderedInput[i]
            display.withLine(0, inputY + i, lineWidth = windowWidth) {
                line.appendTo(this)
            }
        }

        // resize tabpage to properly to take into account input height
        tabpage.resize(windowWidth, windowHeight - renderedInput.size)
        tabpage.render(display)

        if (isCursorOnStatus) {
            val windowX = 0 // TODO when we add vsplit support
            val windowY = tabpage.getYPositionOf(win)
            val windowBottom = win.height + windowY
            display.cursorRow = windowBottom - renderedInput.size
            display.cursorCol = windowX + rawStatusCursor
        } else {
            display.cursorRow = windowHeight - renderedInput.size + input.cursorRow
            display.cursorCol = input.cursorCol
        }
    }

    private fun resize() {
        val size = terminal.size
        windowSize.copy(size)

        windowHeight = size.rows
        windowWidth = size.columns
        window.resize(windowHeight, windowWidth)
        window.clear()

        updateSize()
    }

    internal fun updateSize() = inTransaction {
        // NOTE: running this in a transaction is important to give our onResized
        // handler a chance to re-render the status lines to fit the new window

        inputHelper.windowWidth = windowWidth
        renderSurface.resize(windowWidth, windowHeight)

        // resize the tabpage *last* so it can safely trigger a render
        currentTabpage.resize(windowWidth, windowHeight - 1)

        onResized?.invoke()
    }

    private fun handleSignal(signal: Terminal.Signal) {
        try {
            when (signal) {
                Terminal.Signal.WINCH -> resize()
                Terminal.Signal.CONT -> {
                    terminal.enterRawMode()
                    resize()
                }
                else -> {
                    // TODO ?
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Runs the block, preventing any [inTransaction] blocks from triggering
     * a render at the end
     */
    private inline fun preventingTransactionRenders(block: () -> Unit) {
        transactionDepth.incrementAndGet()
        try {
            block()
        } finally {
            transactionDepth.decrementAndGet()
        }
    }
}