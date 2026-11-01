#!/usr/bin/env python3
"""Verify that a distributable artifact carries the configured Sentry DSN.

A Nightly or beta build that is assembled without the DSN binds the no-op reporter and is silent in
the field. This check reads the DSN the build was given, requires it to be a well-formed Sentry DSN,
and requires the packaged dex to carry it before the artifact is signed or published.
"""
import os
from pathlib import Path
import re
import sys
import zipfile

DSN_PATTERN = re.compile(r"https?://[0-9A-Za-z_\-]+@[0-9A-Za-z.\-]+(?::[0-9]+)?/[0-9]+\Z")
DEX_PATTERN = re.compile(r"classes[0-9]*\.dex\Z")


class DsnError(Exception):
    """The artifact or its configured reporting value cannot be trusted."""


def require(condition, message):
    if not condition:
        raise DsnError(message)


def configured_dsn():
    dsn = os.environ.get("SENTRY_DSN", "").strip()
    require(dsn, "SENTRY_DSN is blank: a distributed build would report nothing")
    require(DSN_PATTERN.fullmatch(dsn) is not None, "SENTRY_DSN is not a Sentry DSN")
    return dsn


def packaged_dex(apk):
    require(apk.is_file() and not apk.is_symlink(), f"Missing or unsafe artifact: {apk}")
    try:
        archive = zipfile.ZipFile(apk)
    except zipfile.BadZipFile as error:
        raise DsnError(f"The artifact is not a readable APK archive: {apk}") from error
    with archive:
        entries = [name for name in archive.namelist() if DEX_PATTERN.fullmatch(name)]
        require(entries, f"The artifact packages no dex entry: {apk}")
        return b"".join(archive.read(name) for name in entries)


def verify(apk):
    dsn = configured_dsn()
    require(dsn.encode() in packaged_dex(apk), "The artifact does not carry the configured SENTRY_DSN")
    return dsn


def main(argv):
    require(len(argv) == 2, "usage: verify-release-dsn.py <apk>")
    try:
        dsn = verify(Path(argv[1]))
    except (DsnError, OSError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    print(f"The artifact carries the release DSN for project {dsn.rsplit('/', 1)[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
