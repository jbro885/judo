package net.dhleong.judo.script

import net.dhleong.judo.IJudoCore
import net.dhleong.judo.JudoRendererInfo
import net.dhleong.judo.StateMap
import net.dhleong.judo.alias.AliasProcesser
import net.dhleong.judo.alias.IAliasManager
import net.dhleong.judo.alias.compileSimplePatternSpec
import net.dhleong.judo.event.IEventManager
import net.dhleong.judo.logging.ILogManager
import net.dhleong.judo.mapping.IJudoMap
import net.dhleong.judo.mapping.IMapManagerPublic
import net.dhleong.judo.modes.ScriptExecutionException
import net.dhleong.judo.prompt.IPromptManager
import net.dhleong.judo.render.IJudoBuffer
import net.dhleong.judo.render.IJudoTabpage
import net.dhleong.judo.render.IJudoWindow
import net.dhleong.judo.trigger.ITriggerManager
import net.dhleong.judo.util.PatternMatcher
import net.dhleong.judo.util.PatternProcessingFlags
import net.dhleong.judo.util.PatternSpec
import org.python.core.JyAttribute
import org.python.core.Py
import org.python.core.PyCallIter
import org.python.core.PyException
import org.python.core.PyFunction
import org.python.core.PyIterator
import org.python.core.PyModule
import org.python.core.PyObject
import org.python.core.PyObjectDerived
import org.python.core.PyStringMap
import org.python.core.PyType
import org.python.core.adapter.PyObjectAdapter
import org.python.modules.sre.MatchObject
import org.python.modules.sre.PatternObject
import org.python.util.PythonInterpreter
import java.io.File
import java.io.InputStream
import java.util.EnumSet
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * @author dhleong
 */
class JythonScriptingEngine : ScriptingEngine {
    class Factory : ScriptingEngine.Factory {
        override val supportsDecorators: Boolean
            get() = true

        override fun supportsFileType(ext: String): Boolean = ext == "py"

        override fun create(): ScriptingEngine = JythonScriptingEngine()

        override fun toString(): String = "PY ScriptingFactory (Jython)"
    }

    private val python = PythonInterpreter()
    private val keepModules = HashSet<String>() // FIXME TODO initialize this
    private val globals = PyGlobals()

    init {
        InterfaceAdapter.init()

        // the naming here is insane, but correct
        python.locals = globals
    }

    override fun onPostRegister() {
        // add everything as a module
        val asModule = PyModule("judo", globals)
        val modules = python.systemState.modules as PyStringMap
        modules.__setitem__("judo", asModule)

        // don't override our input()!!
        val builtins = modules.__getitem__("__builtin__")
        builtins.dict.__setitem__("input", globals.__getitem__("input"))

        modules.keys().asIterable().forEach {
            keepModules.add(it.asString())
        }
    }

    override fun register(entity: JudoScriptingEntity) {
        when (entity) {
            is JudoScriptingEntity.Constant<*> -> {
                globals[entity.name] = entity.value!!
            }

            is JudoScriptingEntity.Function<*> -> {
                globals[entity.name] = entity.toPyFn()
            }
        }
    }

    override fun execute(code: String) {
        wrapExceptions(lineExecution = true) {
            python.exec(code)
        }
    }


//    override fun readFile(file: File, inputStream: InputStream) {
//        file.parentFile?.let { fileDir ->
//            python.exec(
//                """
//                import sys
//                sys.path.insert(0, '${fileDir.absolutePath}')
//                """.trimIndent())
//        }
//        super.readFile(file, inputStream)
//    }

    override fun readFile(fileName: String, stream: InputStream) {
        wrapExceptions {
            python.execfile(stream, fileName)
        }
    }

    override fun toScript(fromJava: Any): Any = Py.java2py(fromJava)
    override fun toJava(fromScript: Any): Any =
        (fromScript as? PyObject)?.__tojava__(Any::class.java)
            ?: fromScript

    override fun callableArgsCount(fromScript: Any): Int {
        val pyHandler = fromScript as PyFunction
        return pyHandler.__code__.__getattr__("co_argcount").asInt()
    }

