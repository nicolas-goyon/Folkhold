"""Tests of scripts/version.py. Run: python3 -m unittest discover -s scripts -p "test_*.py" """
import os
import subprocess
import tempfile
import unittest

import version


class TitleTest(unittest.TestCase):
    def test_valid_titles(self):
        for title in ("fix(world): close the gates at night [patch]",
                      "feat(core,world): faction codec [minor]",
                      "refactor!: new save format [major]",
                      "docs: centered header in the README [patch]"):
            with self.subTest(title=title):
                self.assertIsNone(version.check_title(title))

    def test_title_with_no_marker(self):
        self.assertIn("bump marker", version.check_title("fix(world): close the gates at night"))

    def test_title_with_a_marker_in_the_wrong_place(self):
        self.assertIn("bump marker", version.check_title("[patch] fix(world): close the gates at night"))

    def test_title_that_is_not_a_conventional_commit(self):
        self.assertIn("Conventional Commits", version.check_title("Close the gates at night [patch]"))
        self.assertIn("Conventional Commits", version.check_title("feature(world): gates [minor]"))


class MarkerTest(unittest.TestCase):
    def test_marker_of_a_squash_commit(self):
        self.assertEqual("minor", version.marker("feat(world): caravans [minor] (#42)"))
        self.assertEqual("patch", version.marker("fix: gates [patch]"))

    def test_no_marker(self):
        self.assertIsNone(version.marker("fix: gates (#42)"))
        self.assertIsNone(version.marker("fix: [patch] gates"))

    def test_dependabot_commit_is_a_patch(self):
        self.assertEqual("patch", version.marker("ci: bump actions/checkout (#50)", "dependabot[bot]"))

    def test_title_without_marker(self):
        self.assertEqual("feat(world): caravans (#42)", version.without_marker("feat(world): caravans [minor] (#42)"))
        self.assertEqual("fix: gates", version.without_marker("fix: gates [patch]"))


class BumpTest(unittest.TestCase):
    def test_bumps_in_order_reset_the_lower_numbers(self):
        current = version.Version(1, 2, 3)
        steps = []
        for kind in ("patch", "minor", "patch", "major", "patch", None):
            current = current.bump(kind)
            steps.append(str(current))
        self.assertEqual(["1.2.4", "1.3.0", "1.3.1", "2.0.0", "2.0.1", "2.0.1"], steps)

    def test_tag_versions(self):
        self.assertEqual(version.Version(0, 1, 0), version.parse_tag("v0.1.0-alpha.1")[0])
        self.assertGreater(version.parse_tag("v0.1.0")[1], version.parse_tag("v0.1.0-alpha.1")[1])
        self.assertIsNone(version.parse_tag("v1.2"))
        self.assertIsNone(version.parse_tag("release-1.2.3"))


class HistoryTest(unittest.TestCase):
    """Builds a repository: main with a pre-release tag, develop with squash commits, merges into main."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cwd = os.getcwd()
        os.chdir(self.tmp.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.com")
        self.commit("chore: initial commit")
        self.git("tag", "v0.1.0-alpha.1")
        self.commit("fix(world): change with no marker (#1)")
        self.git("checkout", "-q", "-b", "develop")

    def tearDown(self):
        os.chdir(self.cwd)
        self.tmp.cleanup()

    def git(self, *args):
        return subprocess.run(("git",) + args, check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, title, author=None):
        args = ["commit", "-q", "--allow-empty", "-m", title]
        if author:
            args += ["--author", f"{author} <{author}@example.com>"]
        self.git(*args)
        return self.git("rev-parse", "HEAD")

    def merge_into_main(self):
        self.git("checkout", "-q", "main")
        self.git("merge", "-q", "--no-ff", "-m", "Merge pull request #9 from owner/develop", "develop")
        merge = self.git("rev-parse", "HEAD")
        self.git("checkout", "-q", "develop")
        return merge

    def test_versions_from_the_pre_release_tag(self):
        self.commit("fix(world): gates [patch] (#2)")
        self.commit("feat(world): caravans [minor] (#3)")
        self.commit("fix(world): boats [patch] (#4)")
        base, start, commits = version.history("develop")
        self.assertEqual("v0.1.0-alpha.1", base)
        self.assertEqual("0.1.0", str(start))
        self.assertEqual(["0.1.0", "0.1.1", "0.2.0", "0.2.1"], [str(c.version) for c in commits])

    def test_release_and_next_versions(self):
        self.commit("fix(world): gates [patch] (#2)")
        merge = self.merge_into_main()
        released = self.git("rev-parse", f"{merge}^2")
        self.git("tag", "v0.1.1", released)
        self.commit("feat(world): caravans [minor] (#3)")
        self.commit("ci: bump a dependency (#4)", author="dependabot[bot]")
        self.commit("feat!: new save format [major] (#5)")
        _, start, commits = version.history("develop")
        self.assertEqual("0.1.1", str(start))
        self.assertEqual(["0.2.0", "0.2.1", "1.0.0"], [str(c.version) for c in commits])
        # The merge commit of main has no marker: the version on main is the version of develop.
        merge = self.merge_into_main()
        _, _, on_main = version.history(merge)
        self.assertEqual("1.0.0", str(on_main[-1].version))

    def test_pull_request_title_comes_after_the_branch(self):
        self.commit("fix(world): gates [patch] (#2)")
        out = os.path.join(self.tmp.name, "output.txt")
        self.assertEqual(0, version.main(["version", "--rev", "develop", "--title", "feat: quests [minor]",
                                          "--github-output", out]))
        with open(out, encoding="utf-8") as lines:
            values = dict(line.strip().split("=", 1) for line in lines if "=" in line)
        self.assertEqual("0.2.0", values["version"])
        self.assertEqual("false", values["exists"])

    def test_titles_of_a_stack_come_in_order(self):
        self.commit("fix(world): gates [patch] (#2)")
        out = os.path.join(self.tmp.name, "output.txt")
        self.assertEqual(0, version.main(["version", "--rev", "develop", "--title", "feat(blocks): library [minor]",
                                          "--title", "fix(blocks): brazier [patch]", "--github-output", out]))
        with open(out, encoding="utf-8") as lines:
            values = dict(line.strip().split("=", 1) for line in lines if "=" in line)
        # 0.1.1, then the bottom pull request gives 0.2.0, and the pull request above it gives 0.2.1.
        self.assertEqual("0.2.1", values["version"])

    def test_release_notes(self):
        self.commit("fix(world): gates [patch] (#2)")
        self.commit("feat(world): caravans [minor] (#3)")
        notes = os.path.join(self.tmp.name, "notes.md")
        version.main(["version", "--rev", "develop", "--repo", "owner/repo", "--notes", notes])
        with open(notes, encoding="utf-8") as text:
            content = text.read()
        self.assertIn("| 0.2.0 | feat(world): caravans (#3) |", content)
        self.assertIn("| 0.1.1 | fix(world): gates (#2) |", content)
        self.assertLess(content.index("0.2.0"), content.index("0.1.1"))
        self.assertIn("1 commit with no bump marker", content)
        self.assertIn("- 0.1.0: fix(world): change with no marker (#1)", content)
        self.assertIn("https://github.com/owner/repo/compare/v0.1.0-alpha.1...v0.2.0", content)


if __name__ == "__main__":
    unittest.main()
