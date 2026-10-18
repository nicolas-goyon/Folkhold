#!/usr/bin/env python3
"""Calculates the version of a commit from the bump markers of the commits, and checks the pull request titles.

The version starts from the highest release tag (vX.Y.Z) that the commit contains. A pre-release suffix of the tag
is ignored: v0.1.0-alpha.1 gives 0.1.0. No tag gives 0.0.0. Then the commits after the tag apply their bump marker
in order, oldest first. The marker is at the end of the commit title, before the pull request number that GitHub
adds to a squash commit:

    fix(world): close the gates at night [patch] (#42)

- [patch] adds 1 to the patch number.
- [minor] adds 1 to the minor number and sets the patch number to 0.
- [major] adds 1 to the major number and sets the minor and patch numbers to 0.

Example from 1.2.3: [patch], [minor], [patch] give 1.2.4, 1.3.0, 1.3.1. A merge commit and a commit with no marker
keep the version. A commit of Dependabot with no marker counts as [patch].

--title adds the commit of a pull request after REV. For a pull request in a stack, give --title for each open pull
request of the stack, from the bottom pull request to this pull request.

Usage:
    scripts/version.py version [--rev REV] [--title TITLE]... [--author LOGIN] [--repo OWNER/NAME] [--notes FILE]
                               [--github-output FILE]
    scripts/version.py check-title TITLE
"""
import argparse
import re
import subprocess
import sys
from dataclasses import dataclass

BUMPS = ("patch", "minor", "major")
TYPES = ("feat", "fix", "perf", "refactor", "docs", "test", "build", "ci", "chore", "style", "revert")
TAG = re.compile(r"^v(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$")
# The marker at the end of a commit title, with the "(#N)" that GitHub adds to a squash commit.
MARKER = re.compile(r"\s\[(patch|minor|major)\](?:\s+\(#\d+\))?\s*$")
TITLE = re.compile(
    r"^(?P<type>" + "|".join(TYPES) + r")(?:\((?P<scope>[a-z0-9][a-z0-9,/-]*)\))?!?: \S.*\S \[(?P<bump>patch|minor|major)\]$")
DEPENDABOT = "dependabot[bot]"


@dataclass(frozen=True)
class Version:
    major: int
    minor: int
    patch: int

    def bump(self, kind):
        """The version after a bump of the given kind ("patch", "minor" or "major"), or this version for None."""
        if kind == "major":
            return Version(self.major + 1, 0, 0)
        if kind == "minor":
            return Version(self.major, self.minor + 1, 0)
        if kind == "patch":
            return Version(self.major, self.minor, self.patch + 1)
        return self

    def __str__(self):
        return f"{self.major}.{self.minor}.{self.patch}"


@dataclass(frozen=True)
class Commit:
    sha: str
    title: str
    author: str
    bump: str  # "patch", "minor", "major" or None
    version: Version


def git(*args):
    return subprocess.run(("git",) + args, check=True, capture_output=True, text=True).stdout


def marker(title, author=""):
    """The bump marker of a commit title, or None. A commit of Dependabot with no marker is a patch."""
    found = MARKER.search(title)
    if found:
        return found.group(1)
    if author == DEPENDABOT:
        return "patch"
    return None


def without_marker(title):
    """The commit title without the bump marker. The pull request number stays."""
    return re.sub(r"\s\[(?:patch|minor|major)\](?=(?:\s+\(#\d+\))?\s*$)", "", title)


def parse_tag(name):
    """The version and the sort key of a release tag, or None for a tag that is not a release tag."""
    found = TAG.match(name)
    if not found:
        return None
    version = Version(int(found.group(1)), int(found.group(2)), int(found.group(3)))
    # A pre-release comes before the release with the same numbers.
    key = (version.major, version.minor, version.patch, found.group(4) is None)
    return version, key


def base_tag(rev):
    """The highest release tag that the commit contains, or None."""
    best = None
    for name in git("tag", "--merged", rev, "--list", "v*").split():
        parsed = parse_tag(name)
        if parsed and (best is None or parsed[1] > best[2]):
            best = (name, parsed[0], parsed[1])
    return None if best is None else (best[0], best[1])


