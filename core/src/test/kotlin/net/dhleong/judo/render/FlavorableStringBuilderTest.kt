package net.dhleong.judo.render

import assertk.Assert
import assertk.all
import assertk.assert
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.support.expected
import assertk.assertions.support.show
import org.junit.Test

class FlavorableStringBuilderTest {
    @Test fun `Basic toString()`() {
        assert(FlavorableStringBuilder.fromString("mreynolds"))
            .hasToString("mreynolds")
    }

    @Test fun `Basic append`() {
        val b = FlavorableStringBuilder.fromString("Mal")
        b += " Reynolds"
        assert(b).all {
            hasToString("Mal Reynolds")
            hasLength(12)
        }
    }

    @Test fun `Continue old flavor if not otherwise specified in append (String)`() {
        val b = FlavorableStringBuilder.fromString("Mal")
        b.beginFlavor(SimpleFlavor(isBold = true), 0)

        b += " Reynolds"
        assert(b).all {
            hasToString("Mal Reynolds")
            hasFlavor(SimpleFlavor(isBold = true))
            hasLength(12)
        }
    }

    @Test fun `Continue old flavor if not otherwise specified in append (Flavorable)`() {
        val b = FlavorableStringBuilder.fromString("Mal")
        b.beginFlavor(SimpleFlavor(isBold = true), 0)

        b += FlavorableStringBuilder.fromString(" Reynolds")
        assert(b).all {
            hasToString("Mal Reynolds")
            hasFlavor(SimpleFlavor(isBold = true))
            hasLength(12)
        }
    }

    @Test fun `Keep new flavor in append`() {
        val b = FlavorableStringBuilder.fromString("Mal").apply {
            beginFlavor(SimpleFlavor(isBold = true), 0)
        }

        b += FlavorableStringBuilder.fromString(" Reynolds").apply {
            beginFlavor(SimpleFlavor(isItalic = true), 0)
        }
        assert(b).all {
            hasToString("Mal Reynolds")
            hasFlavor(SimpleFlavor(isBold = true), untilIndex = 3)
            hasFlavor(SimpleFlavor(isItalic = true), atIndex = 3)
            hasLength(12)
        }
    }

    @Test fun `Keep partial new flavor in append`() {
        val b = FlavorableStringBuilder.fromString("Mal").apply {
            beginFlavor(SimpleFlavor(isBold = true), 0)
        }

        b += FlavorableStringBuilder.fromString(" Reynolds").apply {
            beginFlavor(SimpleFlavor(isItalic = true), 4)
        }
        assert(b).all {
            hasToString("Mal Reynolds")
            hasFlavor(SimpleFlavor(isBold = true), untilIndex = 7)
            hasFlavor(SimpleFlavor(isItalic = true), atIndex = 7)
            hasLength(12)
        }
    }

    @Test fun `subSequence works as expected`() {
        val b = FlavorableStringBuilder.fromString("mreynolds")
        val sub = b.subSequence(1, 4)
        assert(sub).hasToString("rey")
    }

    @Test fun `append to subSequence works as expected`() {
        val b = FlavorableStringBuilder.fromString("mreynolds")
        val sub = b.subSequence(1, 4)
        sub += "noldo"
        assert(sub).hasToString("reynoldo")
    }

    @Test fun `append subSequence to subSequence works as expected`() {
        val b = FlavorableStringBuilder.fromString("mreynolds")
        val sub1 = b.subSequence(1, 4)
        val sub2 = b.subSequence(4, 8)

        sub1 += sub2
        assert(sub1).hasToString("reynold")
    }

    @Test fun `subSequence edge cases`() {
        val b = FlavorableStringBuilder.fromString("mreynolds")
        assert(b.subSequence(0, 0)).hasToString("")
        assert(b.subSequence(0, 9)).hasToString("mreynolds")
        assert(b.subSequence(9, 9)).hasToString("")
    }

    @Test fun `splitAtNewlines() handles the empty string`() {
        val b = FlavorableStringBuilder.fromString("")
        assert(b).splitsAtNewlinesToStrings(
            ""
        )
    }

