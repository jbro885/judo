package net.dhleong.judo.logging

import net.dhleong.judo.util.ESCAPE_CHAR
import java.io.IOException
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date



/**
 * @author dhleong
 */
class HtmlLogFormatter : ILogFormatter {

    override val format = ILogManager.Format.HTML

    private val dateFormatter = SimpleDateFormat(DATE_FORMAT)

    private val STYLES =
        """
        body {
            background-color: #000;
            font-family: Consolas, Monaco, Lucida Console, Courier New, Courier, monospace;
        }
        .i { font-style: italic; }
        .u { text-decoration: underline; }
        .s { text-decoration: line-through; }
        .s.u { text-decoration: line-through underline; }
		.d0 { color: #000; } .l0 { color: #555; } .b40 { background-color: #000; } .b50 { background-color: #555 }
		.d1 { color: #B00; } .l1 { color: #F55; } .b41 { background-color: #B00; } .b51 { background-color: #F55 }
		.d2 { color: #0B0; } .l2 { color: #5F5; } .b42 { background-color: #0B0; } .b52 { background-color: #5F5 }
		.d3 { color: #BB0; } .l3 { color: #FF5; } .b43 { background-color: #BB0; } .b53 { background-color: #FF5 }
		.d4 { color: #00B; } .l4 { color: #55F; } .b44 { background-color: #00B; } .b54 { background-color: #55F }
		.d5 { color: #B0B; } .l5 { color: #F5F; } .b45 { background-color: #B0B; } .b55 { background-color: #F5F }
		.d6 { color: #0BB; } .l6 { color: #5FF; } .b46 { background-color: #0BB; } .b56 { background-color: #5FF }
		.d7 { color: #BBB; } .l7 { color: #FFF; } .b47 { background-color: #BBB; } .b57 { background-color: #FFF }
		.d9 { color: #FFF; } .l9 { color: #FFF; } .b49 { background-color: #000; } .b59 { background-color: #000 }
        """.trimIndent()

    private val HEADER_FORMAT =
        """
        <!doctype html>
        <html>
          <head>
            <meta charset="utf-8">
            <meta name="description" content="Generated by Judo on %s">
            <meta name="generator" content="Judo - https://github.com/dhleong/judo">
            <meta http-equiv="X-UA-Compatible" content="IE=edge">
            <meta
                name="viewport"
                content="width=device-width, initial-scale=1, user-scalable=0, maximum-scale=1, minimum-scale=1"
            >
            <style type="text/css">
            $STYLES
            </style>
          </head>
          <body>
            <pre>
              <span>
        """.trimIndent()

    private val FOOTER =
        """
              </span>
        """
    // NOTE: if we print these in the footer, it breaks append mode;
    // omitting them makes it not quite proper HTML, but I don't think
    // any modern browsers care
//            </pre>
//          </body>
//        </html>
//        """

    private val COLOR_256_BLOCKS = charArrayOf('0', '5', '8', 'B', 'D', 'F')

    private val stylist = AnsiStylist()

    override fun writeHeader(out: Writer) {
        out.append(HEADER_FORMAT.format(dateFormatter.format(Date())))
    }

    override fun writeLine(input: CharSequence, out: Writer) {
        var i = 0
        val end = input.length
        while (i < end) {
            val ch = input[i]
            when (ch) {
                ESCAPE_CHAR -> {
                    i = stylist.readAnsi(input, i)
                    out.append("</span><span")
                    appendStyle(out)
                    out.append(">")
                }

                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")

                else -> out.append(ch)
            }

            ++i
        }

        out.append('\n')
    }

    override fun writeFooter(out: Writer) {
        out.append(FOOTER)
    }

    internal fun appendStyle(out: Writer) {
        val simpleFg = stylist.fg in 0..7
        val simpleBg = stylist.bg in 0..7
        if (stylist.hasStyle() || simpleFg || simpleBg) {
            out.write(" class=\"")

            var first = true
            if (stylist.italic) first = writeClass(out, "i", first)
            if (stylist.underline) first = writeClass(out, "u", first)
            if (stylist.strikethrough) first = writeClass(out, "s", first)

            if (simpleFg) first = writeClass(out, pickFgClass(), first)
            if (simpleBg) writeClass(out, pickBgClass(), first)

            out.write("\"")
        }

        val hasHexFg = stylist.fg < -1 || stylist.fg > 7
        val hasHexBg = stylist.bg < -1 || stylist.bg > 7
        if (hasHexFg || hasHexBg) {
            out.write(" style=\"")

            if (hasHexFg) {
                out.write("color: ")
                writeIntColor(stylist.fg, out)
                out.write(";")
            }
            if (hasHexBg) {
                out.write("background-color: ")
                writeIntColor(stylist.bg, out)
                out.write(";")
            }

            out.write("\"")
        }
    }

    private fun pickBgClass(): String =
        "b%d".format(
            if (stylist.bg < 8) 40 + stylist.bg
            else 50 + stylist.bg - 8
        )

    private fun pickFgClass(): String =
        (if (stylist.bold) "l%d"
         else "d%d").format(stylist.fg)

    private fun writeClass(out: Writer, className: String, first: Boolean): Boolean {
        if (!first) out.append(' ')
        out.append(className)
        return false
    }

