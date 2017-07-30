package net.dhleong.judo.net

import net.dhleong.judo.IJudoCore
import net.dhleong.judo.JudoCore
import net.dhleong.judo.JudoRendererInfo
import org.apache.commons.net.telnet.EchoOptionHandler
import org.apache.commons.net.telnet.SimpleOptionHandler
import org.apache.commons.net.telnet.TelnetClient
import org.apache.commons.net.telnet.TelnetNotificationHandler
import org.apache.commons.net.telnet.TelnetOption
import org.apache.commons.net.telnet.TelnetOptionHandler
import org.apache.commons.net.telnet.WindowSizeOptionHandler
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class CommonsNetConnection(
    judo: IJudoCore,
    private val address: String, private val port: Int,
    private val echo: (String) -> Unit
) : Connection() {

    override val input: InputStream
    override val output: OutputStream

    override val isMsdpEnabled: Boolean
        get() = msdp.isMsdpEnabled
    override val isGmcpEnabled: Boolean
        get() = gmcp.isGmcpEnabled

    // NOTE: it's a bit weird storing this value in the socketFactory,
    // but we need to update it somehow....
    var debug: Boolean
        get() = socketFactory.debug
        set(value) {
            socketFactory.debug = value
        }

    private val client = TelnetClient()
    private val socketFactory = MccpHandlingSocketFactory(echo)

    private val gmcp: GmcpHandler
    private val msdp: MsdpHandler

    private var echoStateChanges = 0

    private var lastWidth = -1
    private var lastHeight = -1

    init {
        client.connectTimeout = DEFAULT_CONNECT_TIMEOUT
        client.setSocketFactory(socketFactory)
        client.connect(address, port)

        input = MsdpHandler.wrap(client.inputStream)
        output = client.outputStream

        val echoDebug = { text: String ->
            if (debug) {
                echo(text)
            }
        }

        val mtts = MttsTermTypeHandler(judo.renderer, echoDebug)
        msdp = MsdpHandler(judo, { debug }, echoDebug)
        gmcp = GmcpHandler(judo, { debug }, echoDebug)
        client.addOptionHandler(mtts)
        client.addOptionHandler(EchoOptionHandler(false, false, false, true))
        client.addOptionHandler(gmcp)
        client.addOptionHandler(msdp)
        client.addOptionHandler(SimpleOptionHandler(TELNET_TELOPT_MCCP2.toInt(), false, false, true, true))
        client.registerNotifHandler { negotiation_code, option_code ->
            echoDebug("## TELNET < ${stringify(negotiation_code)} ${stringifyOption(option_code)}")

            when (option_code) {
                TelnetOption.ECHO -> {
                    if (echoStateChanges > 5) {
                        // this is probably enough; just ignore it
                        // (nop)
                    } else {
                        ++echoStateChanges
                        val doEcho = echoStateChanges >= 5 ||
                            negotiation_code != TelnetNotificationHandler.RECEIVED_WILL
                        onEchoStateChanged?.invoke(doEcho)
                    }
                }

                TelnetOption.TERMINAL_TYPE -> {
                    if (negotiation_code == TelnetNotificationHandler.RECEIVED_DONT) {
                        mtts.reset()
                    }
                }
            }
        }
    }

    override fun close() {
        try {
            client.disconnect()
        } catch (e: IOException) {
            // ignore?
        }
    }

    override fun send(line: String) {
        if (isTelnetSubsequence(line)) {
            // hold commons.net's hand so it doesn't stomp on the
            // user's IAC codes
            val intArrayLength = line.length - 4 // IAC SE / IAC SB
            val asIntArray = IntArray(intArrayLength)

            // NOTE: `until` creates an IntRange object, so we just
            // do the -1 by hand for now
            for (i in 2..line.length - 2 - 1) {
                asIntArray[i - 2] = line[i].toInt()
            }

            client.sendSubnegotiation(asIntArray)
        } else {
            // regular line; send it regularly
            super.send(line)
        }
    }

    override fun setWindowSize(width: Int, height: Int) {
        if (width == lastWidth && height == lastHeight) return

        val windowSizeHandler = WindowSizeOptionHandler(width, height, false, false, true, false)
        if (lastWidth != -1) {
            // delete the old handler; if lastWidth (or lastHeight) == -1 there
            // is none, and deleting a non-existing handler is apparently an error
            client.deleteOptionHandler(windowSizeHandler.optionCode)
        }

        lastWidth = width
        lastHeight = height
        client.addOptionHandler(windowSizeHandler)
        client.sendSubnegotiation(windowSizeHandler.startSubnegotiationLocal())
    }

    override fun toString(): String {
        return "[$address:$port]"
    }

    private fun stringify(negotiationCode: Int): String =
        when (negotiationCode) {
            TelnetNotificationHandler.RECEIVED_WILL -> "WILL"
            TelnetNotificationHandler.RECEIVED_WONT -> "WONT"
            TelnetNotificationHandler.RECEIVED_DO -> "DO"
            TelnetNotificationHandler.RECEIVED_DONT -> "DONT"
            TelnetNotificationHandler.RECEIVED_COMMAND -> "COMMAND"
            else -> "[$negotiationCode]"
        }

    private fun stringifyOption(optionCode: Int): String =
        when (optionCode) {
            TELNET_TELOPT_MSDP.toInt() -> "MSDP"
            70 -> "MSSP" // server status
            85 -> "MCCP1" // legacy; don't use
            TELNET_TELOPT_MCCP2.toInt() -> "MCCP"
            90 -> "MSP" // mud sound
            91 -> "MXP" // mud extension
            93 -> "ZMP" // zenith mud
            TELNET_TELOPT_GMCP -> "GMCP"
            239 -> "EOR" // used for prompt marking
            249 -> "GA" // used for prompt marking
            else -> {
                val known = TelnetOption.getOption(optionCode)
                if (known == "UNASSIGNED") "[$optionCode]"
                else known
            }
        }

}

