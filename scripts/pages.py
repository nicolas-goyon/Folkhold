#!/usr/bin/env python3
"""Builds the site of the project for GitHub Pages.

The site shows the downloads of the latest release and the notes of all the releases. It has one page for each
language file of site/lang/. The script reads the releases from the GitHub API. It reads the versions of Minecraft,
NeoForge and GeckoLib from gradle.properties at the tag of the latest release. It writes these files to the output
folder:

- index.html: the page in the default language (en_us).
- <code>/index.html: the page in each other language, with the code of the language. Example: fr/index.html.
- The files of site/static/, with no change.
- version.json: the tag of the latest release, in the format of the endpoint badges of shields.io. The version badge
  of the README reads this file.

The texts of site/lang/ can contain HTML. This script gives the value of a field in braces, for example {version}. In
the template site/page.html, the script replaces $page_title with the text of the key page.title. The template can use
only the texts with no field.

The API accepts more requests with a token in GH_TOKEN or GITHUB_TOKEN. The tags of the releases must be in the local
repository (git fetch --tags).

Usage:
    scripts/pages.py --repo OWNER/NAME [--out DIR] [--summary FILE]
"""
import argparse
import html
import json
import os
import re
import shutil
import string
import subprocess
import sys
import urllib.error
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
DEFAULT_LANGUAGE = "en_us"
API = "https://api.github.com"
# The API gives the notes of the releases as HTML, in the field body_html.
ACCEPT = "application/vnd.github.html+json"
# The values for the downloads, and their keys in gradle.properties.
REQUIREMENTS = {"minecraft": "minecraft_version", "neoforge": "neo_version", "geckolib": "geckolib_version"}


def load_languages(folder):
    """The texts of each language file of the folder, by language code. The default language is first."""
    languages = {}
    for path in sorted(folder.glob("*.json")):
        with open(path, encoding="utf-8") as file:
            languages[path.stem] = json.load(file)
    if DEFAULT_LANGUAGE not in languages:
        raise SystemExit(f"The folder {folder} has no file {DEFAULT_LANGUAGE}.json.")
    return dict(sorted(languages.items(), key=lambda item: item[0] != DEFAULT_LANGUAGE))


def missing_keys(languages):
    """The keys that are missing in a language file. Each key of a language file must be in all the language files."""
    keys = set().union(*(texts.keys() for texts in languages.values()))
    return [f"{code}.json: the key {key} is missing."
            for code, texts in languages.items() for key in sorted(keys - texts.keys())]


def page_folder(code, texts):
    """The folder of the page of a language, from the root of the site. The default language has the root ("")."""
    return "" if code == DEFAULT_LANGUAGE else texts["language.code"] + "/"


def numbered(texts, prefix):
    """The texts of the keys prefix1, prefix2 and so on, in the order of the numbers."""
    keys = [key for key in texts if re.fullmatch(re.escape(prefix) + r"\d+", key)]
    return [texts[key] for key in sorted(keys, key=lambda key: int(key[len(prefix):]))]


def format_date(timestamp, texts):
    """The date of a timestamp of the API (example: 2026-10-24T14:33:03Z) in a language."""
    year, month, day = (int(part) for part in timestamp[:10].split("-"))
    pattern = texts["date.format.first"] if day == 1 else texts["date.format"]
    return pattern.format(day=day, month=texts[f"date.month.{month}"], year=year)


def format_size(size, texts):
    """The size of a file in kilobytes (1 KB is 1024 bytes). From 1 MB, the size is in megabytes with one decimal."""
    if size < 1024 * 1024:
        kilobytes = max(1, round(size / 1024)) if size else 0
        return texts["size.kb"].format(value=kilobytes)
    megabytes = f"{size / (1024 * 1024):.1f}".replace(".", texts["number.decimal"])
    return texts["size.mb"].format(value=megabytes)


def read_properties(text):
    """The keys and the values of a .properties file. A line that starts with # or ! is a comment."""
    properties = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line[0] in "#!":
            continue
        key, separator, value = line.partition("=")
        if separator:
            properties[key.strip()] = value.strip()
    return properties


