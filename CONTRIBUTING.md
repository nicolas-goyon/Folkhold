# Contributing

## Language

- Write all code, comments, documentation, commit messages, issues and pull requests in English.
- Use the style of ASD-STE100 (Simplified Technical English): short sentences, active voice, one instruction for each sentence, no semicolons.
- Documentation and comments describe the current state of the code and of the project. Do not refer to earlier versions, to the origin of a document or to the reason for a change. Put this information in commit messages and pull requests.
- The mods support English (`en_us`) and French (`fr_fr`). See [Translations](#translations).

## Workflow

1. Open an issue, or select an existing issue.
2. Create a branch from `develop`. Use the format `<type>/<issue>-<short-name>`:
   - `feat/12-faction-codec`
   - `fix/31-region-negative-coords`
   - `docs/1-design-document`
   - Types: `feat`, `fix`, `docs`, `build`, `ci`, `refactor`, `test`, `chore`.
3. Make small commits. Use [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat(world): add faction codec`
   - `fix(core): use floor division for negative region coordinates`
   - Scopes: `core`, `world`, `balance`, `events`, `modpack`, `dev`, `ci`, `docs`.
4. Open a pull request to `develop`. Write `Closes #<issue>` in the description. The title follows Conventional
   Commits and ends with a bump marker (see [Versions and releases](#versions-and-releases)):
   - `fix(world): close the gates at night [patch]`
5. The CI must pass before the merge. The check **Title and version** refuses a title with no bump marker, and its
   summary gives the version after the merge.
6. Use "Squash and merge": the title of the pull request becomes the title of the commit on `develop`. Delete the
   branch after the merge.

Do not push directly to `develop` or to `main`.

## Labels

- Type: `type: feature`, `type: build`, `type: ci`, `type: docs`, `type: design`, `type: chore`, `bug`.
- Area: `mod: core`, `mod: world`, `mod: balance`, `mod: events`, `modpack`, `i18n`, `dev-env`.
- Status: `needs discussion`.

## Translations

- Do not hard-code text that the player sees. Use `Component.translatable("<key>")`.
- Each mod has `assets/<mod_id>/lang/en_us.json` and `assets/<mod_id>/lang/fr_fr.json`.
- When you add a key, add it to the two files in the same commit.
- `./gradlew checkTranslations` fails when a key is missing in one language. The CI runs this check.

## Code checks

`./gradlew build` runs these checks. The CI runs them on each pull request.

- `javac` lints: all the lints are errors (`-Xlint:all -Werror`), except `this-escape`.
- [Error Prone](https://errorprone.info): the bug patterns of the `core` and `world` mods are errors. `ReferenceEquality` is off, because the game compares the registry objects and the entities by identity. To silence one finding, use `@SuppressWarnings("<pattern>")` on the smallest element with a comment that gives the reason.
- [Spotless](https://github.com/diffplug/spotless): import order (one group in ASCII order, static imports first), unused imports, trailing spaces, tabs and the final newline. `./gradlew spotlessApply` fixes the files.

## Versions and releases

- `develop` gets the pull requests. `main` gets only the merges of `develop`, and each merge makes a release.
- Each commit of `develop` has a bump marker at the end of its title:
  - `[patch]`: a fix or a small change. 1.2.3 becomes 1.2.4.
  - `[minor]`: a new feature. 1.2.3 becomes 1.3.0.
  - `[major]`: a change that breaks the worlds or the compatibility. 1.2.3 becomes 2.0.0.
- The version starts from the last release tag (`vX.Y.Z`). The commits after the tag apply their bump in order, the
  oldest first. Example from 1.2.3: `[patch]`, `[minor]`, `[patch]` give 1.2.4, 1.3.0, 1.3.1. The release gets the
  version of the last commit, and the release notes give the version of each commit.
- A commit with no marker keeps the version. A commit of Dependabot counts as `[patch]`.
- `python3 scripts/version.py version --rev <commit>` gives the version of a commit.
- To make a release, open a pull request from `develop` to `main`, then use "Create a merge commit". The check
  **Release preview** gives the version and the release notes. The workflow **Release** tags the commit of
  `develop`, creates a GitHub Release with the jars and the modpack (`.mrpack`), then publishes the jars to GitHub
  Packages.
- All mods share one version. `mod_version` in `gradle.properties` is the version of a local build.
