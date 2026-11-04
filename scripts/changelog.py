#!/usr/bin/env python3
"""Checks the changelog entries, and gives the player-facing changes of each version.

A pull request with a change that the player sees adds one entry file to changelog/. The name of the file is a free
slug, usually the issue number and the short name of the branch: changelog/412-night-gates.json. A pull request can
add more than one file. An entry file has these fields, and no other field:

    {
      "category": "fixed",
      "en_us": "The gates of the village close at night.",
      "fr_fr": "Les portes du village ferment la nuit."
    }

- category: "new", "changed" or "fixed".
- en_us, fr_fr: the text for the player, one line of 1 to 300 characters.

The version of an entry comes from Git. The commit that adds the file is the squash commit of the pull request: its
title gives the number of the pull request. The version of the entry is the first release tag that contains this
commit. An entry with no release tag gets the version of REV (see scripts/version.py).

Usage:
    scripts/changelog.py check [--dir DIR]
    scripts/changelog.py build [--rev REV] [--notes FILE] [--json FILE]

check validates the entry files of DIR (default: changelog). build prepends the entries of the version of REV to the
release notes in FILE, and writes all the entries, grouped by version, to the JSON file.
"""
import argparse
import json
import os
import re
import sys

import version

DIR = "changelog"
CATEGORIES = ("new", "changed", "fixed")
LANGUAGES = ("en_us", "fr_fr")
FIELDS = ("category",) + LANGUAGES
MAX_LENGTH = 300
NAME = re.compile(r"^[a-z0-9][a-z0-9-]*\.json$")
PULL_REQUEST = re.compile(r"\(#(\d+)\)\s*$")
TITLES = {
    "en_us": {"heading": "What's new", "new": "New", "changed": "Changed", "fixed": "Fixed"},
    "fr_fr": {"heading": "Nouveautés", "new": "Nouveau", "changed": "Modifié", "fixed": "Corrigé"},
}


def check_entry(name, text):
    """The errors of one entry file, as "field: reason" strings. An empty list for a valid entry."""
    errors = []
    if not NAME.match(name):
        errors.append("file name: use lowercase letters, digits and hyphens, then .json. "
                      "Example: 412-night-gates.json")
    try:
        entry = json.loads(text)
    except ValueError as error:
        return errors + [f"JSON: {error}"]
    if not isinstance(entry, dict):
        return errors + ["JSON: the entry must be an object with the fields " + ", ".join(FIELDS)]
    for field in sorted(set(entry) - set(FIELDS)):
        errors.append(f"{field}: unknown field. The fields are " + ", ".join(FIELDS))
    if "category" not in entry:
        errors.append("category: missing field")
    elif entry["category"] not in CATEGORIES:
        errors.append(f"category: {json.dumps(entry['category'])} is not valid. Use " + ", ".join(CATEGORIES))
    for language in LANGUAGES:
        value = entry.get(language)
        if language not in entry:
            errors.append(f"{language}: missing field")
        elif not isinstance(value, str) or not value.strip():
            errors.append(f"{language}: the text must be a string that is not empty")
        elif "\n" in value or len(value) > MAX_LENGTH:
            errors.append(f"{language}: the text must be one line of {MAX_LENGTH} characters or less")
    return errors


def check_dir(path):
    """The errors of all the files of the directory, as "file: field: reason" strings. No directory is valid."""
    if not os.path.isdir(path):
        return []
    errors = []
    for name in sorted(os.listdir(path)):
        file = os.path.join(path, name)
        if os.path.isdir(file):
            errors.append(f"{file}: the directory {path} must contain only entry files")
            continue
        with open(file, encoding="utf-8") as text:
            errors += [f"{file}: {error}" for error in check_entry(name, text.read())]
    return errors


def release_of(sha):
    """The name of the first release tag that contains the commit, without the "v", or None."""
    best = None
    for name in version.git("tag", "--contains", sha, "--list", "v*").split():
        parsed = version.parse_tag(name)
        if parsed and (best is None or parsed[1] < best[1]):
            best = (name, parsed[1])
    return None if best is None else best[0][1:]