def requirements_of(tag):
    """The versions of Minecraft, NeoForge and GeckoLib in gradle.properties at a tag."""
    try:
        text = subprocess.run(("git", "show", f"refs/tags/{tag}:gradle.properties"), cwd=ROOT, check=True,
                              capture_output=True, text=True).stdout
    except subprocess.CalledProcessError as error:
        raise SystemExit(f"Cannot read gradle.properties at the tag {tag}: {error.stderr.strip()}\n"
                         "To add the tags of the releases, run: git fetch --tags") from error
    properties = read_properties(text)
    missing = [key for key in REQUIREMENTS.values() if key not in properties]
    if missing:
        raise SystemExit(f"gradle.properties at the tag {tag} has no {', '.join(missing)}.")
    return {name: properties[key] for name, key in REQUIREMENTS.items()}


def safe_url(url):
    """The URL of a link of the notes, or None for a scheme other than http, https and mailto.

    A URL that starts with / is a page of github.com.
    """
    url = re.sub(r"[\x00-\x20\x7f]", "", url)
    if url.startswith("//"):
        return "https:" + url
    if url.startswith("/"):
        return "https://github.com" + url
    scheme = re.match(r"([A-Za-z][A-Za-z0-9+.-]*):", url)
    if scheme and scheme.group(1).lower() not in ("http", "https", "mailto"):
        return None
    return url


def heading(tag):
    """The tag of a heading two levels lower (h2 becomes h4), below the title of the release (h3). Other tags stay."""
    found = re.fullmatch(r"h([1-6])", tag)
    return f"h{min(6, int(found.group(1)) + 2)}" if found else tag


class NotesCleaner(HTMLParser):
    """Keeps a safe part of the HTML of the release notes.

    - A tag that is not in TAGS goes, and its text stays. The content of script, style and template goes.
    - An attribute that is not in ATTRIBUTES goes, for example the classes and the data attributes of GitHub.
    - The headings go two levels lower (see heading).
    - A table goes into a box that scrolls on a narrow screen.
    """
    TAGS = {"a", "b", "blockquote", "br", "code", "del", "details", "div", "em", "h1", "h2", "h3", "h4", "h5", "h6",
            "hr", "i", "img", "kbd", "li", "ol", "p", "pre", "s", "span", "strong", "sub", "summary", "sup", "table",
            "tbody", "td", "tfoot", "th", "thead", "tr", "tt", "ul"}
    VOID = {"br", "hr", "img"}
    HIDDEN = {"script", "style", "template"}
    ATTRIBUTES = {"a": {"href", "title"}, "img": {"src", "alt", "width", "height"}, "ol": {"start"},
                  "details": {"open"}, "td": {"align", "colspan", "rowspan"}, "th": {"align", "colspan", "rowspan"}}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in self.HIDDEN:
            self.hidden += 1
            return
        if self.hidden or tag not in self.TAGS:
            return
        kept = []
        for name, value in attrs:
            if name not in self.ATTRIBUTES.get(tag, ()):
                continue
            if value is None:
                kept.append(f" {name}")
                continue
            if name in ("href", "src"):
                value = safe_url(value)
                if value is None:
                    continue
            kept.append(f' {name}="{html.escape(value)}"')
        if tag == "table":
            self.parts.append('<div class="table">')
        self.parts.append(f"<{heading(tag)}{''.join(kept)}>")

    def handle_startendtag(self, tag, attrs):
        if tag in self.HIDDEN:
            return
        self.handle_starttag(tag, attrs)
        if tag not in self.VOID:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if tag in self.HIDDEN:
            self.hidden = max(0, self.hidden - 1)
            return
        if self.hidden or tag not in self.TAGS or tag in self.VOID:
            return
        self.parts.append(f"</{heading(tag)}>")
        if tag == "table":
            self.parts.append("</div>")

    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(html.escape(data, quote=False))


def clean_notes(text):
    """The HTML of the release notes for the site (see NotesCleaner)."""
    cleaner = NotesCleaner()
    cleaner.feed(text or "")
    cleaner.close()
    return "".join(cleaner.parts)


def next_page(link):
    """The URL of the next page in the Link header of a response of the API, or None."""
    for part in link.split(","):
        found = re.match(r'\s*<([^>]*)>\s*;\s*rel="next"', part)
        if found:
            return found.group(1)
    return None


