"""Tests of scripts/pages.py. Run: python3 -m unittest discover -s scripts -p "test_*.py" """
import json
import tempfile
import unittest
from pathlib import Path

import pages

LANGUAGES = pages.load_languages(pages.SITE / "lang")
ENGLISH = LANGUAGES["en_us"]
FRENCH = LANGUAGES["fr_fr"]

LATEST = {
    "tag_name": "v0.2.0",
    "name": "Folkhold 0.2.0",
    "draft": False,
    "prerelease": False,
    "published_at": "2026-10-01T12:00:00Z",
    "created_at": "2026-10-01T11:50:00Z",
    "html_url": "https://github.com/owner/repo/releases/tag/v0.2.0",
    "body_html": '<h2>Versions</h2><table><tr><td>0.2.0</td><td>feat: caravans (<a class="issue-link" data-id="7" '
                 'href="https://github.com/owner/repo/pull/2">#2</a>)</td></tr></table>',
    "assets": [
        {"name": "folkhold_world-0.2.0.jar", "size": 1064314, "browser_download_url": "https://example.test/w.jar"},
        {"name": "folkhold-0.2.0.mrpack", "size": 1355262, "browser_download_url": "https://example.test/f.mrpack"},
        {"name": "folkhold_core-0.2.0.jar", "size": 3158, "browser_download_url": "https://example.test/c.jar"},
        {"name": "checksums.txt", "size": 200, "browser_download_url": "https://example.test/checksums.txt"},
    ],
}
PRERELEASE = {
    "tag_name": "v0.1.0-alpha.1",
    "name": "<script>alert(1)</script>",
    "draft": False,
    "prerelease": True,
    "published_at": "2026-09-15T08:00:00Z",
    "created_at": "2026-09-15T08:00:00Z",
    "html_url": "https://github.com/owner/repo/releases/tag/v0.1.0-alpha.1",
    "body_html": "<p>First release</p>",
    "assets": [],
}
DRAFT = {
    "tag_name": "v0.3.0",
    "name": "Draft",
    "draft": True,
    "prerelease": False,
    "published_at": None,
    "created_at": "2026-10-20T08:00:00Z",
    "html_url": "https://github.com/owner/repo/releases/tag/v0.3.0",
    "body_html": "<p>Not published</p>",
    "assets": [],
}
REQUIREMENTS = {"minecraft": "1.21.1", "neoforge": "21.1.259", "geckolib": "4.9.3"}


class LanguageTest(unittest.TestCase):
    def test_the_language_files_have_the_same_keys(self):
        self.assertEqual([], pages.missing_keys(LANGUAGES))

    def test_a_missing_key_is_found(self):
        languages = {"en_us": {"a": "A", "b": "B"}, "fr_fr": {"a": "A"}}
        self.assertEqual(["fr_fr.json: the key b is missing."], pages.missing_keys(languages))

    def test_the_default_language_is_first_and_at_the_root(self):
        self.assertEqual("en_us", next(iter(LANGUAGES)))
        self.assertEqual("", pages.page_folder("en_us", ENGLISH))
        self.assertEqual("fr/", pages.page_folder("fr_fr", FRENCH))

    def test_numbered_texts_are_in_the_order_of_the_numbers(self):
        texts = {"step10": "J", "step2": "B", "step1": "A", "other": "X"}
        self.assertEqual(["A", "B", "J"], pages.numbered(texts, "step"))


class FormatTest(unittest.TestCase):
    def test_dates(self):
        self.assertEqual("October 24, 2026", pages.format_date("2026-10-24T14:33:03Z", ENGLISH))
        self.assertEqual("October 1, 2026", pages.format_date("2026-10-01T00:00:00Z", ENGLISH))
        self.assertEqual("24 octobre 2026", pages.format_date("2026-10-24T14:33:03Z", FRENCH))
        self.assertEqual("1er octobre 2026", pages.format_date("2026-10-01T00:00:00Z", FRENCH))

    def test_sizes(self):
        self.assertEqual("0&nbsp;KB", pages.format_size(0, ENGLISH))
        self.assertEqual("1&nbsp;KB", pages.format_size(100, ENGLISH))
        self.assertEqual("3&nbsp;KB", pages.format_size(3158, ENGLISH))
        self.assertEqual("1.3&nbsp;MB", pages.format_size(1355262, ENGLISH))
        self.assertEqual("1,3&nbsp;Mo", pages.format_size(1355262, FRENCH))
        self.assertEqual("244&nbsp;Ko", pages.format_size(250317, FRENCH))

    def test_properties(self):
        text = "# Comment\n! Comment\n\nminecraft_version=1.21.1\n neo_version = 21.1.259 \nno separator\n"
        self.assertEqual({"minecraft_version": "1.21.1", "neo_version": "21.1.259"}, pages.read_properties(text))

    def test_next_page_of_the_api(self):
        link = ('<https://api.github.com/repositories/1/releases?page=2>; rel="next", '
                '<https://api.github.com/repositories/1/releases?page=5>; rel="last"')
        self.assertEqual("https://api.github.com/repositories/1/releases?page=2", pages.next_page(link))
        self.assertIsNone(pages.next_page('<https://api.github.com/repositories/1/releases?page=1>; rel="prev"'))
        self.assertIsNone(pages.next_page(""))

    def test_version_badge(self):
        self.assertEqual('{"schemaVersion":1,"label":"version","message":"v0.2.0","color":"blue"}\n',
                         pages.version_badge(LATEST))
        self.assertEqual("none", json.loads(pages.version_badge(None))["message"])