def history(rev):
    """The base tag (or None), the base version and the commits after the base tag, oldest first."""
    base = base_tag(rev)
    span = f"{base[0]}..{rev}" if base else rev
    version = base[1] if base else Version(0, 0, 0)
    start = version
    commits = []
    log = git("log", "--reverse", "--topo-order", "--no-merges", "--format=%H%x1f%s%x1f%an%x1e", span)
    for record in log.split("\x1e"):
        record = record.strip()
        if not record:
            continue
        sha, title, author = record.split("\x1f")
        kind = marker(title, author)
        version = version.bump(kind)
        commits.append(Commit(sha, title, author, kind, version))
    return (base[0] if base else None), start, commits


def check_title(title):
    """None for a valid pull request title, else the reason."""
    if TITLE.match(title):
        return None
    if not MARKER.search(title):
        return ("The title must end with a bump marker: [patch], [minor] or [major]. "
                "Example: \"fix(world): close the gates at night [patch]\".")
    return ("The title must follow Conventional Commits: <type>(<scope>): <description> [<bump>]. "
            "Types: " + ", ".join(TYPES) + ".")


def release_notes(base, start, commits, version, repo):
    """The release notes: the version of each commit with a bump marker, newest first, then the other commits."""
    marked = [commit for commit in reversed(commits) if commit.bump]
    others = [commit for commit in reversed(commits) if not commit.bump]
    lines = ["## Versions", ""]
    if marked:
        lines += ["| Version | Change | Commit |", "| --- | --- | --- |"]
        for commit in marked:
            change = without_marker(commit.title).replace("|", "\\|")
            lines.append(f"| {commit.version} | {change} | {commit.sha[:7]} |")
    else:
        lines.append("No commit with a bump marker since the last release.")
    lines.append("")
    if others:
        count = f"{len(others)} commit" + ("" if len(others) == 1 else "s")
        lines += [f"<details><summary>{count} with no bump marker</summary>", ""]
        lines += [f"- {commit.version}: {commit.title} ({commit.sha[:7]})" for commit in others]
        lines += ["", "</details>", ""]
    if base:
        lines.append(f"The versions start from {start} ({base}).")
        if repo:
            lines.append(f"All changes: https://github.com/{repo}/compare/{base}...v{version}")
    else:
        lines.append(f"The versions start from {start}: no earlier release.")
    return "\n".join(lines) + "\n"


def run_version(args):
    base, start, commits = history(args.rev)
    for title in args.title or ():
        kind = marker(title, args.author or "")
        previous = commits[-1].version if commits else start
        commits.append(Commit("0" * 40, title, "", kind, previous.bump(kind)))
    version = commits[-1].version if commits else start
    tag = f"v{version}"
    exists = bool(git("tag", "--list", tag).strip())
    print(version)
    if args.notes:
        with open(args.notes, "w", encoding="utf-8") as out:
            out.write(release_notes(base, start, commits, version, args.repo))
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as out:
            out.write(f"version={version}\ntag={tag}\nexists={'true' if exists else 'false'}\n"
                      f"base={base or ''}\n")
    return 0


def run_check_title(args):
    reason = check_title(args.title)
    if reason:
        print(reason, file=sys.stderr)
        return 1
    print(f"OK: {TITLE.match(args.title).group('bump')}")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)
    version = commands.add_parser("version", help="print the version of a commit")
    version.add_argument("--rev", default="HEAD", help="the commit (default: HEAD)")
    version.add_argument("--title", action="append",
                         help="the title of one more commit after REV, for a pull request. Give it one time for each "
                              "open pull request of a stack, the bottom one first")
    version.add_argument("--author", help="the author of the commits of --title (dependabot[bot] counts as [patch])")
    version.add_argument("--repo", help="OWNER/NAME, for the link to all the changes in the notes")
    version.add_argument("--notes", help="write the release notes (Markdown) to this file")
    version.add_argument("--github-output", help="append version, tag, exists and base to this file")
    version.set_defaults(run=run_version)
    title = commands.add_parser("check-title", help="check the title of a pull request")
    title.add_argument("title")
    title.set_defaults(run=run_check_title)
    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
