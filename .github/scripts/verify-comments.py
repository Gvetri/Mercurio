#!/usr/bin/env python3
"""Fail when a hand-maintained file carries a comment other than KDoc.

The policy lives in docs/coding-guidelines.md: KDoc is the only allowed comment form, and the
exceptions are the tool directives a tool requires, a license header, and the generated or vendored
files the class table excludes. Every tracked file gets a reading; a class this verifier does not
know fails instead of passing, so a new file class is decided in the same change that adds it.

A caller that already knows the scope it cares about, the pre-commit hook that reads the staged
files, names it with `--files`. A named scope that holds no in-scope file passes, because the caller
declared the scope rather than the repository being empty.
"""
import argparse
import bisect
import pathlib
import re
import subprocess
import sys

LINE = "line"
BLOCK = "block"
MARKUP = "markup"
UNDECLARED = "undeclared"

REMEDY = "delete it, or move its constraint into a test, a check, or docs/ first; KDoc is the only allowed comment form"
UNDECLARED_REMEDY = (
    "declare the file class in docs/coding-guidelines.md and teach .github/scripts/verify-comments.py to read it"
)

LICENSE_MARKERS = (
    "copyright",
    "licensed under",
    "spdx-license-identifier",
    "apache license",
    "mit license",
    "gnu general public license",
    "all rights reserved",
)

DIRECTIVE_PREFIXES = (
    "noqa",
    "type:",
    "shellcheck",
    "syntax=",
    "escape=",
    "check=",
    "pragma:",
    "coding:",
    "fmt:",
    "pylint:",
    "mypy:",
    "-*- coding",
)

SCRIPT_KEYS = frozenset({"run", "script"})
BLOCK_SCALAR = re.compile(r"(?:^|:|-)\s*[|>][-+]?[0-9]?[-+]?\s*$")
HEREDOC = re.compile(r"<<(-?)\s*(['\"]?)([A-Za-z_][A-Za-z0-9_]*)\2")


def _newlines(text):
    return [index for index, character in enumerate(text) if character == "\n"]


def _line_of(newlines, position):
    return bisect.bisect_left(newlines, position) + 1


def _skip_delimited(text, position, delimiter, escapes):
    length = len(text)
    while position < length:
        if escapes and text[position] == "\\":
            position += 2
            continue
        if text.startswith(delimiter, position):
            return position + len(delimiter)
        position += 1
    return length


def _skip_block_comment(text, position):
    depth = 0
    length = len(text)
    while position < length:
        if text.startswith("/*", position):
            depth += 1
            position += 2
        elif text.startswith("*/", position):
            depth -= 1
            position += 2
            if depth == 0:
                return position
        else:
            position += 1
    return length


def _end_of_line(text, position):
    end = text.find("\n", position)
    return len(text) if end < 0 else end


def kotlin_comments(text):
    found = []
    position = 0
    length = len(text)
    while position < length:
        if text.startswith('"""', position):
            position = _skip_delimited(text, position + 3, '"""', False)
        elif text[position] == '"':
            position = _skip_delimited(text, position + 1, '"', True)
        elif text[position] == "'":
            position = _skip_delimited(text, position + 1, "'", True)
        elif text.startswith("//", position):
            found.append((position, LINE))
            position = _end_of_line(text, position)
        elif text.startswith("/**", position):
            position = _skip_block_comment(text, position)
        elif text.startswith("/*", position):
            found.append((position, BLOCK))
            position = _skip_block_comment(text, position)
        else:
            position += 1
    return found


def python_comments(text):
    found = []
    position = 0
    length = len(text)
    while position < length:
        character = text[position]
        if character in "\"'":
            triple = character * 3
            if text.startswith(triple, position):
                position = _skip_delimited(text, position + 3, triple, True)
            else:
                position = _skip_delimited(text, position + 1, character, True)
        elif character == "#":
            found.append((position, LINE))
            position = _end_of_line(text, position)
        else:
            position += 1
    return found


def shell_comments(text):
    found = []
    position = 0
    length = len(text)
    pending = []
    while position < length:
        if text.startswith("<<", position) and not text.startswith("<<<", position):
            heredoc = HEREDOC.match(text, position)
            if heredoc is not None:
                pending.append((heredoc.group(3), bool(heredoc.group(1))))
                position = heredoc.end()
                continue
            position += 2
            continue
        character = text[position]
        if character in "\"'":
            position = _skip_delimited(text, position + 1, character, character == '"')
        elif character == "#" and (position == 0 or text[position - 1] in " \t\n;&|("):
            found.append((position, LINE))
            position = _end_of_line(text, position)
        elif character == "\n":
            position += 1
            for delimiter, strip_tabs in pending:
                while position < length:
                    end = text.find("\n", position)
                    end = length if end < 0 else end
                    body = text[position:end]
                    position = min(end + 1, length)
                    if (body.strip() if strip_tabs else body) == delimiter:
                        break
            pending = []
        else:
            position += 1
    return found


