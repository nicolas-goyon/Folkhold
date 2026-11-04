"""Tests of scripts/changelog.py. Run: python3 -m unittest discover -s scripts -p "test_*.py" """
import contextlib
import io
import json
import os
import subprocess
import tempfile
import unittest

import changelog

VALID = {"category": "fixed", "en_us": "The gates close at night.", "fr_fr": "Les portes ferment la nuit."}


class CheckTest(unittest.TestCase):
    def test_valid_entry(self):
        self.assertEqual([], changelog.check_entry("412-night-gates.json", json.dumps(VALID)))

    def test_errors_give_the_field(self):
        cases = [
            ("Gates.json", VALID, "file name:"),
            ("gates.txt", VALID, "file name:"),
            ("gates.json", dict(VALID, category="added"), 'category: "added" is not valid'),
            ("gates.json", {k: v for k, v in VALID.items() if k != "category"}, "category: missing field"),
            ("gates.json", {k: v for k, v in VALID.items() if k != "fr_fr"}, "fr_fr: missing field"),
            ("gates.json", dict(VALID, en_us="  "), "en_us: the text must be a string"),
            ("gates.json", dict(VALID, en_us=3), "en_us: the text must be a string"),
            ("gates.json", dict(VALID, fr_fr="a" * 301), "fr_fr: the text must have 300 characters"),
            ("gates.json", dict(VALID, de_de="Tore"), "de_de: unknown field"),
            ("gates.json", [VALID], "JSON: the entry must be an object"),
        ]
        for name, entry, error in cases:
            with self.subTest(error=error):
                errors = changelog.check_entry(name, json.dumps(entry))
                self.assertEqual(1, len(errors), errors)
                self.assertTrue(errors[0].startswith(error), errors)

    def test_characters_that_change_the_notes(self):
        for text in ("a\nb", "a\tb", "<!-- hidden", "a > b", "@someone", "`code`", "a\x85b"):
            with self.subTest(text=text):
                errors = changelog.check_entry("gates.json", json.dumps(dict(VALID, en_us=text)))
                self.assertEqual(["en_us: do not use <, >, @, ` or a control character (for example a line break)"],
                                 errors)
        self.assertEqual([], changelog.check_entry("gates.json", json.dumps(dict(VALID, fr_fr="L'été : « ok » !"))))

    def test_bad_json(self):
        self.assertTrue(changelog.check_entry("gates.json", "{")[0].startswith("JSON:"))

    def test_file_that_is_not_utf_8(self):
        data = json.dumps(dict(VALID, fr_fr="Été"), ensure_ascii=False).encode("latin-1")
        self.assertEqual(["encoding: use UTF-8"], changelog.check_entry("gates.json", data))

    def test_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "changelog")
            # No directory: a pull request with no entry is valid.
            self.assertEqual([], changelog.check_dir(path))
            self.assertEqual(0, changelog.main(["check", "--dir", path]))
            os.mkdir(path)
            with open(os.path.join(path, "1-good.json"), "w", encoding="utf-8") as out:
                json.dump(VALID, out)
            with open(os.path.join(path, "2-bad.json"), "w", encoding="utf-8") as out:
                json.dump(dict(VALID, category="other"), out)
            errors = changelog.check_dir(path)
            self.assertEqual(1, len(errors))
            self.assertTrue(errors[0].startswith(os.path.join(path, "2-bad.json") + ": category:"), errors)
            self.assertEqual(1, changelog.main(["check", "--dir", path]))


