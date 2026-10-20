#!/usr/bin/env python3
"""Export the blocks of tools/pixvox into the resources of folkhold_blocks, with PixVox.

Each file tools/pixvox/<name>.py (not this file, not a file that starts with "_") defines build(): a PixVox asset or a
list of assets. The name of an asset is the name of a block model of folkhold_blocks.

Output (in blocks/src/main/resources/assets/folkhold_blocks):
- models/block/<asset>.json: the model of the asset.
- textures/block/<asset>/: the textures of the asset. The script makes this folder again at each run.

The block states and the item models of the mod do not change. PixVox writes the formats of Minecraft 1.21.4 and
later. The script changes the models for Minecraft 1.21.1 (NeoForge):
- The light of an element ("light_emission") becomes "neoforge_data", with the block light and the sky light.
- "shade_direction_override" goes away: 1.21.1 reads "shade".
- A model with a glowing layer gets the render type "cutout": the transparent pixels of the layer do not show.

The export checks each model with a new render. A failed check stops the script with exit code 1.

    pip install -r tools/pixvox/requirements.txt
    python3 tools/pixvox/export.py               # writes the models and the textures
    python3 tools/pixvox/export.py --review      # also writes the review boards of PixVox into build/pixvox
"""
import argparse
import importlib.util
import json
import os
import shutil
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
NS = "folkhold_blocks"
ROOT = REPO / "blocks" / "src" / "main" / "resources" / "assets" / NS
REVIEW = REPO / "build" / "pixvox"


def load_assets():
    """The assets of all the files of tools/pixvox, in the order of the file names."""
    sys.path.insert(0, str(HERE))
    assets = []
    for path in sorted(HERE.glob("*.py")):
        if path.name == "export.py" or path.name.startswith("_"):
            continue
        spec = importlib.util.spec_from_file_location(f"folkhold_pixvox_{path.stem}", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        built = module.build()
        assets.extend(built if isinstance(built, list) else [built])
    names = [asset.name for asset in assets]
    if len(names) != len(set(names)):
        raise SystemExit(f"Two assets have the same name: {names}")
    return assets


def texture_path(reference, name):
    """The new reference of a texture of an asset: the texture goes into the folder of the asset."""
    prefix = f"{NS}:block/{name}"
    if reference == prefix:
        return f"{prefix}/atlas"
    if reference.startswith(prefix + "_"):
        return f"{prefix}/{reference[len(prefix) + 1:]}"
    return reference


def convert_model(model, name):
    """Changes a model of PixVox for Minecraft 1.21.1 (see the description of the script)."""
    glows = False
    for element in model.get("elements", []):
        element.pop("shade_direction_override", None)
        light = element.pop("light_emission", None)
        if light:
            element["neoforge_data"] = {"block_light": light, "sky_light": light}
            glows = True
    if glows and "render_type" not in model:
        model["render_type"] = "minecraft:cutout"
    model["textures"] = {key: texture_path(value, name) for key, value in model.get("textures", {}).items()}
    return model


def owner(stem, assets):
    """The name of the asset of a texture: the longest name that starts the name of the texture, or None."""
    names = [asset.name for asset in assets if stem == asset.name or stem.startswith(asset.name + "_")]
    return max(names, key=len, default=None)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def export(assets, pack):
    from pixvox import export_pack
    from pixvox.export import verified

    infos = export_pack(assets, root=str(pack), namespace=NS)
    failed = [info for info in infos if not verified(info)]
    if failed:
        raise SystemExit(f"PixVox check failed: {failed}")
    source = pack / "assets" / NS
    for asset in assets:
        name = asset.name
        model = json.loads((source / "models" / "block" / f"{name}.json").read_text(encoding="utf-8"))
        write_json(ROOT / "models" / "block" / f"{name}.json", convert_model(model, name))
        folder = ROOT / "textures" / "block" / name
        shutil.rmtree(folder, ignore_errors=True)
        folder.mkdir(parents=True)
        for texture in sorted((source / "textures" / "block").iterdir()):
            stem = texture.name.split(".")[0]
            if owner(stem, assets) != name:
                continue
            suffix = texture.name[len(name):]
            target = "atlas" + suffix if suffix.startswith(".") else suffix[1:]
            shutil.copyfile(texture, folder / target)
        print(f"{name}: models/block/{name}.json, textures/block/{name}/ ({len(list(folder.iterdir()))} files)")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--review", action="store_true", help="also write the review boards into build/pixvox")
    args = parser.parse_args()
    assets = load_assets()
    with tempfile.TemporaryDirectory() as tmp:
        export(assets, Path(tmp) / "pack")
    if args.review:
        from pixvox import review

        os.makedirs(REVIEW, exist_ok=True)
        for asset in assets:
            review(asset, out_dir=str(REVIEW))
        print(f"Review boards: {REVIEW}")


if __name__ == "__main__":
    main()
