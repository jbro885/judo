package net.dhleong.judo.modes.cmd

import assertk.assert
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.dhleong.judo.render.Flavor
import net.dhleong.judo.render.FlavorableStringBuilder
import net.dhleong.judo.render.JudoColor
import net.dhleong.judo.render.SimpleFlavor
import net.dhleong.judo.script.ScriptingEngine
import net.dhleong.judo.trigger.TriggerManager
import net.dhleong.judo.util.ansi
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * @author dhleong
 */
@RunWith(Parameterized::class)
class CmdModeTriggerTest(
    factory: ScriptingEngine.Factory
) : AbstractCmdModeTest(factory) {

    @Test fun `trigger() basic`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
                |def handleTrigger(): print("awesome")
                |trigger('cool', handleTrigger)
            """.trimMargin()

            SupportedScriptTypes.JS -> """
                trigger('cool', function handleTrigger() {
                    print("awesome");
                });
            """.trimIndent()
        })
        assert(judo.triggers.hasTriggerFor("cool")).isTrue()

        judo.triggers.process("this is cool")
        assert(judo.prints).containsExactly("awesome")
    }

    @Test fun `trigger() as decorator`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
                |@trigger('cool')
                |def handleTrigger(): print("awesome")
            """.trimMargin()

            SupportedScriptTypes.JS -> return
        })
        assert(judo.triggers.hasTriggerFor("cool")).isTrue()

        judo.triggers.process("this is cool")
        assert(judo.prints).containsExactly("awesome")
    }

    @Test fun `trigger() with regex`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
            import re
            @trigger(re.compile('cool(.*)'))
            def handleTrigger(thing): print("awesome%s" % thing)
            """.trimIndent()

            SupportedScriptTypes.JS -> """
                trigger(/cool(.*)/, function(thing) {
                    print("awesome" + thing);
                });
            """.trimIndent()
        })

        judo.triggers.process("cool story bro")
        assert(judo.prints).containsExactly("awesome story bro")
    }

    @Test fun `trigger() strips color by default`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
                |@trigger('cool $1')
                |def handleTrigger(thing): print("awesome %s" % thing)
            """.trimMargin()

            SupportedScriptTypes.JS -> """
                trigger('cool $1', function(thing) {
                    print("awesome " + thing);
                });
            """.trimIndent()
        })

        judo.triggers.process(`cool story`())
        assert(judo.prints).containsExactly("awesome story")
    }

    @Test fun `trigger(_, 'color') preserves ANSI`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
                |@trigger('cool $1', 'color')
                |def handleTrigger(thing): print("awesome %s" % thing)
            """.trimMargin()

            SupportedScriptTypes.JS -> """
                trigger('cool $1', 'color', function(thing) {
                    print("awesome " + thing);
                });
            """.trimIndent()
        })

        judo.triggers.process(`cool story`())
        assert(judo.prints).hasSize(1)
        assert(judo.prints[0] as String).isEqualTo(
            "awesome ${ansi(1,2)}st${ansi(fg=3)}or${ansi(fg=4)}y${ansi(0)}")
    }

    @Test fun `trigger() ending in dot works`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
                |@trigger('cool.')
                |def handleTrigger(): print("awesome.")
            """.trimMargin()

            SupportedScriptTypes.JS -> """
                trigger('cool.', function() {
                    print("awesome.");
                });
            """.trimIndent()
        })
        assert(judo.triggers.hasTriggerFor("cool.")).isTrue()

        judo.triggers.process("cool.")
        assert(judo.prints).containsExactly("awesome.")
    }

    @Test fun `trigger() supports multiple decorators`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
                |@trigger('cool')
                |@trigger('shiny')
                |def handleTrigger(): print("awesome")
            """.trimMargin()

            // no decorator support:
            SupportedScriptTypes.JS -> return
        })
        assert(judo.triggers.hasTriggerFor("cool")).isTrue()
        assert(judo.triggers.hasTriggerFor("shiny")).isTrue()

        judo.triggers.process("this is cool")
        assert(judo.prints).containsExactly("awesome")

        judo.triggers.process("this is shiny")
        assert(judo.prints).containsExactly("awesome", "awesome")
    }

    @Test fun `trigger() and alias() decorators stack`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
                |@trigger('cool')
                |@alias('shiny')
                |def handleTriggerOrAlias(): print("awesome")
            """.trimMargin()

            // no decorators
            SupportedScriptTypes.JS -> return
        })

        assert(judo.triggers.hasTriggerFor("cool")).isTrue()
        assert(judo.triggers.hasTriggerFor("shiny")).isFalse()
        assert(judo.aliases.hasAliasFor("cool")).isFalse()
        assert(judo.aliases.hasAliasFor("shiny")).isTrue()

        judo.triggers.process("this is cool")
        assert(judo.prints).containsExactly("awesome")

        judo.send("shiny", fromMap = false)
        assert(judo.sends).isEmpty()
        assert(judo.prints).containsExactly("awesome", "awesome")
    }

    @Test fun `trigger() only matches complete lines`() {
        mode.execute(when (scriptType()) {
            SupportedScriptTypes.PY -> """
                |@trigger('cool $1')
                |def handleTrigger(thing): print("awesome %s" % thing)
            """.trimMargin()

            SupportedScriptTypes.JS -> """
                trigger('cool $1', function(thing) {
                    print("awesome " + thing);
                });
            """.trimIndent()
        })

        judo.triggers.process(`cool story`().apply {
            removeTrailingNewline()
        })
        judo.triggers.process(`cool story`().apply {
            removeTrailingNewline()
            append("bro\n")
        })
        assert(judo.prints).containsExactly("awesome storybro")
    }

    private fun `cool story`() = FlavorableStringBuilder(64).apply {
        append("cool ", Flavor.default)
        append("st", SimpleFlavor(
            isBold = true,
            hasForeground = true,
            foreground = JudoColor.Simple.from(2)
        ))
        append("or", SimpleFlavor(
            isBold = true,
            hasForeground = true,
            foreground = JudoColor.Simple.from(3)
        ))
        append("y", SimpleFlavor(
            isBold = true,
            hasForeground = true,
            foreground = JudoColor.Simple.from(4)
        ))
        append("\n")
    }
}

fun TriggerManager.process(string: String) = process(FlavorableStringBuilder.withDefaultFlavor(
    string + "\n"
))