package net.dhleong.judo.jline.stacks

import net.dhleong.judo.jline.IJLineWindow
import net.dhleong.judo.jline.JLineDisplay

const val WINDOW_MIN_HEIGHT = 2

/**
 * @author dhleong
 */
class VerticalStack(
    parent: IStack,
    width: Int,
    height: Int
) : BaseStack(parent, width, height),
    StackWindowCommandHandler by parent // delegate by default
{

    override fun add(item: IStack) {
        if (contents.isNotEmpty()) {
            var available = height
            for (row in contents) {
                available -= row.height
            }

            // reduce the size of other buffers to make room for the new item
            if (available < item.height) {
                val otherHeightDelta = (item.height - available) / contents.size
                for (row in contents) {
                    row.resize(width, maxOf(
                        WINDOW_MIN_HEIGHT,
                        row.height - otherHeightDelta
                    ))
                }
            }
        }

        // insert at the top
        // TODO vim has settings (and function args) for where to insert the new window...
        contents.add(0, item)
        resize(width, height)
    }

    override fun getCollapseChild(): IStack? {
        if (contents.size == 1) {
            return contents[0]
        }

        return null
    }

    override fun render(display: JLineDisplay, x: Int, y: Int) {
        // vertical stack is easy
        var line = y
        for (i in contents.indices) {
            val row = contents[i]

            row.render(display, x, line)
            line += row.height
        }
    }

    override fun getYPositionOf(window: IJLineWindow): Int {
        var yOffset = 0
        for (row in contents) {
            val rowY = row.getYPositionOf(window)
            if (rowY != -1) return yOffset + rowY

            yOffset += row.height
        }

        // not in this stack
        return -1
    }

    override fun remove(child: IStack) {
        if (!contents.remove(child)) {
            throw IllegalArgumentException("$child not contained in $this")
        }
    }

    override fun resize(width: Int, height: Int) {
        var availableHeight = height

        val last = contents.lastIndex
        for (i in contents.indices) {
            val item = contents[i]
            val requestedHeight = item.height
            val remainingRows = last - i
            val allottedHeight =
                if (remainingRows == 0) availableHeight
                else maxOf(
                    WINDOW_MIN_HEIGHT,
                    minOf(requestedHeight,
                        // leave room for the remaining rows
                        availableHeight - WINDOW_MIN_HEIGHT * remainingRows)
                )
            availableHeight -= allottedHeight

            item.resize(width, allottedHeight)
        }
    }

    override fun focusUp(search: CountingStackSearch) = focus(search, -1, IStack::focusUp)
    override fun focusDown(search: CountingStackSearch) = focus(search, 1, IStack::focusDown)

}
