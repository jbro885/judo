package net.dhleong.judo.render

import net.dhleong.judo.net.AnsiFlavorableStringReader

/**
 * @author dhleong
 */
class FlavorableStringBuilder private constructor(
    private var chars: CharArray,
    private var flavors: Array<Flavor?>,
    private var start: Int = 0,
    private var myLength: Int = 0,
    private var expandedTab: String = "  "
): FlavorableCharSequence, Appendable {

    constructor(capacity: Int) : this(
        CharArray(capacity),
        arrayOfNulls(capacity)
    )

    /**
     * Deep copy constructor
     */
    constructor(
        other: FlavorableCharSequence,
        capacity: Int = other.length
    ) : this(capacity) {
        append(other)
    }

    override fun codePointAt(index: Int) = Character.codePointAt(chars, index)

    override fun append(c: Char): Appendable = apply {
        this += c
    }

    fun append(char: Char, flavor: Flavor) {
        if (char == '\t') {
            append(expandedTab, flavor)
            return
        }

        ensureCapacity(myLength + 1)
        chars[start + myLength] = char
        flavors[start + myLength] = flavor
        ++myLength
    }

    override fun append(csq: CharSequence?) = append(csq, 0, csq?.length ?: 0)
    override fun append(csq: CharSequence?, start: Int, end: Int) = apply {
        csq ?: throw IllegalArgumentException()
        if (csq is FlavorableCharSequence) {
            append(csq, start, end)
        } else {
            append(csq.toString(), start, end)
        }
    }

    fun append(string: String, flavor: Flavor) {
        appendImpl(string, 0, string.length, flavor)
    }
    fun append(string: String, offset: Int, end: Int) {
        appendImpl(string, offset, end, when {
            isNotEmpty() -> flavors[start + myLength - 1]
            else -> null
        })
    }
    fun append(string: String, offset: Int, end: Int, flavor: Flavor) {
        appendImpl(string, offset, end, flavor)
    }
    private fun appendImpl(string: String, offset: Int, end: Int, flavor: Flavor? = null) {
        val length = end - offset
        ensureCapacity(myLength + length)

        val appendStart = start + myLength
        string.toCharArray(chars, appendStart, startIndex = offset)
        flavors.fill(flavor, fromIndex = appendStart)

        myLength += length

        var i = appendStart
        val appendEnd = appendStart + length
        while (i < appendEnd) {
            if (chars[i] == '\t') {
                replace(i, i + 1, expandedTab)
                i += expandedTab.length
            } else {
                ++i
            }
        }

    }

    override fun plus(string: String): FlavorableCharSequence =
        FlavorableStringBuilder(this, this.length + string.length).also {
            it += string
        }

    override operator fun plusAssign(char: Char) {
        append(char, when {
            isEmpty() -> Flavor.default
            else -> getFlavor(lastIndex)
        })
    }

    operator fun plusAssign(string: String) {
        append(string, 0, string.length)
    }

    fun append(other: FlavorableCharSequence, offset: Int = 0, end: Int = other.length) {
        val length = end - offset
        ensureCapacity(myLength + length)

        if (other is FlavorableStringBuilder) {
            System.arraycopy(other.chars, other.start + offset, chars, start + myLength, length)
            System.arraycopy(other.flavors, other.start + offset, flavors, start + myLength, length)

            val oldFlavorIndex = start + myLength - 1
            val oldFlavor =
                if (oldFlavorIndex >= 0) flavors[oldFlavorIndex]
                else null
            val newStartingFlavor = oldFlavor + other.flavors[other.start + offset]
            if (newStartingFlavor != null) {
                beginFlavor(newStartingFlavor, myLength)
            }
        } else {
            TODO("support non-FSB-based char sequences?")
        }

        myLength += length
    }

    override fun plusAssign(other: FlavorableCharSequence) {
        append(other, 0, other.length)
    }

    operator fun set(index: Int, value: Char) {
        chars[start + index] = value
    }

    /**
     * Return a shallow copy of a subSequence of this Builder. Subsequent changes to
     * this Builder *will* be reflected in the new instance; if you need a copy that
     * won't change, use [toFlavorableString]
     */
    override fun subSequence(startIndex: Int, endIndex: Int): FlavorableStringBuilder =
        FlavorableStringBuilder(chars, flavors, start + startIndex, endIndex - startIndex)

    override fun splitAtNewlines(
        destination: MutableList<FlavorableCharSequence>,
        continueIncompleteLines: Boolean
    ) {
        if (!continueIncompleteLines && isEmpty()) {
            destination.add(FlavorableStringBuilder.EMPTY)
            return
        }

        var start = 0
        for (i in indices) {
            if (get(i) != '\n') continue

            concatOrAddSubSequenceTo(continueIncompleteLines, destination, start, i + 1)
            start = i + 1
        }

        if (start < length) {
            concatOrAddSubSequenceTo(continueIncompleteLines, destination, start, length)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private fun concatOrAddSubSequenceTo(
        continueIncompleteLines: Boolean,
        destination: MutableList<FlavorableCharSequence>,
        start: Int,
        end: Int
    ) {
        val text = subSequence(start, end)
        if (
            continueIncompleteLines
            && start == 0 // only the first line can continue a previous one
            && !destination.isEmpty()
            && !destination.last().endsWith('\n')
        ) {
            destination.last() += text
        } else {
            destination.add(text)
        }
    }

    /**
     * Return a deep copy of (a subsequence of) this Builder. Subsequent changes to this
     * Builder will not be reflected in the new instance
     */
    fun toFlavorableString(startIndex: Int = 0, endIndex: Int = myLength): FlavorableCharSequence =
        FlavorableStringBuilder(endIndex - startIndex).also {
            it.append(this, startIndex, endIndex)
        }

    override fun clearFlavor(startIndex: Int, endIndex: Int) {
        flavors.fill(Flavor.default, fromIndex = startIndex, toIndex = endIndex)
    }

    override fun getFlavor(index: Int): Flavor = flavors[start + index]
        ?: throw IllegalStateException("No flavors at $index")

    override fun addFlavor(flavor: Flavor, startIndex: Int, endIndex: Int) {
        flavors.forEachIndexed { index, old ->
            flavors[index] = old + flavor
        }
    }

    override fun beginFlavor(flavor: Flavor, startIndex: Int) {
        for (i in start + startIndex until flavors.size) {
            if ((flavors[i] ?: flavor) != flavor) {
                break
            }

            flavors[i] = flavor
        }
    }

    override fun setFlavor(flavor: Flavor, startIndex: Int, endIndex: Int) {
        flavors.fill(
            flavor,
            fromIndex = start + startIndex,
            toIndex = start + endIndex
        )
    }

    override val length: Int
        get() = myLength

    override fun get(index: Int): Char = chars[start + index]

    override fun toString(): String = String(chars, start, myLength)

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is FlavorableCharSequence) return false
        if (myLength != other.length) return false

        for (i in 0 until myLength) {
            if (this[i] != other[i]) return false
            if (getFlavor(i) != other.getFlavor(i)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = myLength
        for (i in start until start + myLength) {
            result = 31 * result + chars[i].hashCode()
            result = 31 * result + flavors[i].hashCode()
        }
        return result
    }


    private fun ensureCapacity(newCapacity: Int) {
        if (start + newCapacity <= chars.size) return // good to go

        val actualCapacity = newCapacity + 32
        val newChars = CharArray(actualCapacity)
        System.arraycopy(chars, start, newChars, 0, myLength)
        chars = newChars

        val newFlavors = arrayOfNulls<Flavor>(actualCapacity)
        System.arraycopy(flavors, start, newFlavors, 0, myLength)
        flavors = newFlavors
        start = 0

        // TODO fill the last flavor up to the capacity?
    }

    fun replace(start: Int, end: Int, string: String) {
        if (start < 0) throw IndexOutOfBoundsException()
        if (start >= myLength) throw IndexOutOfBoundsException()
        if (end < 0) throw IndexOutOfBoundsException()
        if (end > myLength) throw IndexOutOfBoundsException()
        if (end < start) throw IllegalArgumentException()

        val toBeReplacedLen = end - start
        val stringLen = string.length
        val newLength = myLength + stringLen - toBeReplacedLen
        ensureCapacity(newLength)

        // move characters as appropriate
        System.arraycopy(
            chars, this.start + end,
            chars,
            this.start + start + stringLen,
            myLength - end
        )

        // then flavor
        System.arraycopy(
            flavors,
            this.start + end,
            flavors,
            this.start + start + stringLen,
            myLength - end
        )

        // now copy in the replacement
        string.toCharArray(
            chars,
            this.start + start,
            0,
            stringLen
        )
        myLength = newLength

        // try to expand the flavor that was there
        if (
            stringLen > toBeReplacedLen
            && start + toBeReplacedLen - 1 > 0
        ) {
            setFlavor(
                // expand the existing flavor
                flavors[this.start + start + toBeReplacedLen - 1] ?: Flavor.default,
                startIndex = start,
                endIndex = start + stringLen
            )
        }
    }

    fun reset() {
        start = 0
        myLength = 0
    }

    fun setLength(newLength: Int) {
        myLength = newLength
    }

    companion object {
        val EMPTY: FlavorableCharSequence = fromString("")

        fun fromString(string: String) = FlavorableStringBuilder(string.length).also {
            it += string
        }

        fun withDefaultFlavor(string: String) = fromString(string).apply {
            beginFlavor(Flavor.default, 0)
        }
    }
}

fun CharSequence.toFlavorable() = when (this) {
    is FlavorableCharSequence -> this
    else -> FlavorableStringBuilder(length).also { builder ->
        builder += this.toString()
    }
}

fun FlavorableCharSequence.asFlavorableBuilder() = when (this) {
    is FlavorableStringBuilder -> this
    else -> FlavorableStringBuilder(length).also {
        it.append(this)
    }
}

fun String.parseAnsi(): FlavorableCharSequence {
    // TODO avoid having to copy the char array?
    val chars = toCharArray()
    return AnsiFlavorableStringReader().feed(chars).first()
}