def api_get(url, token):
    """The JSON of a response of the API, and the URL of the next page (or None)."""
    request = urllib.request.Request(url, headers={
        "Accept": ACCEPT, "User-Agent": "folkhold-pages", "X-GitHub-Api-Version": "2022-11-28"})
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response), next_page(response.headers.get("Link") or "")


def fetch_releases(repo, token):
    """All the releases of the repository, with the notes as HTML."""
    releases = []
    url = f"{API}/repos/{repo}/releases?per_page=100"
    while url:
        page, url = api_get(url, token)
        releases.extend(page)
    return releases


def fetch_latest(repo, token):
    """The latest release (the release with the label "Latest" on GitHub), or None when the repository has none."""
    try:
        return api_get(f"{API}/repos/{repo}/releases/latest", token)[0]
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def published(releases):
    """The releases that are not drafts, the newest first."""
    return sorted((release for release in releases if not release.get("draft")),
                  key=lambda release: release.get("published_at") or "", reverse=True)


def escape(value):
    return html.escape(str(value))


def file_list(assets, texts):
    items = "".join(f'<li><a href="{escape(asset["browser_download_url"])}">{escape(asset["name"])}</a>'
                    f' <span class="size">{format_size(asset["size"], texts)}</span></li>' for asset in assets)
    return f'<ul class="file-list">{items}</ul>'


def render_downloads(latest, requirements, texts, repo):
    """The downloads of the latest release, with the install steps."""
    if latest is None:
        return f'<p>{texts["download.none"]}</p>'
    values = {name: escape(value) for name, value in requirements.items()}
    version = escape(latest["tag_name"].removeprefix("v"))
    date = format_date(latest["published_at"], texts)
    assets = sorted(latest.get("assets", []), key=lambda asset: asset["name"])
    modpacks = [asset for asset in assets if asset["name"].endswith(".mrpack")]
    jars = [asset for asset in assets if asset["name"].endswith(".jar")]
    others = [asset for asset in assets if asset not in modpacks and asset not in jars]
    parts = [f'<p class="latest">{texts["download.latest"].format(version=version, date=date)}</p>',
             f'<p class="requirements">{texts["download.requirements"].format(**values)}</p>',
             '<div class="files">']
    if modpacks:
        buttons = " ".join(f'<a class="button" href="{escape(asset["browser_download_url"])}">'
                           f'<span class="file-name">{escape(asset["name"])}</span>'
                           f' <span class="size">{format_size(asset["size"], texts)}</span></a>'
                           for asset in modpacks)
        steps = "".join(f"<li>{step}</li>" for step in numbered(texts, "download.modpack.step"))
        parts.append(f'<div class="card"><h3>{texts["download.modpack.title"]}</h3>'
                     f'<p>{texts["download.modpack.text"]}</p><p>{buttons}</p><ol>{steps}</ol></div>')
    if jars:
        parts.append(f'<div class="card"><h3>{texts["download.mods.title"]}</h3>{file_list(jars, texts)}'
                     f'<p>{texts["download.mods.text"].format(**values)}</p></div>')
    if others:
        parts.append(f'<div class="card"><h3>{texts["download.other.title"]}</h3>{file_list(others, texts)}</div>')
    parts.append("</div>")
    readme = escape(f"https://github.com/{repo}#installation")
    parts.append(f'<p class="more">{texts["download.more"].format(readme=readme)}</p>')
    return "\n".join(parts)


def render_releases(releases, latest, texts):
    """The notes of all the releases, the newest first. The newest release is open."""
    releases = published(releases)
    if not releases:
        return f'<p>{texts["releases.none"]}</p>'
    latest_tag = latest["tag_name"] if latest else None
    parts = [f'<p class="note">{texts["releases.language"]}</p>'] if texts["releases.language"] else []
    for index, release in enumerate(releases):
        tag = release["tag_name"]
        badges = ""
        if tag == latest_tag:
            badges += f'<span class="badge badge-latest">{texts["releases.latest"]}</span>'
        if release.get("prerelease"):
            badges += f'<span class="badge badge-pre">{texts["releases.prerelease"]}</span>'
        date = release.get("published_at") or release["created_at"]
        parts.append(
            f'<article class="release" id="{escape(tag)}"><details{" open" if index == 0 else ""}>'
            f'<summary><h3>{escape(release.get("name") or tag)}</h3>'
            f'<time datetime="{escape(date[:10])}">{format_date(date, texts)}</time>{badges}</summary>'
            f'<div class="notes">{clean_notes(release.get("body_html"))}</div>'
            f'<p class="release-link"><a href="{escape(release["html_url"])}">{texts["releases.open"]}</a></p>'
            "</details></article>")
    return "\n".join(parts)


