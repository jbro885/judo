package net.dhleong.judo.trigger

import net.dhleong.judo.alias.AliasManager

/**
 * @author dhleong
 */
class TriggerManager : ITriggerManager {

    val aliases = AliasManager()

    override fun clear() =
        aliases.clear()

    override fun define(inputSpec: String, parser: TriggerProcessor) {
        aliases.define(inputSpec, { args ->
            parser.invoke(args)
            "" // ignore result
        })
    }

    override fun process(input: CharSequence) {
        aliases.process(input)
    }

    fun hasTriggerFor(inputSpec: String): Boolean =
        aliases.hasAliasFor(inputSpec)

}