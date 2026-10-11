# Folkhold

A single-player Minecraft experience (NeoForge 1.21.1) where the world changes without the player. NPC factions trade, fight and take territory. Nature grows and ages. Natural events occur.

The project has one shared library and three mods:

| Module | Mod ID | Content |
| --- | --- | --- |
| `core` | `folkhold_core` | Shared library: abstract simulation, regions, scheduler |
| `world` | `folkhold_world` | Factions, villages, economy, caravans, quests |
| `balance` | `folkhold_balance` | Farm limits, villager budgets, chunk loading |
| `events` | `folkhold_events` | Earthquakes, collapses |

A packwiz modpack (`modpack/`) adds existing mods for performance, nature and weather.

Supported languages: English, French.

## Documentation

- [Design document](docs/design.md)
- [Getting started](docs/getting-started.md): prerequisites, first build, daily workflow.
- [Contributing](CONTRIBUTING.md): branches, commits, pull requests, translations.

## Status

Early development. See the [issues](https://github.com/nicolas-goyon/Folkhold/issues) and the roadmap review in #6.

## License

All Rights Reserved, until the owner selects a license (see #7).
