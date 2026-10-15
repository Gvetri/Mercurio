package org.hermesnative.client.feature.entry.presentation

internal fun codeGrammar(language: String): CodeGrammar? = LanguageGrammars[language.trim().lowercase()]

internal data class CodeGrammar(
    val keywords: Set<String>,
    val lineComment: String?,
    val blockComment: Pair<String, String>?,
    val quotes: String,
    val tripleQuotes: String = "",
    val caseInsensitiveKeywords: Boolean = false,
)

private val CFamilyKeywords =
    setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
        "enum", "extern", "float", "for", "goto", "if", "int", "long", "register", "return",
        "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned",
        "void", "volatile", "while",
    )

private val CGrammar =
    CodeGrammar(
        keywords = CFamilyKeywords,
        lineComment = "//",
        blockComment = "/*" to "*/",
        quotes = "\"'",
    )

private val KotlinGrammar =
    CodeGrammar(
        keywords =
            setOf(
                "as", "break", "by", "catch", "class", "const", "constructor", "continue",
                "crossinline", "data", "do", "dynamic", "else", "enum", "false", "final", "for",
                "fun", "get", "if", "import", "in", "infix", "init", "inline", "inner",
                "interface", "internal", "is", "lateinit", "noinline", "null", "object", "open",
                "operator", "out", "override", "package", "private", "public", "reified",
                "return", "sealed", "set", "super", "suspend", "this", "throw", "true", "try",
                "typealias", "val", "var", "vararg", "when", "where", "while",
            ),
        lineComment = "//",
        blockComment = "/*" to "*/",
        quotes = "\"'",
        tripleQuotes = "\"",
    )

private val JavaGrammar =
    CodeGrammar(
        keywords =
            CFamilyKeywords +
                setOf(
                    "abstract", "assert", "boolean", "byte", "catch", "class", "extends", "false",
                    "final", "finally", "implements", "import", "instanceof", "interface", "new",
                    "null", "package", "private", "protected", "public", "static", "strictfp",
                    "synchronized", "this", "throw", "throws", "transient", "true", "void",
                ),
        lineComment = "//",
        blockComment = "/*" to "*/",
        quotes = "\"'",
    )

private val JavaScriptKeywords =
    setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "export", "extends", "false", "finally", "for",
        "function", "get", "if", "import", "in", "instanceof", "let", "new", "null", "of",
        "return", "set", "static", "super", "switch", "this", "throw", "true", "try", "typeof",
        "var", "void", "while", "with", "yield",
    )

private val JavaScriptGrammar =
    CodeGrammar(
        keywords = JavaScriptKeywords,
        lineComment = "//",
        blockComment = "/*" to "*/",
        quotes = "\"'`",
        tripleQuotes = "`",
    )

private val TypeScriptGrammar =
    JavaScriptGrammar.copy(
        keywords =
            JavaScriptKeywords +
                setOf(
                    "abstract", "as", "declare", "enum", "implements", "interface", "keyof",
                    "namespace", "private", "protected", "public", "readonly", "satisfies", "type",
                ),
    )

private val PythonGrammar =
    CodeGrammar(
        keywords =
            setOf(
                "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
                "del", "elif", "else", "except", "False", "finally", "for", "from", "global",
                "if", "import", "in", "is", "lambda", "match", "case", "None", "nonlocal", "not",
                "or", "pass", "raise", "return", "self", "True", "try", "while", "with", "yield",
                "cls", "super",
            ),
        lineComment = "#",
        blockComment = null,
        quotes = "\"'",
        tripleQuotes = "\"'",
    )

private val RubyGrammar =
    CodeGrammar(
        keywords =
            setOf(
                "alias", "and", "begin", "break", "case", "class", "def", "defined", "do", "else",
                "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next", "nil",
                "not", "or", "redo", "require", "rescue", "retry", "return", "self", "super",
                "then", "true", "undef", "unless", "until", "when", "while", "yield",
            ),
        lineComment = "#",
        blockComment = null,
        quotes = "\"'",
    )

private val GoGrammar =
    CodeGrammar(
        keywords =
            setOf(
                "break", "case", "chan", "const", "continue", "default", "defer", "else",
                "fallthrough", "false", "for", "func", "go", "goto", "if", "import", "interface",
                "map", "nil", "package", "range", "return", "select", "struct", "switch", "true",
                "type", "var",
            ),
        lineComment = "//",
        blockComment = "/*" to "*/",
        quotes = "\"'`",
        tripleQuotes = "`",
    )