class NotesTest(unittest.TestCase):
    def test_the_attributes_of_github_go(self):
        notes = pages.clean_notes('<a class="issue-link" data-id="7" href="https://github.com/o/r/pull/2">#2</a>')
        self.assertEqual('<a href="https://github.com/o/r/pull/2">#2</a>', notes)

    def test_the_headings_go_two_levels_lower(self):
        self.assertEqual("<h4>Versions</h4><h6>Details</h6>", pages.clean_notes("<h2>Versions</h2><h5>Details</h5>"))

    def test_a_table_goes_into_a_box(self):
        self.assertEqual('<div class="table"><table><tr><td>1</td></tr></table></div>',
                         pages.clean_notes("<table><tr><td>1</td></tr></table>"))

    def test_links(self):
        self.assertEqual('<a href="https://github.com/o/r/pull/2">#2</a>',
                         pages.clean_notes('<a href="/o/r/pull/2">#2</a>'))
        self.assertEqual("<a>x</a>", pages.clean_notes('<a href="javascript:alert(1)">x</a>'))
        self.assertEqual("<a>x</a>", pages.clean_notes('<a href="java&#9;script:alert(1)">x</a>'))
        self.assertEqual('<a href="mailto:a@b.c">x</a>', pages.clean_notes('<a href="mailto:a@b.c">x</a>'))

    def test_unknown_tags_go_and_their_text_stays(self):
        self.assertEqual("<p>a b</p>", pages.clean_notes("<p>a <custom-tag>b</custom-tag></p>"))

    def test_scripts_go_with_their_content(self):
        self.assertEqual("<p>a</p><p>b</p>", pages.clean_notes("<p>a</p><script>alert(1)</script><style/><p>b</p>"))

    def test_the_text_is_escaped(self):
        self.assertEqual("<p>1 &lt; 2 &amp; &lt;b&gt;</p>", pages.clean_notes("<p>1 &lt; 2 &amp; &lt;b&gt;</p>"))

    def test_details_and_void_tags(self):
        self.assertEqual("<details open><summary>2 commits</summary><ul><li>a<br></li></ul></details>",
                         pages.clean_notes("<details open><summary>2 commits</summary><ul><li>a<br/></li></ul>"
                                           "</details>"))

    def test_no_notes(self):
        self.assertEqual("", pages.clean_notes(None))


class BuildTest(unittest.TestCase):
    def build(self, releases, latest, requirements):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        out = Path(folder.name)
        pages.build(out, "owner/repo", releases, latest, requirements)
        return out

    def test_site(self):
        out = self.build([PRERELEASE, DRAFT, LATEST], LATEST, REQUIREMENTS)
        self.assertTrue((out / "style.css").is_file())
        self.assertEqual("v0.2.0", json.loads((out / "version.json").read_text(encoding="utf-8"))["message"])
        english = (out / "index.html").read_text(encoding="utf-8")
        french = (out / "fr" / "index.html").read_text(encoding="utf-8")
        for page in (english, french):
            # The template and the texts have no field with no value.
            self.assertNotIn("$", page)
            self.assertNotIn("{", page)
            # The newest release is first and open. The draft is not on the page.
            self.assertLess(page.index('id="v0.2.0"'), page.index('id="v0.1.0-alpha.1"'))
            self.assertIn('<article class="release" id="v0.2.0"><details open>', page)
            self.assertNotIn("v0.3.0", page)
            # The name of a release is text, not HTML.
            self.assertIn("&lt;script&gt;alert(1)&lt;/script&gt;", page)
            self.assertNotIn("<script>", page)
            self.assertIn('href="https://example.test/f.mrpack"', page)
            self.assertIn("checksums.txt", page)
            self.assertIn("21.1.259", page)
        self.assertIn('<html lang="en">', english)
        self.assertIn('href="style.css"', english)
        self.assertIn('<a href="fr/" lang="fr" hreflang="fr">Français</a>', english)
        self.assertIn("Download 0.2.0", english)
        self.assertIn("October 1, 2026", english)
        self.assertIn('<span class="badge badge-pre">Pre-release</span>', english)
        self.assertNotIn('class="note"', english)
        self.assertIn('<html lang="fr">', french)
        self.assertIn('href="../style.css"', french)
        self.assertIn('<a href="../" lang="en" hreflang="en">English</a>', french)
        self.assertIn("1er octobre 2026", french)
        self.assertIn("1,3&nbsp;Mo", french)
        self.assertIn('<p class="note">Les notes de version sont en anglais.</p>', french)

    def test_site_with_no_release(self):
        out = self.build([], None, None)
        self.assertEqual("none", json.loads((out / "version.json").read_text(encoding="utf-8"))["message"])
        english = (out / "index.html").read_text(encoding="utf-8")
        self.assertEqual(2, english.count("There is no release yet."))
        self.assertIn('<a class="button" href="#download">Download</a>', english)
        self.assertNotIn("{", english)


if __name__ == "__main__":
    unittest.main()
