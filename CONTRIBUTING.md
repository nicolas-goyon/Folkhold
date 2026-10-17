# Contributing

## Language

- Write all code, comments, documentation, commit messages, issues and pull requests in English.
- Use the style of ASD-STE100 (Simplified Technical English): short sentences, active voice, one instruction for each sentence, no semicolons.
- Documentation and comments describe the current state of the code and of the project. Do not refer to earlier versions, to the origin of a document or to the reason for a change. Put this information in commit messages and pull requests.
- The mods support English (`en_us`) and French (`fr_fr`). See [Translations](#translations).

## Workflow

1. Open an issue, or select an existing issue.
2. Create a branch from `main`. Use the format `<type>/<issue>-<short-name>`:
   - `feat/12-faction-codec`
   - `fix/31-region-negative-coords`
   - `docs/1-design-document`
   - Types: `feat`, `fix`, `docs`, `build`, `ci`, `refactor`, `test`, `chore`.
3. Make small commits. Use [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat(world): add faction codec`
   - `fix(core): use floor division for negative region coordinates`
   - Scopes: `core`, `world`, `balance`, `events`, `modpack`, `dev`, `ci`, `docs`.
4. Open a pull request to `main`. Write `Closes #<issue>` in the description.
5. The CI must pass before the merge.
6. Use "Squash and merge". Delete the branch after the merge.

Do not push directly to `main`.

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

- The version is in `gradle.properties` (`mod_version`). All mods share one version.
- To make a release, push a tag `vX.Y.Z` on `main`. The `release` workflow creates a GitHub Release with the jars and the modpack, then publishes the jars to GitHub Packages. A tag that is not on `main` makes no release.