def entries(rev, path=DIR):
    """The version of REV, and the entries of REV grouped by version, the newest version first.

    Each item is {"version": ..., "entries": [...]}. Each entry has the fields of its file, plus "pull_request" (a
    number or None). In a version, the entries are in the order of CATEGORIES, then of the commits, oldest first.
    """
    _, start, commits = version.history(rev)
    current = str(commits[-1].version if commits else start)
    files = version.git("ls-tree", "-r", "--name-only", rev, "--", f"{path}/").split()
    if not files:
        return current, []
    # The newest commit that adds each file. The log gives the newest commit first.
    added = {}
    log = version.git("log", "--diff-filter=A", "--no-merges", "--name-only", "--format=%x1e%H%x1f%s", rev, "--",
                      f"{path}/")
    for record in log.split("\x1e"):
        lines = record.strip().splitlines()
        if not lines:
            continue
        sha, title = lines[0].split("\x1f", 1)
        for file in lines[1:]:
            added.setdefault(file.strip(), (sha, title))
    order = {sha: index for index, sha in enumerate(
        version.git("rev-list", "--reverse", "--topo-order", rev).split())}
    groups = {}
    for file in files:
        # A file that only a merge commit adds gets the version of REV.
        sha, title = added.get(file, (None, ""))
        entry = json.loads(version.git("show", f"{rev}:{file}"))
        number = PULL_REQUEST.search(title)
        item = {"category": entry["category"], "pull_request": int(number.group(1)) if number else None}
        item.update({language: entry[language] for language in LANGUAGES})
        name = (release_of(sha) if sha else None) or current
        key = (CATEGORIES.index(item["category"]), order.get(sha, len(order)), file)
        groups.setdefault(name, []).append((key, item))
    result = []
    for name, items in groups.items():
        result.append({"version": name, "entries": [item for _, item in sorted(items, key=lambda i: i[0])]})
    result.sort(key=lambda group: version.parse_tag("v" + group["version"])[1], reverse=True)
    return current, result


def notes(group):
    """The Markdown of the entries of one version: English, then French in a closed section."""
    def section(language, level):
        lines = []
        for category in CATEGORIES:
            texts = [entry for entry in group["entries"] if entry["category"] == category]
            if texts:
                lines += [f"{level} {TITLES[language][category]}", ""]
                lines += [f"- {entry[language]}" + (f" (#{entry['pull_request']})" if entry["pull_request"] else "")
                          for entry in texts]
                lines.append("")
        return lines

    lines = [f"## {TITLES['en_us']['heading']}", ""] + section("en_us", "###")
    lines += [f"<details><summary>{TITLES['fr_fr']['heading']} (français)</summary>", ""]
    lines += section("fr_fr", "####") + ["</details>", ""]
    return "\n".join(lines) + "\n"


def run_check(args):
    errors = check_dir(args.dir)
    for error in errors:
        file, reason = error.split(": ", 1)
        print(f"::error file={file}::{reason}" if os.environ.get("GITHUB_ACTIONS") else error, file=sys.stderr)
    if errors:
        print(f"{len(errors)} error(s) in the changelog entries. See CONTRIBUTING.md, section Changelog.",
              file=sys.stderr)
        return 1
    count = len(os.listdir(args.dir)) if os.path.isdir(args.dir) else 0
    print(f"OK: {count} changelog entr{'y' if count == 1 else 'ies'}.")
    return 0


def run_build(args):
    current, groups = entries(args.rev, args.dir)
    group = next((group for group in groups if group["version"] == current), None)
    if args.notes and group:
        with open(args.notes, encoding="utf-8") as text:
            rest = text.read()
        with open(args.notes, "w", encoding="utf-8") as out:
            out.write(notes(group) + rest)
    if args.json:
        with open(args.json, "w", encoding="utf-8") as out:
            json.dump({"versions": groups}, out, ensure_ascii=False, indent=2)
            out.write("\n")
    count = len(group["entries"]) if group else 0
    print(f"{current}: {count} player-facing change{'' if count == 1 else 's'}.")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("check", help="check the entry files")
    check.add_argument("--dir", default=DIR, help=f"the directory of the entries (default: {DIR})")
    check.set_defaults(run=run_check)
    build = commands.add_parser("build", help="write the entries for the release notes and for the mod")
    build.add_argument("--rev", default="HEAD", help="the commit (default: HEAD)")
    build.add_argument("--dir", default=DIR, help=f"the directory of the entries (default: {DIR})")
    build.add_argument("--notes", help="prepend the entries of the version of REV to this Markdown file")
    build.add_argument("--json", help="write all the entries, grouped by version, to this JSON file")
    build.set_defaults(run=run_build)
    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
