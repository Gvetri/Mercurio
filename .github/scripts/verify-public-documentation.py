#!/usr/bin/env python3
"""Verify the published documentation before it is announced.

The public documentation is part of the product boundary: a link that does not resolve, a credential
or a real-looking endpoint in an example, a private infrastructure reference, or an instruction
outside the supported product scope misleads a user before any code runs. This check reads the
committed Markdown, resolves every relative link and in-page anchor, applies the safety rules, and
verifies that the user-facing documents still state the approved scope. It fails closed.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

README = Path("README.md")
USER_GUIDE = Path("docs/user-guide.md")
RELEASE_ARTIFACTS = Path("docs/release-artifacts.md")
PINNED_FIXTURE = Path("fixtures/hermes/pinned-fixture.properties")
DIAGNOSTICS_SURFACE = Path(
    "feature/entry/presentation/src/main/kotlin/org/hermesnative/client/feature/entry/presentation"
    "/LocalDiagnosticsScreen.kt"
)

LINK_CHECK_DOCUMENTS = (README,)
LINK_CHECK_DIRECTORIES = (Path("docs"),)
PRODUCT_SCOPE_DOCUMENTS = (README, USER_GUIDE, RELEASE_ARTIFACTS)
OPERATOR_IDENTITY_DOCUMENTS = (USER_GUIDE, RELEASE_ARTIFACTS)

ALLOWED_PUBLIC_HOSTS = frozenset(
    {
        "github.com",
        "api.github.com",
        "developer.android.com",
        "hermes-agent.nousresearch.com",
    }
)
RESERVED_EXAMPLE_HOSTS = frozenset({"example.com", "example.net", "example.org"})
RESERVED_EXAMPLE_SUFFIX = ".example"

SENSITIVE_PATTERNS = (
    ("a private filesystem path", re.compile(r"(?<![\w.])/(?:home|Users)/\S")),
    ("an SSH or Git remote reference", re.compile(r"(?:ssh://|\bgit@[A-Za-z0-9.-]+:)")),
    (
        "a credential value",
        re.compile(r"\b[A-Z][A-Z0-9_]*(?:API_KEY|TOKEN|SECRET|PASSWORD)\s*=\s*(?!<)[A-Za-z0-9_+/=-]{16,}"),
    ),
    ("a GitHub token", re.compile(r"\b(?:ghp_|gho_|ghs_|github_pat_)[A-Za-z0-9_]{10,}")),
    ("a private key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("a bare bearer credential", re.compile(r"\bBearer\s+[A-Za-z0-9._+/=-]{20,}")),
    (
        "a private network address",
        re.compile(
            r"\b(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}"
            r"|192\.168\.\d{1,3}\.\d{1,3}"
            r"|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b"
        ),
    ),
)

OPERATOR_IDENTITY_PATTERNS = (
    ("a local smoke or operator identity parameter", re.compile(r"\b(?:local-live-smoke|device-serial|avd-name|video-recipient)\b")),
    ("a provider credential name", re.compile(r"\bOPENCODE_GO_API_KEY\b")),
)

OUT_OF_SCOPE_INSTRUCTIONS = (
    "docker run",
    "docker compose",
    "docker-compose",
    "docker build",
    "kubectl",
    "helm install",
    "helm upgrade",
    "systemctl",
    "pip install",
    "uv tool install",
    "uvx ",
    "npm install",
    "hermes gateway",
    "nginx",
    "caddy",
    "certbot",
    "reverse proxy",
    "port forward",
    "port-forward",
)

REQUIRED_USER_GUIDE_STATEMENTS = (
    "Android 8.0",
    "minSdk 24",
    "phones and tablets",
    "arm64-v8a",
    "pre-existing compatible Hermes Gateway",
    "profile-specific HTTPS endpoint",
    "bearer Gateway Credential",
    "saved securely on this device after verification",
    "excluded from Android backup",
    "does not create, host, configure networking for, or publish a Gateway",
    "prereleases",
    "Stable Public Beta vX.Y.Z",
    "Nightly",
    "community support only",
    "no service-level agreement",
    "no network analytics, no product telemetry, and no third-party crash reporting",
    "not persisted on the device as an offline history",
    "terminal status only",
    "Hermes Run",
    "Session",
    "Send",
    "retry",
    "Try again",
    "Invalid Gateway address",
    "Secure connection failed",
    "Authentication failed",
    "Required feature unavailable",
    "Gateway request failed",
)

REQUIRED_LOCAL_DIAGNOSTICS_STATEMENTS = (
    "Local diagnostics",
    "Export diagnostics",
    "Clear diagnostics",
    "uploaded automatically",
)

REQUIRED_RELEASE_ARTIFACT_STATEMENTS = (
    "hermes-native-client.apk",
    "SHA256SUMS",
    "signing-metadata.json",
    "Semantic Version",
    "Milestone",
    "SHA-256",
    "community-support-only",
    "compatibility statement",
    ".github/scripts/verify-release-artifacts.py",
)

FENCE = re.compile(r"^```[^\n]*\n.*?^```[^\n]*$", re.MULTILINE | re.DOTALL)
INLINE_LINK = re.compile(r"!?\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
REFERENCE_LINK = re.compile(r"^\[[^\]]+\]:\s*(\S+)", re.MULTILINE)
HEADING = re.compile(r"^#{1,6}\s+(.+?)\s*$", re.MULTILINE)
URL = re.compile(r"https?://([^\s/`\"'()\[\]<>]+)")
HEADING_PUNCTUATION = re.compile(r"[^\w\- ]")


class DocumentationError(Exception):
    """The published documentation cannot be trusted."""


def require(condition, message):
    if not condition:
        raise DocumentationError(message)


def read(root, relative):
    path = root / relative
    require(path.is_file(), f"The published document {relative} is missing")
    return path.read_text(encoding="utf-8")


def documents(root):
    """Every Markdown document a user or a contributor can follow a link into."""
    found = []
    for relative in LINK_CHECK_DOCUMENTS:
        found.append(root / relative)
    for directory in LINK_CHECK_DIRECTORIES:
        found.extend(sorted((root / directory).rglob("*.md")))
    return [path for path in found if path.is_file()]


def prose(text):
    """The text with fenced code blocks removed, so a sample command is not read as a link."""
    return FENCE.sub("", text)


def slug(heading):
    return HEADING_PUNCTUATION.sub("", heading.strip().lower()).replace(" ", "-")


def anchors(text):
    """Every anchor the document exposes, matching GitHub's duplicate-heading suffixes."""
    seen = {}
    found = set()
    for heading in HEADING.findall(prose(text)):
        base = slug(heading)
        if not base:
            continue
        count = seen.get(base, 0)
        seen[base] = count + 1
        found.add(base if count == 0 else f"{base}-{count}")
    return found


