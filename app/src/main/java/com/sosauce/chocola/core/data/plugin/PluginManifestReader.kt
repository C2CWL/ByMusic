package com.sosauce.chocola.core.data.plugin

/**
 * Reads a MusicFree-format plugin's exported fields from source text without evaluating it.
 *
 * Preview/import must not run plugin code before the user accepts the risk sheet. The
 * CommonJS body is never executed here: only the top-level `module.exports = { ... }`
 * object literal (or the same object assigned via `Object.assign(module.exports, { ... })`)
 * is scanned for string/array fields the UI needs. Methods and `require()` usage are
 * inferred from the source text so the import sheet can still list declared features;
 * the real method list is confirmed later when the plugin is loaded after install.
 */
internal object PluginManifestReader {

    private val OBJECT_ASSIGN = Regex(
        """Object\.assign\s*\(\s*module\.exports\s*,\s*\{""",
        RegexOption.IGNORE_CASE
    )
    private val MODULE_EXPORTS = Regex(
        """(?:module\.exports|exports)\s*=\s*\{""",
        RegexOption.IGNORE_CASE
    )
    private val REQUIRE = Regex("""require\s*\(\s*['"]([^'"]+)['"]\s*\)""")

    fun read(code: String): PluginManifest {
        val body = code.removePrefix("\uFEFF")
        if (body.isBlank()) throw PluginException(PluginErrorType.LOAD, "文件是空的")
        val obj = findExportsObject(body)
            ?: throw PluginException(PluginErrorType.LOAD, "插件没有导出对象（module.exports）")
        val platform = stringField(obj, "platform")?.trim().orEmpty()
        if (platform.isBlank()) {
            throw PluginException(PluginErrorType.LOAD, "插件没有填写名称（platform）")
        }
        if (platform == "本地") {
            throw PluginException(PluginErrorType.LOAD, "插件名称不能叫“本地”")
        }
        val required = REQUIRE.findAll(body).map { it.groupValues[1] }.toSet()
        val missing = required.filter { it !in MusicFreeProtocol.SUPPORTED_MODULES }.sorted()
        val methods = MusicFreeProtocol.METHODS.filter { methodDeclared(body, it) }
        return PluginManifest(
            platform = platform,
            version = stringField(obj, "version"),
            author = stringField(obj, "author"),
            srcUrl = stringField(obj, "srcUrl"),
            description = stringField(obj, "description"),
            appVersion = stringField(obj, "appVersion"),
            cacheControl = stringField(obj, "cacheControl"),
            primaryKey = stringArrayField(obj, "primaryKey"),
            supportedSearchType = stringArrayField(obj, "supportedSearchType"),
            userVariables = userVariables(obj),
            methods = methods,
            requiredModules = required.sorted(),
            missingModules = missing
        )
    }

    private fun findExportsObject(code: String): String? {
        // Both patterns end on the opening `{`; extractObjectLiteral needs that index.
        val start = OBJECT_ASSIGN.find(code)?.range?.last
            ?: MODULE_EXPORTS.find(code)?.range?.last
            ?: return null
        return extractObjectLiteral(code, start)
    }