    override fun callableToAliasProcessor(fromScript: Any): AliasProcesser {
        val handler = fromScript as? PyFunction ?: throw IllegalArgumentException()
        return { args ->
            wrapExceptions {
                handler.__call__(args.map { Py.java2py(it) }.toTypedArray())
                    .__tojava__(String::class.java)
                    as String?
                    ?: ""
            }
        }
    }

    override fun <R> callableToFunction0(fromScript: Any): () -> R = {
        wrapExceptions {
            @Suppress("UNCHECKED_CAST")
            (fromScript as PyFunction).__call__() as R
        }
    }

    override fun callableToFunction1(fromScript: Any): (Any?) -> Any? = { arg ->
        wrapExceptions {
            (fromScript as PyFunction).__call__(Py.java2py(arg))
        }
    }

    override fun callableToFunctionN(fromScript: Any): (Array<Any>) -> Any? = { rawArg ->
        wrapExceptions {
            val pythonArgs = Array<PyObject>(rawArg.size) { index ->
                Py.java2py(rawArg[index])
            }
            (fromScript as PyFunction).__call__(pythonArgs)
        }
    }

    override fun compilePatternSpec(fromScript: Any, flags: String): PatternSpec {
        val flagsSet = patternSpecFlagsToEnums(flags)

        return when (fromScript) {
            is String -> compileSimplePatternSpec(fromScript, flagsSet)
            is PatternObject -> {
                // first, try to compile it as a Java regex Pattern;
                // that will be much more efficient than delegating
                // to Python regex stuff (since we have to allocate
                // arrays for just about every call)
                val patternAsString = fromScript.pattern.string
                try {
                    val javaPattern = Pattern.compile(patternAsString)

                    // if we got here, huzzah! no compile issues
                    JavaRegexPatternSpec(
                        patternAsString,
                        javaPattern,
                        flagsSet
                    )
                } catch (e: PatternSyntaxException) {
                    // alas, fallback to using the python pattern
                    PyPatternSpec(fromScript, flagsSet)
                }
            }

            else -> throw IllegalArgumentException(
                "Invalid alias type: $fromScript (${fromScript.javaClass})")
        }
    }

    override fun wrapWindow(
        tabpage: IJudoTabpage,
        window: IJudoWindow
    ) = createPyWindow(tabpage, window)

    override fun onPreReadFile(file: File, inputStream: InputStream) {
        file.parentFile?.let { fileDir ->
            python.exec(
                """
                import sys
                sys.path.insert(0, '${fileDir.absolutePath}')
                """.trimIndent())
        }
    }

    override fun onPreReload() {
        // clean up modules
        val modules = python.systemState.modules as PyStringMap
        val toRemove = HashSet<PyObject>()
        modules.keys().asIterable()
            .filter { it.asString() !in keepModules }
            .forEach {
                toRemove.add(it)
            }

        for (keyToRemove in toRemove) {
            modules.__delitem__(keyToRemove)
        }

        super.onPreReload()
    }

    private inline fun <R> wrapExceptions(lineExecution: Boolean = false, block: () -> R): R {
        try {
            return block()
        } catch (e: PyException) {
            if (lineExecution) {
                // if the single line execution's cause was a ScriptExecution,
                // that's the only info we need
                val cause = e.cause
                if (cause is ScriptExecutionException) {
                    throw cause
                }
            }

            throw ScriptExecutionException(e.toString())
        }
    }

    private fun <R> JudoScriptingEntity.Function<R>.toPyFn(): PyObject {
        val usages = doc.invocations ?: throw IllegalArgumentException("No invocations for Fn?")
        val hasVarArgs = usages.any { it.hasVarArgs }
        val hasDecorator = usages.any { it.canBeDecorator }
        val flagsType = usages.flatMap { usage ->
            usage.args.map { it.flags }
        }.firstOrNull { it != null }
        val hasFlags = flagsType != null
        val maxArgs = when {
            hasVarArgs -> Int.MAX_VALUE
            else -> usages.maxBy { it.args.size }!!.args.size
        }
        val minArgs = usages.asSequence().map { usage ->
            usage.args.sumBy {
                if (it.isOptional) 0
                else 1
            }
        }.min() ?: throw IllegalStateException()
        val callable = this.toFunctionalInterface(this@JythonScriptingEngine)

        return if (hasDecorator) {
            asMaybeDecorator<Any>(
                name,
                acceptsFlag = hasFlags,
                isFlag = flagCheckerFor(flagsType),
                takeArgs = maxArgs,
                minArgs = minArgs - 1 // as a decorator, it can be called with min-1
            ) { args -> callable.call(*args) }
        } else {
            asPyFn<Any, Any>(
                name,
                takeArgs = maxArgs,
                minArgs = minArgs
            ) { args -> callable.call(*args) }
        }
    }
}