def link_targets(text):
    plain = prose(text)
    return INLINE_LINK.findall(plain) + REFERENCE_LINK.findall(plain)


def verify_links(root):
    violations = []
    for document in documents(root):
        text = document.read_text(encoding="utf-8")
        relative_document = document.relative_to(root)
        for target in link_targets(text):
            if target.startswith(("http://", "https://", "mailto:")):
                continue
            path, _, fragment = target.partition("#")
            resolved = document.parent / path if path else document
            if not resolved.exists():
                violations.append(f"{relative_document} links to a missing target: {target}")
                continue
            if fragment and resolved.is_file() and resolved.suffix == ".md":
                if fragment not in anchors(resolved.read_text(encoding="utf-8")):
                    violations.append(f"{relative_document} links to a missing anchor: {target}")
    return violations


def url_hosts(text):
    hosts = []
    for match in URL.findall(text):
        host = match.split("@")[-1].split(":")[0].lower()
        if host:
            hosts.append(host)
    return hosts


def is_allowed_host(host):
    return (
        host in ALLOWED_PUBLIC_HOSTS
        or host in RESERVED_EXAMPLE_HOSTS
        or host.endswith(RESERVED_EXAMPLE_SUFFIX)
    )


def verify_safety(root):
    violations = []
    for document in documents(root):
        relative_document = document.relative_to(root)
        text = document.read_text(encoding="utf-8")
        for description, pattern in SENSITIVE_PATTERNS:
            match = pattern.search(text)
            if match is not None:
                violations.append(f"{relative_document} contains {description}: {match.group(0)!r}")
    for relative in OPERATOR_IDENTITY_DOCUMENTS:
        text = read(root, relative)
        for description, pattern in OPERATOR_IDENTITY_PATTERNS:
            match = pattern.search(text)
            if match is not None:
                violations.append(f"{relative} contains {description}: {match.group(0)!r}")
    for relative in PRODUCT_SCOPE_DOCUMENTS:
        text = read(root, relative)
        for command in OUT_OF_SCOPE_INSTRUCTIONS:
            if command in text:
                violations.append(
                    f"{relative} gives an instruction outside the supported product scope: {command!r}"
                )
        for host in url_hosts(text):
            if not is_allowed_host(host):
                violations.append(
                    f"{relative} uses a real-looking endpoint example on {host!r}; "
                    "use a reserved example host or an allowlisted public host"
                )
    return violations