def render_page(template, code, languages, repo, releases, latest, requirements):
    """The page of a language."""
    texts = languages[code]
    prefix = "../" * page_folder(code, texts).count("/")
    links = []
    alternates = []
    for other, other_texts in languages.items():
        href = (prefix + page_folder(other, other_texts)) or "./"
        language = escape(other_texts["language.code"])
        current = ' aria-current="page"' if other == code else ""
        links.append(f'<a href="{href}" lang="{language}" hreflang="{language}"{current}>'
                     f'{other_texts["language.name"]}</a>')
        alternates.append(f'<link rel="alternate" hreflang="{language}" href="{href}">')
    button = (texts["nav.download.version"].format(version=escape(latest["tag_name"].removeprefix("v")))
              if latest else texts["nav.download"])
    github = f"https://github.com/{repo}"
    licenses = {"license": escape(f"{github}/blob/HEAD/LICENSE"),
                "assets_license": escape(f"{github}/blob/HEAD/docs/ASSETS_LICENSES.md")}
    values = {
        "part_alternates": "\n".join(alternates),
        "part_languages": "".join(links),
        "part_download_button": f'<a class="button" href="#download">{button}</a>',
        "part_downloads": render_downloads(latest, requirements, texts, repo),
        "part_releases": render_releases(releases, latest, texts),
        "part_license": texts["footer.license"].format(**licenses),
        "url_stylesheet": prefix + "style.css",
        "url_repository": escape(github),
        "url_issues": escape(f"{github}/issues"),
        "url_contributing": escape(f"{github}/blob/HEAD/CONTRIBUTING.md"),
    }
    words = {key.replace(".", "_"): value for key, value in texts.items()}
    clash = sorted(words.keys() & values.keys())
    if clash:
        raise SystemExit(f"The keys {', '.join(clash)} of {code}.json have the names of values of the script.")
    return string.Template(template).substitute(words | values)


def version_badge(latest):
    """The content of version.json for the endpoint badge of shields.io: the tag of the latest release."""
    badge = {"schemaVersion": 1, "label": "version", "message": latest["tag_name"] if latest else "none",
             "color": "blue" if latest else "lightgrey"}
    return json.dumps(badge, separators=(",", ":")) + "\n"


def build(out, repo, releases, latest, requirements):
    """Writes the site to the folder out. requirements is None when there is no latest release."""
    languages = load_languages(SITE / "lang")
    problems = missing_keys(languages)
    if problems:
        raise SystemExit("\n".join(problems))
    template = (SITE / "page.html").read_text(encoding="utf-8")
    out.mkdir(parents=True, exist_ok=True)
    if (SITE / "static").is_dir():
        shutil.copytree(SITE / "static", out, dirs_exist_ok=True)
    for code, texts in languages.items():
        page = out / page_folder(code, texts) / "index.html"
        page.parent.mkdir(parents=True, exist_ok=True)
        page.write_text(render_page(template, code, languages, repo, releases, latest, requirements),
                        encoding="utf-8")
    (out / "version.json").write_text(version_badge(latest), encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--repo", required=True, help="OWNER/NAME of the repository on GitHub")
    parser.add_argument("--out", default=str(ROOT / "build" / "site"), help="the output folder (default: build/site)")
    parser.add_argument("--summary", help="add a summary in Markdown to the end of this file")
    args = parser.parse_args(argv)
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    releases = fetch_releases(args.repo, token)
    latest = fetch_latest(args.repo, token)
    requirements = requirements_of(latest["tag_name"]) if latest else None
    build(Path(args.out), args.repo, releases, latest, requirements)
    count = len(published(releases))
    tag = latest["tag_name"] if latest else "none"
    summary = (f"The site shows the notes of {count} release{'' if count == 1 else 's'}. "
               f"The version badge shows `{tag}`.")
    print(summary)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as out:
            out.write(summary + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
