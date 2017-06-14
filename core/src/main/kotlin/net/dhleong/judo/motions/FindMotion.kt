package net.dhleong.judo.motions

/**
 * @author dhleong
 */

fun findMotion(step: Int): Motion {
    val flags =
        if (step > 0) listOf(Motion.Flags.INCLUSIVE)
        else emptyList()
    return createMotion(flags) { readKey, buffer, start ->
        val target = readKey()
        val end: Int
        if (step > 0) {
            end = buffer.indexOf(target.keyChar, start + 1)
        } else {
            end = buffer.lastIndexOf(target.keyChar, start - 1)
        }

        if (end == -1) {
            start..start
        } else {
            start..end
        }
    }
}

fun tilMotion(step: Int): Motion {
    val base = findMotion(step)
    val flags =
        if (step > 0) listOf(Motion.Flags.INCLUSIVE)
        else emptyList()
    return createMotion(flags) { readKey, buffer, cursor ->
        val baseRange = base.calculate(readKey, buffer, cursor)
        baseRange.start..(baseRange.endInclusive - step)
    }
}