    @Test fun `splitAtNewlines() handles only newline`() {
        val b = FlavorableStringBuilder.fromString("\n")
        assert(b).splitsAtNewlinesToStrings(
            "\n"
        )
    }

    @Test fun `splitAtNewlines() handles the no-newline case`() {
        val b = FlavorableStringBuilder.fromString("Take my love")
        assert(b).splitsAtNewlinesToStrings(
            "Take my love"
        )
    }

    @Test fun `splitAtNewlines() handles the single-newline case`() {
        val b = FlavorableStringBuilder.fromString("Take my love\n")
        assert(b).splitsAtNewlinesToStrings(
            "Take my love\n"
        )
    }

    @Test fun `splitAtNewlines() includes the newlines`() {
        val b = FlavorableStringBuilder.fromString("Take\nmy\nlove\n")
        assert(b).splitsAtNewlinesToStrings(
            "Take\n",
            "my\n",
            "love\n"
        )
    }

    @Test fun `splitAtNewlines() can continue a single line`() {
        val b = FlavorableStringBuilder.fromString(" love")
        assert(b).splitsAtNewlinesToStrings(
            "Take my love",
            continueLines = mutableListOf("Take my")
        )
    }

    @Test fun `splitAtNewlines() can continue multiple lines`() {
        val b = FlavorableStringBuilder.fromString("ke\nmy\nlove\n")
        assert(b).splitsAtNewlinesToStrings(
            "Take\n",
            "my\n",
            "love\n",
            continueLines = mutableListOf("Ta")
        )
    }

    @Test fun `replace() at beginning`() {
        val b = FlavorableStringBuilder.fromString("Took my love")
        b.replace(0, 4, "Take")
        assert(b).hasToString("Take my love")
    }

    @Test fun `replace() at beginning with more characters`() {
        val b = FlavorableStringBuilder.fromString("Oh my love").apply {
            beginFlavor(SimpleFlavor(isItalic = true), 2)
            beginFlavor(SimpleFlavor(isBold = true), 0)
        }

        b.replace(0, 2, "Take")
        assert(b).all {
            hasToString("Take my love")

            hasFlavor(SimpleFlavor(isBold = true), untilIndex = 4)
            hasFlavor(SimpleFlavor(isItalic = true), atIndex = 4)
        }
    }

    @Test fun `replace() at beginning with fewer characters`() {
        val b = FlavorableStringBuilder.fromString("Take my love").apply {
            beginFlavor(SimpleFlavor(isItalic = true), 4)
            beginFlavor(SimpleFlavor(isBold = true), 0)
        }

        b.replace(0, 4, "Oh")
        assert(b).all {
            hasToString("Oh my love")

            hasFlavor(SimpleFlavor(isBold = true), untilIndex = 2)
            hasFlavor(SimpleFlavor(isItalic = true), atIndex = 2)
        }
    }
}

private fun Assert<FlavorableCharSequence>.hasFlavor(
    flavor: Flavor,
    atIndex: Int = 0,
    untilIndex: Int = actual.length
) {
    for (i in atIndex until untilIndex) {
        val thisFlavor = actual.getFlavor(i)
        if (thisFlavor != flavor) {
            expected(
                "from ${show(atIndex)} until ${show(untilIndex)} ${show(flavor)};" +
                "\n encountered at ${show(i)}: ${show(thisFlavor)}"
            )
            return
        }
    }
}

private fun Assert<FlavorableCharSequence>.hasToString(string: String) {
    assert(actual.toString()).isEqualTo(string)
}

private fun Assert<FlavorableCharSequence>.splitsAtNewlinesToStrings(
    vararg strings: String,
    continueLines: MutableList<String>? = null
) {
    val split = continueLines?.map {
        FlavorableStringBuilder.withDefaultFlavor(it) as FlavorableCharSequence
    }?.toMutableList() ?: mutableListOf()
    actual.splitAtNewlines(split, continueIncompleteLines = continueLines != null)
    assert(split.map { it.toString() }).isEqualTo(strings.toList())
}