private val RustGrammar =
    CodeGrammar(
        keywords =
            setOf(
                "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
                "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
                "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
                "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while",
            ),
        lineComment = "//",
        blockComment = "/*" to "*/",
        quotes = "\"'",
    )

private val CppGrammar =
    CGrammar.copy(
        keywords =
            CFamilyKeywords +
                setOf(
                    "asm", "catch", "class", "delete", "false", "friend", "inline", "namespace",
                    "new", "operator", "private", "protected", "public", "template", "this",
                    "throw", "true", "try", "using", "virtual",
                ),
    )

private val CSharpGrammar =
    CodeGrammar(
        keywords =
            setOf(
                "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char",
                "checked", "class", "const", "continue", "default", "delegate", "do", "double",
                "else", "enum", "event", "explicit", "false", "finally", "fixed", "float", "for",
                "foreach", "get", "goto", "if", "implicit", "in", "int", "interface", "internal",
                "is", "lock", "long", "namespace", "new", "null", "object", "operator", "out",
                "override", "params", "private", "protected", "public", "readonly", "ref",
                "return", "sealed", "set", "short", "sizeof", "stackalloc", "static", "string",
                "struct", "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong",
                "unchecked", "unsafe", "ushort", "using", "virtual", "void", "volatile", "where",
                "while", "yield",
            ),
        lineComment = "//",
        blockComment = "/*" to "*/",
        quotes = "\"'",
    )

private val ShellGrammar =
    CodeGrammar(
        keywords =
            setOf(
                "break", "case", "continue", "declare", "do", "done", "elif", "else", "esac",
                "eval", "exit", "export", "fi", "for", "function", "if", "in", "local", "readonly",
                "return", "select", "set", "shift", "source", "then", "time", "trap", "typeset",
                "until", "wait", "while",
            ),
        lineComment = "#",
        blockComment = null,
        quotes = "'\"",
    )

private val SqlGrammar =
    CodeGrammar(
        keywords =
            setOf(
                "add", "all", "alter", "and", "as", "begin", "by", "commit", "create", "delete",
                "distinct", "drop", "end", "foreign", "from", "full", "grant",
                "group", "having", "in", "index", "inner", "insert", "into", "join", "key",
                "left", "like", "limit", "not", "null", "offset", "on", "or",
                "order", "outer", "primary", "references", "revoke", "right", "rollback", "select",
                "set", "table", "union", "update", "values", "view", "where",
            ),
        lineComment = "--",
        blockComment = "/*" to "*/",
        quotes = "'\"",
        caseInsensitiveKeywords = true,
    )

private val DataGrammar =
    CodeGrammar(
        keywords = setOf("false", "null", "true"),
        lineComment = null,
        blockComment = null,
        quotes = "\"'",
    )

private val YamlGrammar =
    DataGrammar.copy(
        keywords = setOf("false", "null", "true", "yes", "no", "on", "off"),
        lineComment = "#",
    )

private val TomlGrammar = DataGrammar.copy(lineComment = "#")

private val LanguageGrammars =
    mapOf(
        "c" to CGrammar,
        "cpp" to CppGrammar,
        "c++" to CppGrammar,
        "cxx" to CppGrammar,
        "csharp" to CSharpGrammar,
        "cs" to CSharpGrammar,
        "go" to GoGrammar,
        "golang" to GoGrammar,
        "java" to JavaGrammar,
        "javascript" to JavaScriptGrammar,
        "js" to JavaScriptGrammar,
        "jsx" to JavaScriptGrammar,
        "json" to DataGrammar,
        "kt" to KotlinGrammar,
        "kotlin" to KotlinGrammar,
        "mjs" to JavaScriptGrammar,
        "py" to PythonGrammar,
        "python" to PythonGrammar,
        "rb" to RubyGrammar,
        "ruby" to RubyGrammar,
        "rs" to RustGrammar,
        "rust" to RustGrammar,
        "sh" to ShellGrammar,
        "bash" to ShellGrammar,
        "shell" to ShellGrammar,
        "sql" to SqlGrammar,
        "toml" to TomlGrammar,
        "ts" to TypeScriptGrammar,
        "tsx" to TypeScriptGrammar,
        "typescript" to TypeScriptGrammar,
        "yaml" to YamlGrammar,
        "yml" to YamlGrammar,
        "zsh" to ShellGrammar,
    )
