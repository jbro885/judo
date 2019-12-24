package net.dhleong.judo

import assertk.Assert
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.support.expected
import assertk.assertions.support.show
import net.dhleong.judo.render.FlavorableCharSequence
import net.dhleong.judo.render.IJudoBuffer

/**
 * @author dhleong
 */
fun Assert<IJudoBuffer>.hasLines(vararg expectedStrings: String) = given { actual ->
    val actualStrings = (0..actual.lastIndex).map { actual[it].toString() }
    assertThat(actualStrings, "buffer lines").isEqualTo(expectedStrings.toList())
}
fun Assert<IJudoBuffer>.hasLinesSomewhere(vararg expectedStrings: String) = given { actual ->
    val actualStrings = (0..actual.lastIndex).map { actual[it].toString() }
    val firstIndex = actualStrings.indexOf(expectedStrings[0])
    if (firstIndex != -1) {
        val subset = actualStrings.subList(firstIndex, firstIndex + expectedStrings.size)
        assertThat(subset, "buffer lines").isEqualTo(expectedStrings.toList())
        return
    }

    expected("buffer of ${show(actualStrings)} to contain the sequence: ${show(expectedStrings)}")
}

fun Assert<IJudoBuffer>.doesNotHaveLine(line: FlavorableCharSequence) = given { actual ->
    for (i in 0..actual.lastIndex) {
        assertThat(actual[i] != line, "line $i doesn't match `$line`").isTrue()
    }
}

fun Assert<IJudoBuffer>.hasSize(size: Int) = given { actual ->
    if (actual.size == size) return
    expected("size = ${show(size)} but was ${show(actual.size)}")
}