@Suppress("NOTHING_TO_INLINE", "RedundantLambdaArrow")
private inline fun flagCheckerFor(type: Class<out Enum<*>>?): (String) -> Boolean {
    return if (type == null) { _ -> false }
    else {
        val stringTypes = type.declaredFields.filter {
            it.isEnumConstant
        }.map {
            it.get(type).toString().toLowerCase()
        }.toSet()

        return { input -> stringTypes.contains(input.toLowerCase()) }
    }
}

private class PyGlobals : PyStringMap() {

    private val reservedSet = HashSet<String>()

    override fun __setitem__(key: String?, value: PyObject?) {
        if (key !in reservedSet) {
            super.__setitem__(key, value)
        }
    }

    operator fun set(key: String, value: PyObject) {
        reservedSet.add(key)
        super.__setitem__(key, value)
    }

    operator fun set(key: String, value: Any) {
        reservedSet.add(key)
        super.__setitem__(key, Py.java2py(value))
    }
}

/**
 * Create a Python function that can be used either as a normal
 * function OR a decorator
 */
private inline fun <reified T: Any> asMaybeDecorator(
    name: String,
    takeArgs: Int,
    minArgs: Int = takeArgs - 1,
    acceptsFlag: Boolean,
    crossinline isFlag: (String) -> Boolean,
    crossinline fn: (Array<T>) -> Unit
) = asPyFn<T, PyObject?>(name, takeArgs, minArgs) { args ->
    if (isDecoratorCall(args, minArgs, takeArgs, acceptsFlag, isFlag)) {
        // decorator mode; we return a function that accepts
        // a function and finally calls `fn`
        asPyFn<PyObject, PyObject>(name, 1) { wrappedArgs ->
            val combined = args + (wrappedArgs[0] as T)
            fn(combined)
            wrappedArgs[0]
        }
    } else {
        // regular function call
        fn(args)
        null
    }
}

private inline fun <T : Any> isDecoratorCall(
    args: Array<T>, minArgs: Int, takeArgs: Int,
    acceptsFlag: Boolean, isFlag: (String) -> Boolean
): Boolean {
    if (args.size !in minArgs..(takeArgs - 1)) return false

    val lastArg = args.last()
    if (lastArg is PyFunction) return false

    if (acceptsFlag) {
        if (lastArg is String && isFlag(lastArg)) {
            return true
        }

        if (args.size == takeArgs - 1
            && lastArg is String
            && !isFlag(lastArg)) {
            // one less than takeArgs, but last arg is not a flag.
            // This must be a regular function call
            return false
        }
    }

    // otherwise, it's a decorator!
    return true
}


private inline fun <reified T: Any, reified R> asPyFn(
    name: String,
    takeArgs: Int = 0,
    minArgs: Int = takeArgs,
    crossinline fn: (Array<T>) -> R
): PyObject = object : PyObject() {
    override fun __call__(args: Array<PyObject>, keywords: Array<String>): PyObject {
        if (minArgs != Int.MAX_VALUE && args.size < minArgs) {
            throw IllegalArgumentException("$name Expected $minArgs arguments; got ${args.size}")
        }

        if (args.size > takeArgs) {
            throw IllegalArgumentException("$name Expected no more than $takeArgs arguments; got ${args.size}")
        }

        val typedArgs =
            if (takeArgs == 0) emptyArray()
            else {
                args.take(takeArgs)
                    .map<PyObject, T> { T::class.java.cast(it.__tojava__(T::class.java)) }
                    .toTypedArray()
            }

        val result = fn(typedArgs)
        if (T::class == Unit::class) {
            return Py.None
        }

        return Py.java2py(result)
    }
}