class MttsTermTypeHandler(
    private val info: JudoRendererInfo,
    private val echoDebug: (String) -> Unit
)
    : TelnetOptionHandler(TelnetOption.TERMINAL_TYPE, false, false, true, false) {

    private val MTTS_ANSI = 1
    private val MTTS_VT100 = 2
    private val MTTS_UTF8 = 4
    private val MTTS_256COLOR = 8

    private val TELNET_IS = 0.toByte()

    private enum class State {
        CLIENT_NAME,
        TERM_TYPE,
        MTTS_BITVECTOR
    }

    private var state = State.CLIENT_NAME

    override fun answerSubnegotiation(suboptionData: IntArray?, suboptionLength: Int): IntArray {
        val name = getNameForCurrentState()
        advanceState()

        echoDebug("## TELNET > IAC SB TTYPE IS $name")
        return with(name.map { it.toInt() }.toMutableList()) {
            TELNET_IAC
            add(0, TelnetOption.TERMINAL_TYPE)
            add(1, TELNET_IS.toInt())
            toIntArray()
        }
    }

    fun reset() {
        state = State.CLIENT_NAME
    }

    private fun advanceState() {
        state = when (state) {
            State.CLIENT_NAME -> State.TERM_TYPE
            else -> State.MTTS_BITVECTOR
        }
    }

    private fun getNameForCurrentState(): String =
        when (state) {
            State.CLIENT_NAME -> JudoCore.CLIENT_NAME.toUpperCase()
            State.TERM_TYPE -> info.terminalType
            State.MTTS_BITVECTOR -> "MTTS ${buildBitVector()}"
        }

    private fun buildBitVector(): Int {
        var vector = MTTS_ANSI // we always support ansi

        if (info.capabilities.contains(JudoRendererInfo.Capabilities.UTF8)) {
            vector += MTTS_UTF8
        }

        if (info.capabilities.contains(JudoRendererInfo.Capabilities.VT100)) {
            vector += MTTS_VT100
        }

        if (info.capabilities.contains(JudoRendererInfo.Capabilities.COLOR_256)) {
            vector += MTTS_256COLOR
        }

        return vector
    }

}