    /** [openBraceIndex] points at the `{` that opens the object literal. */
    private fun extractObjectLiteral(code: String, openBraceIndex: Int): String? {
        if (openBraceIndex !in code.indices || code[openBraceIndex] != '{') return null
        var depth = 0
        var i = openBraceIndex
        var inSingle = false
        var inDouble = false
        var inTemplate = false
        var inLineComment = false
        var inBlockComment = false
        var escape = false
        while (i < code.length) {
            val c = code[i]
            val next = code.getOrNull(i + 1)
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && next == '/') {
                    inBlockComment = false
                    i++
                }
                inSingle -> when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '\'' -> inSingle = false
                }
                inDouble -> when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inDouble = false
                }
                inTemplate -> when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '`' -> inTemplate = false
                }
                c == '/' && next == '/' -> {
                    inLineComment = true
                    i++
                }
                c == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                c == '\'' -> inSingle = true
                c == '"' -> inDouble = true
                c == '`' -> inTemplate = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return code.substring(openBraceIndex, i + 1)
                }
            }
            i++
        }
        return null
    }

    private fun stringField(obj: String, name: String): String? {
        val match = Regex(
            """(?:^|[,{\s])$name\s*:\s*(['"`])((?:\\.|(?!\1).)*)\1""",
            setOf(RegexOption.MULTILINE)
        ).find(obj) ?: return null
        return unquote(match.groupValues[2], match.groupValues[1].single())
    }

    private fun stringArrayField(obj: String, name: String): List<String>? {
        val match = Regex(
            """(?:^|[,{\s])$name\s*:\s*\[([^\]]*)]""",
            setOf(RegexOption.MULTILINE)
        ).find(obj) ?: return null
        return Regex("""(['"])((?:\\.|(?!\1).)*)\1""")
            .findAll(match.groupValues[1])
            .map { unquote(it.groupValues[2], it.groupValues[1].single()) }
            .toList()
    }

    private fun userVariables(obj: String): List<PluginUserVariable> {
        val match = Regex(
            """(?:^|[,{\s])userVariables\s*:\s*\[""",
            setOf(RegexOption.MULTILINE)
        ).find(obj) ?: return emptyList()
        val array = extractArrayLiteral(obj, match.range.last) ?: return emptyList()
        val out = ArrayList<PluginUserVariable>()
        var i = 1
        while (i < array.length) {
            val start = array.indexOf('{', i)
            if (start < 0) break
            val item = extractObjectLiteral(array, start) ?: break
            val key = stringField(item, "key")?.takeIf { it.isNotBlank() }
            if (key != null) {
                out += PluginUserVariable(
                    key = key,
                    name = stringField(item, "name") ?: stringField(item, "title"),
                    hint = stringField(item, "hint")
                )
            }
            i = start + item.length
        }
        return out
    }

    private fun extractArrayLiteral(code: String, openBracketIndex: Int): String? {
        if (openBracketIndex !in code.indices || code[openBracketIndex] != '[') return null
        var depth = 0
        var i = openBracketIndex
        var inSingle = false
        var inDouble = false
        var inTemplate = false
        var escape = false
        while (i < code.length) {
            val c = code[i]
            when {
                inSingle -> when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '\'' -> inSingle = false
                }
                inDouble -> when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inDouble = false
                }
                inTemplate -> when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '`' -> inTemplate = false
                }
                c == '\'' -> inSingle = true
                c == '"' -> inDouble = true
                c == '`' -> inTemplate = true
                c == '[' -> depth++
                c == ']' -> {
                    depth--
                    if (depth == 0) return code.substring(openBracketIndex, i + 1)
                }
            }
            i++
        }
        return null
    }

    private fun methodDeclared(code: String, name: String): Boolean {
        val patterns = listOf(
            Regex("""(?:^|[,{\s])$name\s*\("""),
            Regex("""(?:^|[,{\s])(?:async\s+)?$name\s*\("""),
            Regex("""(?:^|[,{\s])$name\s*:\s*(?:async\s*)?(?:function\b|\()""")
        )
        return patterns.any { it.containsMatchIn(code) }
    }

    private fun unquote(raw: String, quote: Char): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                out.append(c)
                i++
                continue
            }
            when (val n = raw[i + 1]) {
                '\\', '\'', '"', '`' -> {
                    out.append(n)
                    i += 2
                }
                'n' -> {
                    out.append('\n')
                    i += 2
                }
                'r' -> {
                    out.append('\r')
                    i += 2
                }
                't' -> {
                    out.append('\t')
                    i += 2
                }
                'u' if i + 5 < raw.length -> {
                    val hex = raw.substring(i + 2, i + 6)
                    out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                    i += 6
                }
                else -> {
                    out.append(n)
                    i += 2
                }
            }
        }
        return out.toString()
    }
}
