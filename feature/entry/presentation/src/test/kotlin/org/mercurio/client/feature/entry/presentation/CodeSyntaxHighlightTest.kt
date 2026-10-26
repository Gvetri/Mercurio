package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSyntaxHighlightTest {
    @Test
    fun kotlin_code_tokenizes_keywords_numbers_strings_comments_and_call_sites() {
        val code = "fun main() {\n  // plan\n  val total = 42 + 0xFF * \"x\"\n}"

        assertEquals(
            listOf(
                CodeTokenKind.Keyword to "fun",
                CodeTokenKind.Function to "main",
                CodeTokenKind.Comment to "// plan",
                CodeTokenKind.Keyword to "val",
                CodeTokenKind.Number to "42",
                CodeTokenKind.Number to "0xFF",
                CodeTokenKind.String to "\"x\"",
            ),
            tokenTexts("kotlin", code),
        )
    }

    @Test
    fun python_code_tokenizes_its_keywords_call_sites_and_triple_quoted_docstrings() {
        val code = "def greet(name):\n    \"\"\"say hello\"\"\"\n    # note\n    return greet(\"hi\") * 2"

        assertEquals(
            listOf(
                CodeTokenKind.Keyword to "def",
                CodeTokenKind.Function to "greet",
                CodeTokenKind.String to "\"\"\"say hello\"\"\"",
                CodeTokenKind.Comment to "# note",
                CodeTokenKind.Keyword to "return",
                CodeTokenKind.Function to "greet",
                CodeTokenKind.String to "\"hi\"",
                CodeTokenKind.Number to "2",
            ),
            tokenTexts("python", code),
        )
    }

    @Test
    fun shell_code_treats_the_hash_as_a_line_comment_and_quotes_as_strings() {
        val code = "#!/bin/sh\nfor f in \"$@\"; do\n  echo \"hi\" # run\ndone"

        assertEquals(
            listOf(
                CodeTokenKind.Comment to "#!/bin/sh",
                CodeTokenKind.Keyword to "for",
                CodeTokenKind.Keyword to "in",
                CodeTokenKind.String to "\"$@\"",
                CodeTokenKind.Keyword to "do",
                CodeTokenKind.String to "\"hi\"",
                CodeTokenKind.Comment to "# run",
                CodeTokenKind.Keyword to "done",
            ),
            tokenTexts("shell", code),
        )
    }

    @Test
    fun sql_keywords_are_case_insensitive_and_block_comments_close_at_the_marker() {
        val code = "select count(*) /* why */ from t where n = 1;"

        assertEquals(
            listOf(
                CodeTokenKind.Keyword to "select",
                CodeTokenKind.Function to "count",
                CodeTokenKind.Comment to "/* why */",
                CodeTokenKind.Keyword to "from",
                CodeTokenKind.Keyword to "where",
                CodeTokenKind.Number to "1",
            ),
            tokenTexts("sql", code),
        )
    }

    @Test
    fun json_tokenizes_strings_keywords_and_exponent_numbers() {
        val code = "{\"key\": true, \"n\": 1.5e3}"

        assertEquals(
            listOf(
                CodeTokenKind.String to "\"key\"",
                CodeTokenKind.Keyword to "true",
                CodeTokenKind.String to "\"n\"",
                CodeTokenKind.Number to "1.5e3",
            ),
            tokenTexts("json", code),
        )
    }

    @Test
    fun yaml_code_tokenizes_hash_line_comments_outside_strings() {
        val code = "# release\nname: \"a # b\" # note\non: true"

        assertEquals(
            listOf(
                CodeTokenKind.Comment to "# release",
                CodeTokenKind.String to "\"a # b\"",
                CodeTokenKind.Comment to "# note",
                CodeTokenKind.Keyword to "on",
                CodeTokenKind.Keyword to "true",
            ),
            tokenTexts("yaml", code),
        )
    }

    @Test
    fun toml_code_tokenizes_hash_line_comments() {
        val code = "# config\ntitle = \"app\" # label\nport = 8080"

        assertEquals(
            listOf(
                CodeTokenKind.Comment to "# config",
                CodeTokenKind.String to "\"app\"",
                CodeTokenKind.Comment to "# label",
                CodeTokenKind.Number to "8080",
            ),
            tokenTexts("toml", code),
        )
    }

    @Test
    fun json_produces_no_comment_tokens() {
        val code = "{\n  \"note\": \"# not a comment\"\n}\n# stray"

        assertEquals(
            listOf(
                CodeTokenKind.String to "\"note\"",
                CodeTokenKind.String to "\"# not a comment\"",
            ),
            tokenTexts("json", code),
        )
    }

    @Test
    fun javascript_backtick_templates_span_lines_and_line_comments_end_at_the_newline() {
        val code = "const t = `a\nb`; // tail"

        assertEquals(
            listOf(
                CodeTokenKind.Keyword to "const",
                CodeTokenKind.String to "`a\nb`",
                CodeTokenKind.Comment to "// tail",
            ),
            tokenTexts("javascript", code),
        )
    }

    @Test
    fun an_unterminated_string_or_comment_tokenizes_to_the_end_of_its_region() {
        val code = "val s = \"abc"

        assertEquals(
            listOf(CodeTokenKind.Keyword to "val", CodeTokenKind.String to "\"abc"),
            tokenTexts("kotlin", code),
        )
        assertEquals(
            listOf(CodeTokenKind.Comment to "/* open"),
            tokenTexts("kotlin", "/* open"),
        )
    }

    @Test
    fun an_unknown_language_and_plain_text_produce_no_tokens() {
        assertTrue(highlightCode("brainfuck", "val x = 1").isEmpty())
        assertTrue(highlightCode("text", "val x = 1").isEmpty())
    }

    @Test
    fun the_language_label_is_normalized_before_the_grammar_lookup() {
        assertEquals(CodeTokenKind.Keyword, highlightCode("Kotlin", "val x = 1").first().kind)
        assertEquals(CodeTokenKind.Keyword, highlightCode(" py", "def f(): pass").first().kind)
    }

    @Test
    fun tokens_are_disjoint_and_reappear_in_code_order() {
        val samples =
            listOf(
                "kotlin" to "fun x() { // c\n val s = \"a\" + 1.5_0f }",
                "python" to "def x(y):\n    return x('a')",
                "go" to "func main() {\n  s := \"x\" // c\n}",
                "rust" to "fn main() {\n  let n = 3u;\n}",
            )
        samples.forEach { (language, code) ->
            val tokens = highlightCode(language, code)
            var previousEnd = 0
            tokens.forEach { token ->
                assertTrue("tokens are ordered", token.start >= previousEnd && token.end > token.start)
                assertTrue("tokens stay inside the code", token.end <= code.length)
                previousEnd = token.end
            }
        }
    }

    @Test
    fun words_before_parentheses_are_call_sites_and_words_after_assignment_are_not() {
        val code = "val build = plan\nbuild(other)"

        assertEquals(
            listOf(
                CodeTokenKind.Keyword to "val",
                CodeTokenKind.Function to "build",
            ),
            tokenTexts("kotlin", code),
        )
    }

    private fun tokenTexts(
        language: String,
        code: String,
    ): List<Pair<CodeTokenKind, String>> =
        highlightCode(language, code).map { token -> token.kind to code.substring(token.start, token.end) }
}
