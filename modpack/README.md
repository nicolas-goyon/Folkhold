# Folkhold modpack

packwiz modpack for Minecraft 1.21.1 and NeoForge 21.1.259.

- `pack.toml`: pack definition (Minecraft and NeoForge versions).
- `index.toml`: list of files with their hashes. packwiz updates it (`packwiz refresh`).
- `mods/*.pw.toml`: one metafile for each mod (download URL, hash, update source).
- `add-base-mods.sh`: adds the base mods of the design document (run one time).

Use packwiz in the dev container. See [Getting started](../docs/getting-started.md#modpack).

## Rules

- Each update goes through a branch, a test launch and a test world before the merge.
- Pin the beta and unofficial mods (`packwiz pin <mod>`).
- The Folkhold mods are not in `mods/`. The export script (`scripts/export-modpack.sh`) adds the built jars to the `.mrpack` file.