def _yaml_code(line):
    quote = None
    position = 0
    length = len(line)
    while position < length:
        character = line[position]
        if quote == '"':
            if character == "\\":
                position += 2
                continue
            if character == '"':
                quote = None
        elif quote == "'":
            if character == "'":
                if line[position + 1:position + 2] == "'":
                    position += 2
                    continue
                quote = None
        elif character in "\"'":
            quote = character
        elif character == "#" and (position == 0 or line[position - 1] in " \t"):
            return line[:position], True
        position += 1
    return line, False


def yaml_comments(text):
    found = []
    lines = text.split("\n")
    offsets = []
    running = 0
    for line in lines:
        offsets.append(running)
        running += len(line) + 1
    index = 0
    while index < len(lines):
        line = lines[index]
        code, has_comment = _yaml_code(line)
        if has_comment:
            found.append((offsets[index] + len(code), LINE))
        if BLOCK_SCALAR.search(code):
            indent = len(line) - len(line.lstrip(" "))
            key = code.split(":", 1)[0].strip().lstrip("-").strip()
            content_start = index + 1
            content_end = content_start
            while content_end < len(lines):
                candidate = lines[content_end]
                if candidate.strip() and (len(candidate) - len(candidate.lstrip(" "))) <= indent:
                    break
                content_end += 1
            if key in SCRIPT_KEYS:
                block_lines = lines[content_start:content_end]
                block_offsets = []
                running_block = 0
                for block_line in block_lines:
                    block_offsets.append(running_block)
                    running_block += len(block_line) + 1
                block = "\n".join(block_lines)
                block_newlines = _newlines(block)
                for position, kind in shell_comments(block):
                    local = _line_of(block_newlines, position)
                    column = position - block_offsets[local - 1]
                    found.append((offsets[content_start + local - 1] + column, kind))
            index = content_end
            continue
        index += 1
    return found


def toml_comments(text):
    found = []
    position = 0
    length = len(text)
    while position < length:
        character = text[position]
        if character in "\"'":
            triple = character * 3
            if text.startswith(triple, position):
                position = _skip_delimited(text, position + 3, triple, True)
            else:
                position = _skip_delimited(text, position + 1, character, character == '"')
        elif character == "#":
            found.append((position, LINE))
            position = _end_of_line(text, position)
        else:
            position += 1
    return found


def _line_start_comments(text, markers):
    found = []
    position = 0
    for line in text.split("\n"):
        stripped = line.lstrip()
        if stripped[:1] and stripped[0] in markers:
            found.append((position + len(line) - len(stripped), LINE))
        position += len(line) + 1
    return found


def properties_comments(text):
    return _line_start_comments(text, "#!")


def marker_comments(text):
    return _line_start_comments(text, "#")


def editorconfig_comments(text):
    return _line_start_comments(text, "#;")


def xml_comments(text):
    found = []
    position = 0
    length = len(text)
    while position < length:
        if text.startswith("<!--", position):
            end = text.find("-->", position + 4)
            found.append((position, MARKUP))
            position = length if end < 0 else end + 3
        elif text.startswith("<![CDATA[", position):
            end = text.find("]]>", position)
            position = length if end < 0 else end + 3
        else:
            position += 1
    return found


HANDLERS = {
    ".kt": kotlin_comments,
    ".kts": kotlin_comments,
    ".py": python_comments,
    ".sh": shell_comments,
    ".yml": yaml_comments,
    ".yaml": yaml_comments,
    ".toml": toml_comments,
    ".properties": properties_comments,
    ".xml": xml_comments,
    ".sse": marker_comments,
    ".txt": marker_comments,
}

NAMED_HANDLERS = {
    "CODEOWNERS": marker_comments,
    ".gitignore": marker_comments,
    "Dockerfile": marker_comments,
    ".editorconfig": editorconfig_comments,
}

PATH_HANDLERS = ((".githooks/", shell_comments),)

EXCLUDED_SUFFIXES = frozenset(
    {".md", ".json", ".png", ".jar", ".ttf", ".pem", ".p12", ".sha256", ".bat", ".zip", ".jpg", ".webp", ".ico"}
)
EXCLUDED_NAMES = frozenset({"LICENSE", "gradlew"})
EXCLUDED_PREFIXES = ("third_party/", "gradle/wrapper/")