    fun writeIntColor(intColor: Int, out: Writer) {
        if (intColor < -1) {
            // "true" color
            val red = (intColor shr 16) and 0xFF
            val green = (intColor shr 8) and 0xFF
            val blue = intColor and 0xFF

            writeRgb(red, green, blue, out)
        } else if (intColor < 16) {
            // high intensity color
            out.write(when (intColor) {
                8 -> "#888"
                9 -> "#F00"
                10 -> "#0F0"
                11 -> "#FF0"
                12 -> "#00F"
                13 -> "#F0F"
                14 -> "#0FF"
                else -> "#FFF"
            })
        } else if (intColor < 232) {
            // 256 color (actually, 216 colors):
            // the red increases one "block" (see constant above) every 36 numbers;
            // the green increases one "block" every 6 numbers;
            // and blue increases each step (within each 6-block of green)
            val color = intColor - 16
            val redPart = color / 36
            val greenPart = (color % 36) / 6
            val bluePart = (color % 6)

            out.apply {
                write("#")
                append(COLOR_256_BLOCKS[redPart])
                append(COLOR_256_BLOCKS[greenPart])
                append(COLOR_256_BLOCKS[bluePart])
            }
        } else {
            // grayscale from black to white in 24 steps (IE [0,23])
            // each one is ~10, but just multiplying by 10 doesn't get
            // us very bright at 255 (actually it's 230). So, we'll just
            // add 8 like on wikipedia
            val gray = (intColor - 232) * 10 + 8
            writeRgb(gray, gray, gray, out)
        }
    }

    private fun writeRgb(red: Int, green: Int, blue: Int, out: Writer) {
        out.apply {
            write("rgb(")
            write(red.toString())
            write(",")
            write(green.toString())
            write(",")
            write(blue.toString())
            write(")")
        }
    }
}

internal class AnsiStylist {
    var fg = -1
    var bg = -1
    var bold = false
    var faint = false
    var italic = false
    var underline = false
    var strikethrough = false

    private val ansiWorkspace = StringBuilder()

    // temporary state to avoid passing around args everywhere
    // this makes us not threadsafe, but I can't imagine why
    // we would ever need to be
    private var offset = -1
    private lateinit var input: CharSequence

    fun hasStyle() = bold || faint || italic || underline || strikethrough

    /**
     * @return The new offset position
     */
    fun readAnsi(input: CharSequence, offset: Int): Int {
        var i = offset
        if (input[i++] != ESCAPE_CHAR) throw IllegalArgumentException("No escape sequence found")
        if (input[i++] != '[') return i // just the

        this.offset = i - 1 // start ON the [ for readNextIntPart to work
        this.input = input

        while (input[this.offset] != 'm') {
            val number = readNextIntPart()
            when (number) {
                -1 -> return this.offset // unexpected end
                0 -> reset()

            // add flags
                1 -> bold = true
                2 -> faint = true
                3 -> italic = true
                4 -> underline = true
//                        5 -> blink
//                        7 -> inverse
                9 -> strikethrough = true

            // remove flags
                21 -> bold = false
                22 -> {
                    bold = false
                    faint = false
                }
                23 -> italic = false
                24 -> underline = false
                29 -> strikethrough = false

            // basic colors
                in 30..37 -> fg = (number - 30)
                39 -> fg = -1

                in 40..47 -> bg = (number - 40)
                49 -> bg = -1

            // fancy colors
                38 -> fg = readColor()
                48 -> bg = readColor()

            // high-intensity colors
                in 90..97 -> fg = (number - 90 + 8)
                in 100..107 -> fg = (number - 100 + 8)
            }
        }

        return this.offset
    }

    private fun readColor(): Int {
        val int = readNextIntPart()
        return when (int) {
            -1 -> throw IOException("Unexpected end of sequence")

            2 -> readTrueColor()
            5 -> read256Color()

            else -> throw IllegalArgumentException("Unexpected color type $int")
        }
    }

    private fun readTrueColor(): Int {
        val r = readNextIntPart()
        val g = readNextIntPart()
        val b = readNextIntPart()

        if (r == -1 || g == -1 || b == -1) {
            throw IOException("Unexpected end of sequence")
        }

        // NOTE: we don't actually use the alpha channel, and
        // instead basically use it as a marker that we're using
        // full rgb color; since -1 on a color means "none," we
        // use FE instead of FF so #FFFFFF isn't interpreted as
        // "no color"
        return 0xFE shl 24 or (r shl 16) or (g shl 8) or b
    }

    private fun read256Color(): Int {
        val color = readNextIntPart()
        if (color == -1) throw IOException("Unexpected end of sequence")
        return color
    }

    private fun readNextIntPart(): Int {
        // NOTE: we should always start either on the opening `[`
        // or a separating `;`
        var i = offset + 1
        val input = this.input
        val end = input.length
        while (i < end) {
            val ch = input[i]
            when (ch) {
                'm', ';' -> {
                    val number = ansiWorkspace.toInt()
                    ansiWorkspace.setLength(0)

                    // done!
                    offset = i
                    return number
                }

                else -> ansiWorkspace.append(ch)
            }

            ++i
        }

        return -1
    }

    fun reset() {
        fg = -1
        bg = -1
        bold = false
        faint = false
        italic = false
        underline = false
        strikethrough = false
    }
}

/**
 * Zero-allocation toInt for very simple integers
 */
private fun CharSequence.toInt(): Int {
    var int = 0

    for (i in 0..lastIndex) {
        int = (int * 10) + (this[i] - '0')
    }

    return int
}
