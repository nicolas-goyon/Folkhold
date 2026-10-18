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
   - Scopes: `core`, `blocks`, `world`, `balance`, `events`, `modpack`, `dev`, `ci`, `docs`.
4. Open a pull request to `develop`, or to the branch below it in a stack (see
   [Stacked pull requests](#stacked-pull-requests)). Write `Closes #<issue>` in the description. The title follows
   Conventional Commits and ends with a bump marker (see [Versions and releases](#versions-and-releases)):
   - `fix(world): close the gates at night [patch]`
5. The CI must pass before the merge. The check **Title and version** refuses a title with no bump marker, and its
   summary gives the version after the merge.
6. Use "Squash and merge": the title of the pull request becomes the title of the commit on `develop`. Delete the
   branch after the merge.

Do not push directly to `develop` or to `main`.

### Stacked pull requests

Use a [stack of pull requests](https://docs.github.com/en/pull-requests/reference/stacked-pull-requests) when a
change needs the code of a pull request that is not merged. For an independent change, open a pull request to
`develop`.

- Each pull request of the stack is for one issue, and follows the workflow above.
- The bottom pull request targets `develop`. Each other pull request targets the branch of the pull request below it.
  Create each branch from the branch below it.
- Make the stack on GitHub, from the bottom pull request to the top pull request: on the website, with the GitHub CLI
  extension [`gh stack`](https://github.com/github/gh-stack), or with the REST API
  (`POST /repos/{owner}/{repo}/stacks`). A pull request to a feature branch outside a stack merges into this branch,
  not into `develop`. The check **Title and version** shows a warning for such a pull request.
- The rules and the checks of `develop` apply to each pull request of the stack. The summary of the check
  **Title and version** gives the version after the merge of the stack up to the pull request.
- The branches of a stack have a linear history. Do not merge `develop` into them. When `develop` or a branch of the
  stack changes, rebase the stack ("Rebase stack" on GitHub, or `gh stack rebase`), then push with
  `--force-with-lease`.
- To merge, use "Squash and merge" on a pull request of the stack. This pull request and all the pull requests below
  it go into `develop` in one operation, with one commit for each pull request.

## Labels

- Type: `type: feature`, `type: build`, `type: ci`, `type: docs`, `type: design`, `type: chore`, `bug`.
- Area: `mod: core`, `mod: blocks`, `mod: world`, `mod: balance`, `mod: events`, `modpack`, `i18n`, `dev-env`.
- Status: `needs discussion`.

## Translations

- Do not hard-code text that the player sees. Use `Component.translatable("<key>")`.
- Each mod has `assets/<mod_id>/lang/en_us.json` and `assets/<mod_id>/lang/fr_fr.json`.
- When you add a key, add it to the two files in the same commit.
- `./gradlew checkTranslations` fails when a key is missing in one language. The CI runs this check.

## Code checks

`./gradlew build` runs these checks. The CI runs them on each pull request.

- `javac` lints: all the lints are errors (`-Xlint:all -Werror`), except `this-escape`.
- [Error Prone](https://errorprone.info): the bug patterns of the mods are errors. `ReferenceEquality` is off, because the game compares the registry objects and the entities by identity. To silence one finding, use `@SuppressWarnings("<pattern>")` on the smallest element with a comment that gives the reason.
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