def handler_for(relative):
    path = pathlib.PurePosixPath(relative)
    if relative in EXCLUDED_NAMES or path.suffix in EXCLUDED_SUFFIXES:
        return None, "excluded"
    if any(relative.startswith(prefix) for prefix in EXCLUDED_PREFIXES):
        return None, "excluded"
    for prefix, handler in PATH_HANDLERS:
        if relative.startswith(prefix):
            return handler, None
    if path.name in NAMED_HANDLERS:
        return NAMED_HANDLERS[path.name], None
    handler = HANDLERS.get(path.suffix)
    if handler is None:
        return None, "undeclared"
    return handler, None


def _comment_span(text, position, kind):
    if kind == LINE:
        return position, _end_of_line(text, position)
    closing = "-->" if kind == MARKUP else "*/"
    end = text.find(closing, position)
    return position, len(text) if end < 0 else end + len(closing)


def _comment_body(text, position, kind):
    _, end = _comment_span(text, position, kind)
    body = text[position:end]
    if kind == LINE:
        return body[2 if body.startswith("#!") else 1:].strip().lower()
    for opening in ("/**", "/*", "<!--"):
        if body.startswith(opening):
            return body[len(opening):].strip().lower()
    return body.strip().lower()


def _is_shebang(text, position, kind):
    return position == 0 and kind == LINE and text.startswith("#!")


def violations_for(relative, text):
    handler, status = handler_for(relative)
    if handler is None:
        return [(1, UNDECLARED)] if status == "undeclared" else []
    newlines = _newlines(text)
    violations = []
    header_end = 0
    for position, kind in handler(text):
        body = _comment_body(text, position, kind)
        in_header = not text[header_end:position].strip()
        if _is_shebang(text, position, kind):
            header_end = _comment_span(text, position, kind)[1]
            continue
        if body.startswith(DIRECTIVE_PREFIXES):
            if in_header:
                header_end = _comment_span(text, position, kind)[1]
            continue
        if in_header and any(marker in body for marker in LICENSE_MARKERS):
            header_end = _comment_span(text, position, kind)[1]
            continue
        violations.append((_line_of(newlines, position), kind))
    return violations


def describe(relative, line, kind):
    if kind == UNDECLARED:
        return f"{relative}:{line}: undeclared file class; remedy: {UNDECLARED_REMEDY}"
    return f"{relative}:{line}: {kind} comment is not allowed; remedy: {REMEDY}"


def tracked_files(root):
    completed = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        capture_output=True,
        timeout=300,
        check=False,
    )
    if completed.returncode != 0:
        raise SystemExit(
            "Comments verification could not read the tracked files with git ls-files; "
            "it fails closed rather than scanning an unknown scope."
        )
    return sorted(name.decode("utf-8") for name in completed.stdout.split(b"\0") if name)


def scan(root, files=None):
    reports = []
    scanned = 0
    relatives = tracked_files(root) if files is None else [str(pathlib.PurePosixPath(name)) for name in files]
    for relative in relatives:
        handler, status = handler_for(relative)
        if handler is None and status == "excluded":
            continue
        if handler is None:
            reports.append(describe(relative, 1, UNDECLARED))
            continue
        try:
            text = pathlib.Path(root, relative).read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as error:
            raise SystemExit(f"Comments verification could not read {relative}: {error}")
        scanned += 1
        for line, kind in violations_for(relative, text):
            reports.append(describe(relative, line, kind))
    return sorted(reports), scanned


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository-root",
        default=str(pathlib.Path(__file__).resolve().parents[2]),
        help="the repository whose tracked files are verified",
    )
    parser.add_argument(
        "--files",
        nargs="*",
        default=None,
        help="verify these repository-relative files instead of every tracked file, as the pre-commit hook does",
    )
    arguments = parser.parse_args(argv)
    reports, scanned = scan(pathlib.Path(arguments.repository_root).resolve(), arguments.files)
    if reports:
        print("\n".join(reports), file=sys.stderr)
        print(f"Comments verified: {len(reports)} violation(s) across {scanned} in-scope file(s).", file=sys.stderr)
        return 1
    if scanned == 0 and arguments.files is None:
        print("Comments verification found no in-scope file; it fails closed.", file=sys.stderr)
        return 1
    if arguments.files is None:
        print(f"Comments verified: {scanned} in-scope file(s) carry KDoc only.")
    else:
        print(f"Comments verified: {scanned} of {len(arguments.files)} named file(s) are in scope and carry KDoc only.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
