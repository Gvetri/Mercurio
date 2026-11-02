"""Deterministic tests for the comments-policy verifier."""
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
CHECK = ROOT / ".github/scripts/verify-comments.py"
REMEDY = "KDoc is the only allowed comment form"


class CommentsPolicyTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = pathlib.Path(temporary.name)

    def check_tree(self, files):
        for relative, content in files.items():
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
        subprocess.run(
            ["git", "init", "-q", "-b", "main", str(self.root)],
            check=True, capture_output=True, timeout=60,
        )
        subprocess.run(
            ["git", "-C", str(self.root), "add", "-A"],
            check=True, capture_output=True, timeout=60,
        )
        return self.check_root(self.root)

    def check_root(self, root):
        return subprocess.run(
            [sys.executable, str(CHECK), "--repository-root", str(root)],
            capture_output=True, text=True, timeout=120,
        )

    def assert_rejected(self, result, *expected):
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        for fragment in expected:
            self.assertIn(fragment, result.stderr)

    def assert_accepted(self, result):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_kdoc_passes(self):
        result = self.check_tree({
            "lib/Sample.kt": "package sample\n\n/**\n * Documentation of the sample.\n */\ninternal fun sample(): Int = 1\n",
        })
        self.assert_accepted(result)
        self.assertIn("KDoc only", result.stdout)

    def test_a_line_comment_fails_with_file_line_and_remedy(self):
        result = self.check_tree({
            "lib/Sample.kt": "package sample\n\n// The reason the sample returns one.\ninternal fun sample(): Int = 1\n",
        })
        self.assert_rejected(result, "lib/Sample.kt:3", "line comment", REMEDY)

    def test_a_block_comment_fails(self):
        result = self.check_tree({
            "lib/Sample.kt": "package sample\n\n/*\n * The reason.\n */\ninternal fun sample(): Int = 1\n",
        })
        self.assert_rejected(result, "lib/Sample.kt:3", "block comment", REMEDY)

    def test_a_nested_block_comment_fails(self):
        result = self.check_tree({
            "lib/Sample.kt": "package sample\n\n/* outer /* inner */ still a comment */\ninternal fun sample(): Int = 1\n",
        })
        self.assert_rejected(result, "lib/Sample.kt:3", "block comment")

    def test_comment_markers_strings_and_urls_pass(self):
        result = self.check_tree({
            "lib/Sample.kt": (
                "package sample\n\n"
                "internal val url = \"https://example.test/path#fragment\"\n\n"
                "internal val marker = \"// not a comment\"\n\n"
                "internal val raw = \"\"\"/* not a comment either */\"\"\"\n\n"
                "internal val quote = '\"'\n"
            ),
        })
        self.assert_accepted(result)

    def test_a_license_header_passes(self):
        result = self.check_tree({
            "lib/Sample.kt": (
                "/*\n"
                " * Copyright 2026 Mercurio\n"
                " * SPDX-License-Identifier: Apache-2.0\n"
                " */\n\n"
                "package sample\n"
            ),
        })
        self.assert_accepted(result)

    def test_a_line_comment_carrying_a_directive_word_mid_sentence_fails(self):
        result = self.check_tree({
            "lib/Sample.kt": (
                "package sample\n\n"
                "internal val sample = 1\n\n"
                "// The type: of this value is not known to the reader.\n"
            ),
        })
        self.assert_rejected(result, "lib/Sample.kt:5", "line comment")

    def test_a_license_marker_after_code_fails(self):
        result = self.check_tree({
            "lib/Sample.kt": (
                "package sample\n\n"
                "internal val sample = 1\n\n"
                "// Copyright applies to the table below.\n"
            ),
        })
        self.assert_rejected(result, "lib/Sample.kt:5", "line comment")

    def test_a_python_comment_fails(self):
        result = self.check_tree({
            "tools/sample.py": "import os\n\n# The reason for the import.\nprint(os.sep)\n",
        })
        self.assert_rejected(result, "tools/sample.py:3", "line comment", REMEDY)

    def test_python_docstrings_directives_and_strings_pass(self):
        result = self.check_tree({
            "tools/sample.py": (
                "#!/usr/bin/env python3\n"
                "\"\"\"Module documentation.\"\"\"\n"
                "import os  # noqa: E402\n\n"
                "VALUE = \"# not a comment\"\n"
            ),
        })
        self.assert_accepted(result)

    def test_a_shell_comment_fails(self):
        result = self.check_tree({
            "tools/sample.sh": "#!/usr/bin/env bash\nset -euo pipefail\n# The reason for the flag.\nprintf '%s\\n' ok\n",
        })
        self.assert_rejected(result, "tools/sample.sh:3", "line comment")

    def test_shell_quoted_markers_shebang_and_directive_pass(self):
        result = self.check_tree({
            "tools/sample.sh": (
                "#!/usr/bin/env bash\n"
                "# shellcheck disable=SC2016\n"
                "printf '%s\\n' '# not a comment'\n"
                "printf '%s\\n' \"${#path}\"\n"
            ),
        })
        self.assert_accepted(result)

    def test_a_heredoc_body_is_not_a_comment(self):
        result = self.check_tree({
            "tools/sample.sh": (
                "#!/bin/sh\n"
                "set -eu\n"
                "cat <<'EOF'\n"
                "# not a comment\n"
                "EOF\n"
                "python3 - <<'PY'\n"
                "# also not a comment\n"
                "PY\n"
            ),
        })
        self.assert_accepted(result)

    def test_a_comment_after_a_heredoc_still_fails(self):
        result = self.check_tree({
            "tools/sample.sh": (
                "#!/bin/sh\n"
                "cat <<'EOF'\n"
                "EOF\n"
                "# The reason for the call.\n"
            ),
        })
        self.assert_rejected(result, "tools/sample.sh:4", "line comment")

    def test_yaml_block_scalar_content_is_not_a_comment(self):
        result = self.check_tree({
            "flows/sample.yaml": (
                "appId: example.test\n"
                "---\n"
                "- launchApp\n"
                "- inputText: |\n"
                "    # not a comment\n"
                "- assertNotVisible: \".*# Alpha.*\"\n"
            ),
        })
        self.assert_accepted(result)

    def test_a_shell_comment_inside_a_run_block_fails(self):
        result = self.check_tree({
            ".github/workflows/sample.yml": (
                "name: sample\n"
                "on: push\n"
                "jobs:\n"
                "  build:\n"
                "    runs-on: ubuntu-latest\n"
                "    steps:\n"
                "      - run: |\n"
                "          printf '%s\\n' ok\n"
                "          # The reason for the step.\n"
            ),
        })
        self.assert_rejected(result, ".github/workflows/sample.yml:9", "line comment")

    def test_a_yaml_trailing_comment_fails_but_a_url_passes(self):
        rejected = self.check_tree({
            ".github/workflows/sample.yml": "name: sample\nurl: https://example.test/a#b\nkey: value # why\n",
        })
        self.assert_rejected(rejected, ".github/workflows/sample.yml:3", "line comment")

    def test_a_toml_comment_fails_but_a_string_passes(self):
        accepted = self.check_tree({
            "gradle/sample.toml": "[versions]\nurl = \"https://example.test#anchor\"\n",
        })
        self.assert_accepted(accepted)
        self.root.joinpath("gradle/sample.toml").write_text(
            "[versions]\n# The reason.\nkotlin = \"2.0.0\"\n", encoding="utf-8",
        )
        subprocess.run(["git", "-C", str(self.root), "add", "-A"], check=True, capture_output=True, timeout=60)
        self.assert_rejected(self.check_root(self.root), "gradle/sample.toml:2", "line comment")

    def test_a_properties_comment_fails(self):
        result = self.check_tree({
            "gradle/sample.properties": "# The reason.\nkey=value\n",
        })
        self.assert_rejected(result, "gradle/sample.properties:1", "line comment")

    def test_an_xml_comment_fails_but_an_attribute_marker_passes(self):
        accepted = self.check_tree({
            "app/src/main/res/values/styles.xml": (
                "<resources>\n"
                "    <style name=\"Sample\">\n"
                "        <item name=\"android:colorAccent\">#4E5D8C</item>\n"
                "    </style>\n"
                "</resources>\n"
            ),
        })
        self.assert_accepted(accepted)
        self.root.joinpath("app/src/main/res/values/styles.xml").write_text(
            "<resources>\n    <!-- The reason. -->\n</resources>\n", encoding="utf-8",
        )
        subprocess.run(["git", "-C", str(self.root), "add", "-A"], check=True, capture_output=True, timeout=60)
        self.assert_rejected(self.check_root(self.root), "styles.xml:2", "markup")

    def test_a_gitignore_comment_fails(self):
        result = self.check_tree({
            ".gitignore": "# The reason.\nbuild/\n",
        })
        self.assert_rejected(result, ".gitignore:1", "line comment")

    def test_a_dockerfile_syntax_directive_passes_and_prose_fails(self):
        accepted = self.check_tree({
            "tools/sample/Dockerfile": "# syntax=docker/dockerfile:1\nFROM alpine\n",
        })
        self.assert_accepted(accepted)
        self.root.joinpath("tools/sample/Dockerfile").write_text(
            "FROM alpine\n# The reason for the base image.\nRUN true\n", encoding="utf-8",
        )
        subprocess.run(["git", "-C", str(self.root), "add", "-A"], check=True, capture_output=True, timeout=60)
        self.assert_rejected(self.check_root(self.root), "tools/sample/Dockerfile:2", "line comment")

    def test_generated_and_vendored_files_are_outside_the_policy(self):
        result = self.check_tree({
            "lib/Sample.kt": "package sample\n\n/** KDoc. */\ninternal fun sample(): Int = 1\n",
            "gradlew": "#!/bin/sh\n# Generated by Gradle.\nprintf '%s\\n' ok\n",
            "gradle/wrapper/gradle-wrapper.properties": "# Generated.\ndistributionUrl=https\\://example.test/x.zip\n",
            "third_party/vendor/OFL.txt": "# A vendored legal text.\n",
            "docs/notes.md": "<!-- Prose document. -->\n",
        })
        self.assert_accepted(result)

    def test_an_undeclared_file_class_fails(self):
        result = self.check_tree({
            "notes.rst": "Sample\n======\n\n.. note:: not a comment\n",
        })
        self.assert_rejected(result, "notes.rst", "undeclared file class", "coding-guidelines.md")

    def test_a_scope_that_resolves_to_no_file_fails(self):
        result = self.check_tree({"docs/notes.md": "# Prose.\n"})
        self.assert_rejected(result, "no in-scope file")

    def test_the_repository_tree_carries_kdoc_only(self):
        self.assert_accepted(self.check_root(ROOT))

    def check_named(self, files, names):
        for relative, content in files.items():
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
        return subprocess.run(
            [sys.executable, str(CHECK), "--repository-root", str(self.root), "--files", *names],
            capture_output=True, text=True, timeout=120,
        )

    def test_a_named_scope_checks_only_the_files_it_names(self):
        files = {
            "app/src/main/Present.kt": "package app\n\ninternal fun present(): Int = 1\n",
            "tools/run.sh": "#!/usr/bin/env bash\n# run it\nset -euo pipefail\n",
        }
        self.assert_accepted(self.check_named(files, ["app/src/main/Present.kt"]))
        result = self.check_named(files, ["tools/run.sh"])
        self.assert_rejected(result, "tools/run.sh:2", "line comment")

    def test_a_named_scope_with_no_in_scope_file_passes(self):
        result = self.check_named({"docs/notes.md": "# Prose.\n"}, ["docs/notes.md"])
        self.assert_accepted(result)
        self.assertIn("0 of 1 named file(s)", result.stdout)

    def test_a_hook_carrying_a_shell_comment_fails(self):
        result = self.check_named({
            ".githooks/pre-commit": "#!/usr/bin/env bash\n# format the staged files\nset -euo pipefail\n",
        }, [".githooks/pre-commit"])
        self.assert_rejected(result, ".githooks/pre-commit:2", "line comment")


if __name__ == "__main__":
    unittest.main()
