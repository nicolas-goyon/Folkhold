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

   The server starts, runs all GameTests, then stops. The result is in the console.

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
| `world/` | Mod `folkhold_world`: factions, villages, economy, quests. Depends on `core`. |
| `balance/` | Mod `folkhold_balance`: farms, villagers, chunk loading. No dependency. |
| `events/` | Mod `folkhold_events`: earthquakes, collapses. Depends on `core`. |
| `dev/` | No code. Loads all mods in one game instance (client, server, GameTest server). |
| `modpack/` | packwiz modpack. |
| `scripts/` | Container shortcuts, client deployment, modpack export. |
| `tools/` | Generators (for example structure files). |
| `docs/` | Design document and guides. |

Each mod has:

- `src/main/java/io/github/nicolasgoyon/folkhold/<module>/`: Java code.
- `src/main/resources/assets/<mod_id>/lang/en_us.json` and `fr_fr.json`: translations.
- `src/main/templates/META-INF/neoforge.mods.toml`: mod metadata. Gradle replaces the `${...}` values.
- `src/test/java/`: JUnit tests. In `world`, the tests run with the game runtime (registries and mods loaded, no server): a test that reads the config loads it with `TestConfig.loadWorldConfig()`. Pure logic, data and codecs go here. A test that needs a world is a GameTest.
- `gradle.properties`: `mod_id`, `mod_name`, `mod_description`.

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
| `./gradlew :dev:runGameTestServer` | Runs all GameTests in a server. |
| `./gradlew :world:runData` | Data generation for one mod. Output: `world/src/generated/resources/`. |
| `./gradlew :dev:runServer` | Dedicated server with all mods. |

## Test in the game

### 1. Prepare a Prism Launcher instance (one time)

1. In Prism Launcher, click **Add Instance**.
2. Select **Minecraft 1.21.1** and the loader **NeoForge 21.1.259** (the value of `neo_version` in `gradle.properties`).
3. Name the instance `Folkhold-dev`.
4. Start the instance one time, then close the game. Prism creates the game folder.

When the modpack exists (`modpack/`), you can also import the `.mrpack` file that the CI makes (see [Modpack](#modpack)).

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

## Translations

The mods support English and French. Each text that the player sees needs one key in `en_us.json` and in `fr_fr.json`. `./gradlew build` fails when a key is missing in one file. See [CONTRIBUTING.md](../CONTRIBUTING.md#translations).

## Structures

The structure files of the village (`world/src/main/resources/data/folkhold_world/structure/village/*.nbt`) come from a script. Do not edit the `.nbt` files by hand. Change the script, then run it in the container:

```bash
python3 tools/structures/village.py
```

The village has:

- a center piece (`center.nbt`): palisade, 4 corner towers with a watch post (archer post), 2 gates, paths, a plaza with the village heart, 4 braziers and the quest board, lamp posts and torches. White banners hang on the outer face of the palisade. The village heart gives them the banner of the faction;
- house pieces (`house_*.nbt`). Jigsaw connectors put up to 4 houses next to the main path.

To see a village in the game: `/locate structure folkhold_world:village`, then `/tp` to the position.

## Block models from a script

The models and the block states of the quest board come from a script, because the board has 9 parts. Change the script, then run it:

```bash
python3 tools/models/quest_board.py
```

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

- Each push and pull request runs the workflow **Build**: build, JUnit tests, translation check, GameTests. The jars (and the `.mrpack` file, when the modpack exists) and the coverage report of `core` are available as artifacts of the run.
- A change to `.devcontainer/` runs the workflow **Dev container**: it builds the image and checks the tools.
- To make a release, push a tag on `main`:

  ```bash
  git tag v0.1.0
  git push origin v0.1.0
  ```

  The workflow **Release** checks that the tag is on `main`, creates a GitHub Release with the jars and the `.mrpack` file, then publishes the jars to GitHub Packages (Maven, `io.github.nicolasgoyon.folkhold:<mod_id>`). A version with a suffix (for example `v0.1.0-beta.1`) is a pre-release. The release uses the same packwiz commit as the CI, so two runs on the same tag give the same `.mrpack` content.

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
