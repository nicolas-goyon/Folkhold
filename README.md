<div align="center">

# Folkhold

**A single-player Minecraft world that changes without the player.**

NPC factions trade, defend their villages and hold territory. Caravans travel between the villages.<br>
The reputation of the player decides how each village receives them.

[![Build on main](https://img.shields.io/github/actions/workflow/status/nicolas-goyon/Folkhold/build.yml?branch=main&label=build%20%C2%B7%20main&logo=github)](https://github.com/nicolas-goyon/Folkhold/actions/workflows/build.yml?query=branch%3Amain)
[![Build on develop](https://img.shields.io/github/actions/workflow/status/nicolas-goyon/Folkhold/build.yml?branch=develop&label=build%20%C2%B7%20develop&logo=github)](https://github.com/nicolas-goyon/Folkhold/actions/workflows/build.yml?query=branch%3Adevelop)
[![Release workflow](https://img.shields.io/github/actions/workflow/status/nicolas-goyon/Folkhold/release.yml?label=release&logo=github)](https://github.com/nicolas-goyon/Folkhold/actions/workflows/release.yml)
[![Latest version](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fnicolas-goyon%2FFolkhold%2Fbadges%2Fversion.json)](https://github.com/nicolas-goyon/Folkhold/releases)

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)](https://www.minecraft.net)
[![NeoForge 21.1](https://img.shields.io/badge/NeoForge-21.1-D7742F)](https://neoforged.net)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00)](https://adoptium.net)
[![Languages: English, French](https://img.shields.io/badge/languages-English%20%7C%20French-4C7EBF)](CONTRIBUTING.md#translations)
[![Code license: MIT](https://img.shields.io/badge/code-MIT-2EA44F)](LICENSE)
[![Assets license: CC BY 4.0](https://img.shields.io/badge/assets-CC%20BY%204.0-8A8A8A)](docs/ASSETS_LICENSES.md)

[Download](https://github.com/nicolas-goyon/Folkhold/releases) ·
[Design](docs/design.md) ·
[Getting started](docs/getting-started.md) ·
[Contributing](CONTRIBUTING.md) ·
[Roadmap](https://github.com/nicolas-goyon/Folkhold/milestones)

</div>

---

## Features

- **A world that lives on its own.** An abstract simulation runs the factions, the stocks and the caravans, also in the chunks that are not loaded. The world around the player changes with no visit.
- **Factions and territories.** Each village claims the territory around it. The claim grows with the level of the village, from a hamlet to a city.
- **Fortified villages.** A walled compound with towers and a village heart. Guards patrol, archers hold the towers, a captain leads the garrison. Kill the captain and destroy the heart: the village becomes ruins.
- **Simulated economy.** Each village produces and consumes goods with the jobs of its inhabitants. Its prices follow its stock and its needs, and its merchants sell its stock.
- **Caravans and traveling merchants.** Trade between two villages sends a caravan on the road, by land or by boat.
- **Reputation and quests.** Each faction and each village remembers the actions of the player. The quest board of a village gives deliveries and escorts from the state of the world.
- **Two languages.** All text in the game exists in English and in French.

## Installation

The modpack is the simple way to play. It contains the Folkhold mods and a selection of mods for performance, nature and terrain.

1. Download `folkhold-<version>.mrpack` from the [latest release](https://github.com/nicolas-goyon/Folkhold/releases).
2. Import the file in a launcher that reads Modrinth packs, for example [Prism Launcher](https://prismlauncher.org) or the [Modrinth App](https://modrinth.com/app).
3. Start the game and create a new world.

To use the mods without the modpack, put `folkhold_core` and `folkhold_world` from the release in the `mods` folder of a Minecraft 1.21.1 instance with NeoForge 21.1.259 or later and [GeckoLib](https://modrinth.com/mod/geckolib) 4.9.3 or later.

## Modules

The project has one shared library and three mods. All mods share one version.

| Module | Mod ID | Content |
| --- | --- | --- |
| `core` | `folkhold_core` | Shared library: abstract simulation, regions, scheduler, UI framework |
| `world` | `folkhold_world` | Factions, villages, economy, caravans, quests |
| `balance` | `folkhold_balance` | Planned: farm limits, villager budgets, chunk loading. The mod is empty. |
| `events` | `folkhold_events` | Planned: earthquakes, collapses. The mod is empty. |

The packwiz modpack (`modpack/`) adds existing mods for performance, nature and terrain.

## Documentation

| Document | Content |
| --- | --- |
| [Design document](docs/design.md) | Vision, rules of the simulation, what is implemented and what is planned |
| [Getting started](docs/getting-started.md) | Prerequisites, first build, daily workflow, CI and releases |
| [UI framework](docs/ui-framework.md) | The shared widgets and the look of the screens |
| [Contributing](CONTRIBUTING.md) | Branches, commit titles and versions, pull requests, translations |

## Status

Early development. The [milestones](https://github.com/nicolas-goyon/Folkhold/milestones) give the roadmap, and the [issues](https://github.com/nicolas-goyon/Folkhold/issues) give the work in progress. Each merge of `develop` into `main` makes a [release](https://github.com/nicolas-goyon/Folkhold/releases).

## License

The code is under the [MIT License](LICENSE). The assets (textures, models, animations, structures) are under [CC BY 4.0](docs/ASSETS_LICENSES.md).
