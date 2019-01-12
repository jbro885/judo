package net.dhleong.judo.trigger

import net.dhleong.judo.alias.AliasManager
import net.dhleong.judo.alias.AliasProcesser
import net.dhleong.judo.render.FlavorableCharSequence
import net.dhleong.judo.render.asFlavorableBuilder
import net.dhleong.judo.util.PatternSpec

/**
 * @author dhleong
 */
class TriggerManager : ITriggerManager {

    val aliases = AliasManager()

    override fun clear() =
        aliases.clear()

    override fun define(inputSpec: String, parser: TriggerProcessor) {
        aliases.define(inputSpec, toAliasProcessor(parser))
    }

    override fun define(inputSpec: PatternSpec, parser: TriggerProcessor) {
        aliases.define(inputSpec, toAliasProcessor(parser))
    }


    override fun process(input: FlavorableCharSequence) {
        val toProcess = input.asFlavorableBuilder()

        // in general, it *should* have a newline
        val hadNewline = input.endsWith('\n')
        if (hadNewline) {
            // don't process with trailing newlines
            toProcess.setLength(input.length - 1)
        }

        aliases.process(toProcess)

        if (hadNewline && toProcess.isNotEmpty()) {
            // restore the newline
            toProcess += '\n'
        }

    }

    override fun undefine(inputSpec: String) {
        aliases.undefine(inputSpec)
    }

    override fun clear(entry: String) = undefine(entry)

    fun hasTriggerFor(inputSpec: String): Boolean =
        aliases.hasAliasFor(inputSpec)

    override fun toString(): String =
        StringBuilder(1024).apply {
            appendln("Triggers:")
            appendln("=========")
            aliases.describeContentsTo(this)
        }.toString()

    private fun toAliasProcessor(parser: TriggerProcessor): AliasProcesser =
        { args ->
            parser.invoke(args)
            "" // ignore result
        }
}
