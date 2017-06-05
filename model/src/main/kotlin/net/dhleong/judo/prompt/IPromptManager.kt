package net.dhleong.judo.prompt

import net.dhleong.judo.alias.AliasProcesser

/**
 * Prompts are like Aliases that act on output and
 * only ever return an empty string, instead passing
 * replacement value to the [net.dhleong.judo.JudoRenderer]
 *
 * @author dhleong
 */

interface IPromptManager {
    fun clear()

    fun define(inputSpec: String, outputSpec: String)
    fun define(inputSpec: String, parser: AliasProcesser)

    fun process(input: CharSequence, onPrompt: (index: Int, prompt: String) -> Unit): CharSequence

    val size: Int
}