class BuildTest(unittest.TestCase):
    """Builds a repository with squash commits that add entries, and a release tag."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cwd = os.getcwd()
        os.chdir(self.tmp.name)
        self.git("init", "-q", "-b", "develop")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.com")
        self.commit("chore: initial commit")
        self.git("tag", "v0.1.0")

    def tearDown(self):
        os.chdir(self.cwd)
        self.tmp.cleanup()

    def git(self, *args):
        return subprocess.run(("git",) + args, check=True, capture_output=True, text=True).stdout.strip()

    def run_main(self, *args):
        """The exit code and the standard error of changelog.main()."""
        error = io.StringIO()
        with contextlib.redirect_stderr(error):
            code = changelog.main(list(args))
        return code, error.getvalue()

    def commit(self, title, **files):
        """A commit with one entry file for each keyword: name (with "_" for "-") and (category, English text)."""
        for name, (category, text) in files.items():
            os.makedirs("changelog", exist_ok=True)
            with open(f"changelog/{name.replace('_', '-')}.json", "w", encoding="utf-8") as out:
                json.dump({"category": category, "en_us": text, "fr_fr": f"fr: {text}"}, out)
            self.git("add", "changelog")
        self.git("commit", "-q", "--allow-empty", "-m", title)

    def build(self, *args):
        notes = os.path.join(self.tmp.name, "notes.md")
        out = os.path.join(self.tmp.name, "changelog.json")
        with open(notes, "w", encoding="utf-8") as text:
            text.write("## Versions\n")
        self.assertEqual(0, changelog.main(["build", "--rev", "HEAD", "--notes", notes, "--json", out, *args]))
        with open(notes, encoding="utf-8") as text, open(out, encoding="utf-8") as data:
            return text.read(), json.load(data)

    def test_no_entry_keeps_the_notes(self):
        self.commit("ci: faster build [patch] (#2)")
        notes, data = self.build()
        self.assertEqual("## Versions\n", notes)
        self.assertEqual({"versions": []}, data)

    def test_entries_grouped_by_release(self):
        self.commit("fix(world): gates [patch] (#2)", a2_gates=("fixed", "Gates close."))
        self.git("tag", "v0.1.1")
        self.commit("feat(world): caravans [minor] (#3)", a3_caravans=("new", "Caravans."))
        self.commit("ci: no player change [patch] (#4)")
        self.commit("feat(world): boats [minor] (#5)", a5_boats=("changed", "Boats are faster."),
                    a5_rafts=("new", "Rafts."))
        notes, data = self.build()
        versions = data["versions"]
        self.assertEqual(["0.3.0", "0.1.1"], [group["version"] for group in versions])
        self.assertEqual(["Caravans.", "Rafts.", "Boats are faster."], [e["en_us"] for e in versions[0]["entries"]])
        self.assertEqual([3, 5, 5], [e["pull_request"] for e in versions[0]["entries"]])
        self.assertEqual({"category": "fixed", "pull_request": 2, "en_us": "Gates close.", "fr_fr": "fr: Gates close."},
                         versions[1]["entries"][0])
        # The notes start with the entries of the version of HEAD only, then the earlier notes.
        self.assertTrue(notes.startswith("## What's new\n\n### New\n\n- Caravans. (#3)\n- Rafts. (#5)\n"), notes)
        self.assertIn("### Changed\n\n- Boats are faster. (#5)\n", notes)
        self.assertIn("#### Nouveau\n\n- fr: Caravans. (#3)\n", notes)
        self.assertNotIn("Gates close.", notes)
        self.assertTrue(notes.endswith("</details>\n\n## Versions\n"), notes)

    def test_entry_of_an_earlier_release_only(self):
        self.commit("fix(world): gates [patch] (#2)", a2_gates=("fixed", "Gates close."))
        self.git("tag", "v0.1.1")
        self.commit("ci: no player change [patch] (#3)")
        notes, data = self.build()
        self.assertEqual("## Versions\n", notes)
        self.assertEqual(["0.1.1"], [group["version"] for group in data["versions"]])

    def test_squash_title_with_no_pull_request_number(self):
        self.commit("feat(world): caravans [minor]", a3_caravans=("new", "Caravans."))
        notes, data = self.build()
        self.assertIsNone(data["versions"][0]["entries"][0]["pull_request"])
        self.assertIn("- Caravans.\n", notes)

    def test_release_from_the_second_parent_of_a_merge(self):
        self.git("checkout", "-q", "-b", "main")
        self.git("checkout", "-q", "develop")
        self.commit("feat(world): caravans [minor] (#3)", a3_caravans=("new", "Caravans."))
        self.git("checkout", "-q", "main")
        self.git("merge", "-q", "--no-ff", "-m", "Merge pull request #9 from owner/develop", "develop")
        out = os.path.join(self.tmp.name, "changelog.json")
        self.assertEqual(0, changelog.main(["build", "--rev", "HEAD^2", "--json", out]))
        with open(out, encoding="utf-8") as data:
            versions = json.load(data)["versions"]
        self.assertEqual("0.2.0", versions[0]["version"])
        self.assertEqual(3, versions[0]["entries"][0]["pull_request"])

    def test_build_reports_an_entry_that_is_not_valid(self):
        self.commit("feat(world): caravans [minor] (#3)", a3_caravans=("other", "Caravans."))
        with open("changelog/a4-bytes.json", "wb") as out:
            out.write('{"category": "new", "en_us": "A", "fr_fr": "Été"}'.encode("latin-1"))
        self.git("add", "changelog")
        self.git("commit", "-q", "-m", "fix: bytes [patch] (#4)")
        code, error = self.run_main("build", "--rev", "HEAD")
        self.assertEqual(1, code)
        self.assertIn('changelog/a3-caravans.json: category: "other" is not valid', error)
        self.assertIn("changelog/a4-bytes.json: encoding: use UTF-8", error)

    def test_rename_is_refused_and_removal_is_valid(self):
        self.commit("feat(world): caravans [minor] (#3)", a3_caravans=("new", "Caravans."),
                    a3_boats=("new", "Boats."))
        base = self.git("rev-parse", "HEAD")
        self.git("mv", "changelog/a3-caravans.json", "changelog/a3-wagons.json")
        self.git("commit", "-q", "-m", "fix: rename [patch]")
        code, error = self.run_main("check", "--base", base)
        self.assertEqual(1, code)
        self.assertIn("changelog/a3-wagons.json: file name: do not rename an entry file", error)
        # The revert of a pull request removes its entries: the check passes.
        self.git("reset", "-q", "--hard", base)
        self.git("rm", "-q", "changelog/a3-caravans.json")
        self.git("commit", "-q", "-m", 'Revert "feat(world): caravans [minor] (#3)" [patch]')
        self.assertEqual((0, ""), self.run_main("check", "--base", base))


if __name__ == "__main__":
    unittest.main()
