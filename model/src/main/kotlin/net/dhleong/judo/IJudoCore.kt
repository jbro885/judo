package net.dhleong.judo

import javax.swing.KeyStroke

/**
 * @author dhleong
 */
interface IJudoCore {

    val aliases: IAliasManager

    fun echo(vararg objects: Any?)
    fun feedKey(stroke: KeyStroke, remap: Boolean = true)
    fun send(text: String)
    fun enterMode(modeName: String)

    fun map(mode: String, from: String, to: String, remap: Boolean)
    fun quit()
}