def verify_required_statements(text, statements, document):
    return [f"{document} must state: {statement}" for statement in statements if statement not in text]


def pinned_hermes_revision(root):
    text = read(root, PINNED_FIXTURE)
    for line in text.splitlines():
        key, _, value = line.partition("=")
        if key.strip() == "hermes_revision":
            revision = value.strip()
            require(
                re.fullmatch(r"[0-9a-f]{40}", revision) is not None,
                "The pinned Hermes revision is not a full immutable revision",
            )
            return revision
    raise DocumentationError("The pinned fixture descriptor declares no Hermes revision")


def verify_scope(root):
    violations = []
    user_guide = read(root, USER_GUIDE)
    statements = list(REQUIRED_USER_GUIDE_STATEMENTS)
    if (root / DIAGNOSTICS_SURFACE).is_file():
        statements.extend(REQUIRED_LOCAL_DIAGNOSTICS_STATEMENTS)
    violations.extend(verify_required_statements(user_guide, statements, USER_GUIDE))
    release_artifacts = read(root, RELEASE_ARTIFACTS)
    violations.extend(
        verify_required_statements(release_artifacts, REQUIRED_RELEASE_ARTIFACT_STATEMENTS, RELEASE_ARTIFACTS)
    )
    revision = pinned_hermes_revision(root)
    if revision not in user_guide and revision not in release_artifacts:
        violations.append(
            f"The documentation must identify the tested Hermes revision {revision} "
            f"or its immutable replacement"
        )
    readme = read(root, README)
    for relative in (USER_GUIDE, RELEASE_ARTIFACTS):
        if str(relative) not in readme:
            violations.append(f"{README} must link the published document {relative}")
    return violations


def verify(root):
    require((root / README).is_file(), "The repository root is not a Mercurio checkout")
    violations = []
    violations.extend(verify_links(root))
    violations.extend(verify_safety(root))
    violations.extend(verify_scope(root))
    return violations


def main(arguments):
    try:
        root = ROOT
        if arguments[:1] == ["--root"]:
            require(len(arguments) == 2, "Expected --root <directory>")
            root = Path(arguments[1]).resolve()
        elif arguments:
            raise DocumentationError("Unsupported argument")
        violations = verify(root)
    except DocumentationError as error:
        print(f"Published documentation failed closed: {error}", file=sys.stderr)
        return 1
    if violations:
        for violation in violations:
            print(violation, file=sys.stderr)
        print("Published documentation verification failed.", file=sys.stderr)
        return 1
    print(f"Verified {len(documents(root))} published document(s): links, safety, and scope.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