internal fun createPyWindow(tabpage: IJudoTabpage, window: IJudoWindow): PyObject {
    val resize = asPyFn<Int, Unit>("resize", 2) {
        window.resize(it[0], it[1])
    }

    return object : PyObject() {
        override fun __findattr_ex__(name: String?): PyObject? =
            when (name ?: "") {
                "buffer" -> createPyBuffer(window, window.currentBuffer) // cache?
                "height" -> Py.java2py(window.height)
                "width" -> Py.java2py(window.width)
                "id" -> Py.java2py(window.id)

                // not used oft enough to cache
                "close" -> asPyFn<Any, Unit>("close") {
                    tabpage.close(window)
                }
                "resize" -> resize

                else -> super.__findattr_ex__(name)
            }
    }
}

internal fun createPyBuffer(
    window: IJudoWindow,
    buffer: IJudoBuffer
): PyObject {
    val append = asPyFn<String, Unit>("append", 1) {
        window.appendLine(it[0])
    }
    val clear = asPyFn<Any, Unit>("clear") {
        buffer.clear()
    }
    val set = asPyFn<List<String>, Unit>("set", 1) {
        buffer.set(it[0].toFlavorableList())
    }

    return object : PyObject() {
        override fun __len__(): Int {
            return buffer.size
        }

        override fun __findattr_ex__(name: String?): PyObject =
            when (name ?: "") {
                "append" -> append
                "clear" -> clear
                "set" -> set
                "id" -> Py.java2py(buffer.id)

                else -> super.__findattr_ex__(name)
            }
    }
}

/**
 * If we can't use the Python pattern as a Java pattern,
 *  we have to fall back to this
 */
internal class PyPatternSpec(
    private val pattern: PatternObject,
    override val flags: EnumSet<PatternProcessingFlags>
) : PatternSpec {

    override fun matcher(input: CharSequence): PatternMatcher =
        PyPatternMatcher(
            pattern.groups,
            pattern.finditer(arrayOf(Py.java2py(input.toString())), emptyArray())
                as PyCallIter
        )

    override val original: String = pattern.pattern.string
}

internal class PyPatternMatcher(
    override val groups: Int,
    finditer: PyIterator
) : PatternMatcher {

    private var iterator = finditer.iterator()
    private var current: MatchObject? = null


    override fun find(): Boolean =
        if (iterator.hasNext()) {
            current = iterator.next() as MatchObject
            true
        } else {
            current = null
            false
        }

    override fun group(index: Int): String =
    // NOTE: group 0 means everything that matched, but
    // index 0 means that actual matching group; so, index + 1
        current!!.group(arrayOf(Py.java2py(index + 1))).asString()

    override val start: Int
        get() = current!!.start().asInt()

    override fun start(index: Int): Int =
        current!!.start(Py.java2py(index + 1)).asInt()

    override val end: Int
        get() = current!!.end().asInt()

    override fun end(index: Int): Int =
        current!!.end(Py.java2py(index + 1)).asInt()
}

class InterfaceAdapter : PyObjectAdapter {
    companion object {
        private var isInitialized = false

        internal val exposedInterfaces = arrayOf(
            IJudoCore::class.java,

            IAliasManager::class.java,
            IEventManager::class.java,
            ILogManager::class.java,
            IMapManagerPublic::class.java,
            IPromptManager::class.java,
            ITriggerManager::class.java,
            StateMap::class.java,
            JudoRendererInfo::class.java,
            IJudoTabpage::class.java,

            IJudoMap::class.java
        )

        fun init() {
            if (!isInitialized) {
                Py.getAdapter().addPostClass(InterfaceAdapter())
            }
        }
    }

    override fun adapt(obj: Any?): PyObject {
        val type = findInterfaceFor(obj)!!
        val pyObj = PyObjectDerived(PyType.fromClass(type, false))
        JyAttribute.setAttr(pyObj, JyAttribute.JAVA_PROXY_ATTR, obj)
        return pyObj
    }

    override fun canAdapt(obj: Any?): Boolean {
        if (obj == null) return false
        return findInterfaceFor(obj) != null
    }

    private fun findInterfaceFor(obj: Any?): Class<*>? {
        if (obj == null) return null
        return InterfaceAdapter.exposedInterfaces.firstOrNull {
            it.isInstance(obj)
        }
    }
}
