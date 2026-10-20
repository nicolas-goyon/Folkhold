# Getting started

This guide explains how to prepare a computer for Folkhold development, build the mods and test them in the game.

The goal is a clean host machine. The JDK, Gradle, packwiz and the Minecraft development files stay in a Docker container and in one Docker volume. To remove everything, delete the container image and the volume (see [Clean up](#clean-up)).

## What runs where

| Task | Where | Tool |
| --- | --- | --- |
| Edit code | Host, connected to the container | VS Code + Dev Containers (recommended) |
| Build, unit tests, datagen, translation check | Container | Gradle wrapper (`./gradlew`) |
| Dedicated server for tests, with debugger | Container (ports 25565 and 5005) | `./gradlew :dev:runServer` |
| GameTests | Container | `./gradlew :dev:runGameTestServer` |
| Modpack (add, update, export) | Container | packwiz |
| Game client | Host | Prism Launcher (it manages its own Java) |

The game client cannot run in the container, because the container has no display and no GPU. Most of the Folkhold logic runs on the server, so the server in the container plus a normal client on the host covers most tests.

## Prerequisites

Install these tools on the host. You do not need a JDK on the host.

| Tool | Why | Link |
| --- | --- | --- |
| Git | Clone and commit | https://git-scm.com/downloads |
| Docker Desktop (WSL 2 back end on Windows) | Runs the dev container | https://www.docker.com/products/docker-desktop/ |
| VS Code + extension "Dev Containers" | Editor connected to the container | https://code.visualstudio.com/ |
| Prism Launcher | Game client for tests | https://prismlauncher.org/ |

Docker Desktop settings: give Docker at least 8 GB of memory. The first Gradle build decompiles Minecraft and needs memory.

Other IDEs: IntelliJ IDEA can also open a dev container. The support depends on the edition and the version. See the JetBrains documentation. All commands in this guide also work from a terminal with `scripts/dev.ps1` (Windows) or `scripts/dev.sh` (Linux, macOS).

## First setup

1. Clone the repository:

   ```powershell
   git clone https://github.com/nicolas-goyon/Folkhold.git
   cd Folkhold
   ```

   Faster builds on Windows: clone the repository in the WSL file system (for example `\\wsl$\Ubuntu\home\<user>\Folkhold`), or use the VS Code command "Dev Containers: Clone Repository in Container Volume". Files on the Windows file system (`C:\...`) are slow in a Linux container.

2. Open the folder in VS Code.

3. Run the command **Dev Containers: Reopen in Container**. The first time, Docker builds the image (approximately 2 minutes).

4. In the terminal of VS Code (it runs in the container), build the project:

   ```bash
   ./gradlew build
   ```

   The first build downloads Gradle, NeoForge and Minecraft, then decompiles Minecraft. It can take 5 to 15 minutes. The next builds take less than one minute. The files stay in the Docker volume `folkhold-gradle-cache`.

5. Run the GameTests:

   ```bash
   ./gradlew :dev:runGameTestServer
   ```

   The server starts, runs all GameTests, then stops. The result is in the console. The GameTest server uses the folder `dev/run-gametest`, with a new world for each run. It does not change the world of the dev server, `dev/run/world`.

Without VS Code, use the scripts from a terminal on the host:

```powershell
.\scripts\dev.ps1                       # shell in the container
.\scripts\dev.ps1 ./gradlew build       # one command in the container
```

The scripts check that Docker runs. On Windows, if Docker Desktop is installed but does not run, `dev.ps1` starts it, runs the command, then stops Docker Desktop again. To keep it running, set `$env:FOLKHOLD_KEEP_DOCKER = '1'` before the command.

## Project layout

| Folder | Content |
| --- | --- |
| `core/` | Mod `folkhold_core`: shared library. Pure-Java simulation code goes in `core/src/main/java/.../core/sim`. |
| `blocks/` | Mod `folkhold_blocks`: library of the blocks, with no game logic. No dependency. |
| `world/` | Mod `folkhold_world`: factions, villages, economy, quests. Depends on `core` and `blocks`. |
| `balance/` | Mod `folkhold_balance`: farms, villagers, chunk loading. No dependency. |
| `events/` | Mod `folkhold_events`: earthquakes, collapses. Depends on `core`. |
| `dev/` | No code. Loads all mods in one game instance (client, server, GameTest server). |
| `modpack/` | packwiz modpack. |
| `scripts/` | Container shortcuts, client deployment, modpack export. |
| `tools/` | Generators (for example structure files). |
| `docs/` | Design document and guides. |
| `site/` | Site of the project on GitHub Pages (see the workflow **Pages**). |

Each mod has:

- `src/main/java/io/github/nicolasgoyon/folkhold/<module>/`: Java code.
- `src/main/resources/assets/<mod_id>/lang/en_us.json` and `fr_fr.json`: translations.
- `src/main/templates/META-INF/neoforge.mods.toml`: mod metadata. Gradle replaces the `${...}` values.
- `src/test/java/`: JUnit tests. In `world`, the tests run with the game runtime (registries and mods loaded, no server): a test that reads the config loads it with `TestConfig.loadWorldConfig()`. Pure logic, data and codecs go here. A test that needs a world is a GameTest.
- `src/gametest/java/` and `src/gametest/resources/`: GameTests and their test structures. The dev project loads them with the mod in its runs. The jar of the mod does not contain them.
- `gradle.properties`: `mod_id`, `mod_name`, `mod_description`.

The world mod also contains the optional data pack of the test world, in `world/src/main/resources/datapacks/test_world/` (see [Test world](#test-world)).

The shared versions are in the root `gradle.properties`.

## Daily commands

Run these commands in the container.

| Command | Result |
| --- | --- |
| `./gradlew build` | Compiles with the lints and Error Prone, runs the JUnit tests, the format check and the translation check, makes the jars in `<module>/build/libs/`. |
| `./gradlew test` | JUnit tests only. For `core`, the coverage report of JaCoCo is in `core/build/reports/jacoco/test/html/index.html`. |
| `./gradlew :core:test --tests '*RegionPosTest'` | One test class. |
| `./gradlew spotlessApply` | Fixes the format of the sources (imports, trailing spaces, final newline). |
| `./gradlew checkTranslations` | Checks that `en_us.json` and `fr_fr.json` have the same keys. |
| `./gradlew :dev:runGameTestServer` | Runs all GameTests in a server, in a new world (folder `dev/run-gametest`). |
| `./gradlew :dev:runServer` | Dedicated server with all mods. |

## Test in the game

### 1. Prepare a Prism Launcher instance (one time)

1. In Prism Launcher, click **Add Instance**.
2. Select **Minecraft 1.21.1** and the loader **NeoForge 21.1.259** (the value of `neo_version` in `gradle.properties`).
3. Name the instance `Folkhold-dev`.
4. Start the instance one time, then close the game. Prism creates the game folder.

You can also import the `.mrpack` file that the CI makes (see [Modpack](#modpack)).

### 2. Copy the mods into the instance

On the host, after `./gradlew build`:

```powershell
.\scripts\deploy-client.ps1 -Instance "$env:APPDATA\PrismLauncher\instances\Folkhold-dev"
```

The client and the server must have the same mod versions. Run this script after each build.

### 3. Start the dev server

First time only: read the Minecraft EULA (https://aka.ms/MinecraftEULA). If you agree, create the file `dev/run/eula.txt` with this line:

```
eula=true
```

Then start the server, from the container:

```bash
./gradlew :dev:runServer -PdebugPort=5005
```

Or from a terminal on the host:

```powershell
docker compose up server
```

### 4. Connect

Start `Folkhold-dev` in Prism Launcher. Click **Multiplayer**, then add the server `localhost`.

### 5. Debug

- **Server (container)**: start the server with `-PdebugPort=5005`. In VS Code, run the launch configuration **Attach to dev server (port 5005)**. Breakpoints in the Folkhold code now stop the server.
- **Client (Prism on the host)**: in Prism Launcher, open the instance settings, **Java**, **JVM arguments**, and add:

  ```
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006
  ```

  Start the instance. In VS Code, run **Attach to Prism client on the host (port 5006)**.

Hot reload: when the debugger is attached, VS Code can replace the body of a changed method without a restart. Changes to class structure (new fields, new methods) need a restart.

## Test world

The data pack "Folkhold test world" adds the world type "Folkhold Test". It is a flat world (plains, no trees) with one faction village next to the spawn point. The village starts in the chunk (5, 0), about 80 blocks to the east of the spawn point. It is a normal faction village: heart, garrison, bank, quest board and settlement in the simulation. A test of the villages starts with no search for a village.

The data pack is part of `folkhold_world` and is off by default. It also changes the placement of the faction villages for all the world types: one village only, in the chunk (5, 0). Turn it on only for a test world.

In the client:

1. Click **Singleplayer**, then **Create New World**.
2. In the tab **More**, click **Data Packs**. Move "Folkhold test world" to the column **Selected**, then click **Done**.
3. In the tab **World**, click **World Type** until it shows "Folkhold Test".
4. Click **Create New World**.

On the dev server, the world type and the data pack apply when the server creates a new world. Stop the server, delete the folder `dev/run/world`, then set these two lines in `dev/run/server.properties`:

```properties
level-type=folkhold_world:test
initial-enabled-packs=vanilla,mod/folkhold_world:datapacks/test_world
```

The garrison of the village depends on the level of the village. The healers come from the level 5, and the faction golems from the level 13. To see them, change the level of the village with a command. `/folkhold settlement list` gives the id of the village, then `/folkhold settlement level <id> 17` makes it a capital. The new units come when a player is near the village heart.

## Translations

The mods support English and French. Each text that the player sees needs one key in `en_us.json` and in `fr_fr.json`. `./gradlew build` fails when a key is missing in one file. See [CONTRIBUTING.md](../CONTRIBUTING.md#translations).

## Structures

The structure files of the village (`world/src/main/resources/data/folkhold_world/structure/village/*.nbt`) come from a script. Do not edit the `.nbt` files by hand. Change the script, then run it in the container:

```bash
python3 tools/structures/village.py
```

The village has:

- a center piece (`center.nbt`): palisade, 4 corner towers with a watch post (archer post), 2 gates, paths, a plaza with the village heart, 4 braziers (`folkhold_blocks:brazier`) and the quest board, lamp posts and torches. White banners hang on the outer face of the palisade. The village heart gives them the banner of the faction;
- house pieces (`house_*.nbt`). Jigsaw connectors put up to 4 houses next to the main path.

To see a village in the game: `/locate structure folkhold_world:village`, then `/tp` to the position.

## Block models from a script

The models and the block states of the quest board and of the brazier (library `blocks`) come from scripts: the board has 9 parts, and the brazier has a lit model and a model that does not burn. Change a script, then run it:

```bash
python3 tools/models/quest_board.py
python3 tools/models/brazier.py
```

## Textures from a script

The textures of the building signs (library `blocks`) and of the villager and zombie villager professions (world mod) come from scripts (Pillow is in the container). The sign script also writes the models, the block states, the loot tables, the lang keys and the tag of the signs. Change a script, then run it:

```bash
python3 tools/textures/building_signs.py
python3 tools/textures/villager_professions.py
```

## Test structures

The GameTest templates `empty`, `arena` and `arena_large` are boxes of air. The tests build their floor and their walls. The script writes a template:

```bash
python3 tools/make_test_structure.py world/src/gametest/resources/data/folkhold_world/structure/empty.nbt
python3 tools/make_test_structure.py world/src/gametest/resources/data/folkhold_world/structure/arena.nbt --size 16 4 16
python3 tools/make_test_structure.py world/src/gametest/resources/data/folkhold_world/structure/arena_large.nbt --size 32 4 32
```

The workflow **Generated files** runs all the scripts of `tools/` and fails when their output differs from the repository: commit the output of a script with the script.

To replace a generated house with a house built by hand: build it in creative mode, save it with a structure block (name `folkhold_world:village/house_<name>`), copy the file from the world folder (`generated/folkhold_world/structure/`) to the `village` folder, and add it to `worldgen/template_pool/village/houses.json`. The porch row (z = 0) must contain the jigsaw block (name `folkhold_world:house_front`, facing out).

## Modpack

The modpack is in `modpack/` (packwiz). Use packwiz in the container:

```bash
cd modpack
packwiz modrinth add sodium      # add a mod from Modrinth
packwiz update --all             # update all mods
packwiz pin <mod>                # stop updates of a mod
```

To make a `.mrpack` file with the Folkhold mods inside:

```bash
./gradlew build
scripts/export-modpack.sh build/folkhold-dev.mrpack
```

The CI makes the same file for each pull request (artifact `folkhold-modpack`).

## Continuous integration and releases

- Each push on `main` and on `develop` and each pull request runs the workflow **Build**: build, JUnit tests, translation check, GameTests. The jars, the `.mrpack` file and the coverage report of `core` are available as artifacts of the run. The workflow **Generated files** runs the tests of `scripts/version.py` and checks the output of the scripts of `tools/`.
- The workflow **Pull request** checks the title of a pull request to `develop` or in a stack on `develop` (bump marker, see [CONTRIBUTING.md](../CONTRIBUTING.md#versions-and-releases)) and gives the version after the merge. For a pull request to `main`, it checks that the source branch is `develop` and gives the version and the notes of the release.
- A change to `.devcontainer/` runs the workflow **Dev container**: it builds the image and checks the tools.
- The workflow **Pages** publishes the site of the project to GitHub Pages from `develop`: the files of `site/`, and `version.json` with the version of the latest release. The version badge of the README reads this file, with no call to the GitHub API by shields.io. The workflow runs when the files of the site change, and after each release.
- To make a release, merge `develop` into `main`: a pull request from `develop` to `main`, with "Create a merge commit". The workflow **Release** calculates the version from the bump markers of the commits, tags the commit of `develop`, creates a GitHub Release with the jars and the `.mrpack` file, then publishes the jars to GitHub Packages (Maven, `io.github.nicolasgoyon.folkhold:<mod_id>`). When the tag of the version exists, the workflow makes no release. The release uses the same packwiz commit as the CI, so two runs on the same commit give the same `.mrpack` content. After the release, the workflow starts the workflow **Pages**.

## Clean up

Remove all Folkhold development files from the host:

```powershell
docker compose down --rmi local
docker volume rm folkhold-gradle-cache
```

If you use VS Code dev containers, also remove the container and the image `vsc-folkhold-...` in Docker Desktop.

## Troubleshooting

| Problem | Cause | Fix |
| --- | --- | --- |
| `./gradlew: not found` or `bad interpreter: /bin/sh^M` | Windows line endings (CRLF) in `gradlew` | The file `.gitattributes` prevents this for new clones. For an old clone: `git rm --cached -r . && git reset --hard` (commit your changes first). |
| The build stops with `OutOfMemoryError` | Not enough memory for Docker | Give Docker Desktop at least 8 GB. |
| `port is already allocated` | A server already runs | Stop the other server (`docker compose down`). |
| The client cannot connect: "mod mismatch" | Different jars on the client and on the server | Run `scripts/deploy-client.ps1` after each build. |
| The server stops: "You need to agree to the EULA" | No `dev/run/eula.txt` | See [Start the dev server](#3-start-the-dev-server). |

## Without a container (alternative)

If you prefer to run the game client from the IDE (`./gradlew :dev:runClient`), you need a JDK 21 on the host. IntelliJ IDEA can download one (File, Project Structure, SDK, Download JDK) into `~/.jdks`. Gradle then uses `~/.gradle` on the host (several GB). To clean up, delete these two folders.
