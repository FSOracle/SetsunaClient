"""Import the user's Cataclysm item art and original item display transforms."""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path
from zipfile import ZipFile


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def import_items(art: Path, jar: Path, project: Path) -> dict:
    assets = project / "src/main/resources/assets/setsuna"
    destination = assets / "cataclysm/items"
    entries = []
    with ZipFile(jar) as archive:
        english = json.loads(archive.read("assets/cataclysm/lang/en_us.json"))
        original_names = set(archive.namelist())

        def read_model(identifier: str) -> dict:
            namespace, name = identifier.split(":", 1)
            return json.loads(archive.read(f"assets/{namespace}/models/{name}.json"))

        def resolved_model(identifier: str) -> dict:
            value = read_model(identifier)
            parent = value.get("parent", "")
            if parent.startswith("cataclysm:"):
                inherited = resolved_model(parent)
                inherited.update({k: v for k, v in value.items() if k not in ("display", "textures")})
                for field in ("display", "textures"):
                    inherited[field] = {**inherited.get(field, {}), **value.get(field, {})}
                return inherited
            return value

        for line in (art / "说明.txt").read_text(encoding="utf-8-sig").splitlines():
            fields = line.split("\t")
            if len(fields) < 5 or fields[0] not in ("武器", "工具"):
                continue
            category, chinese, item_id, kind = fields[:4]
            candidates = [p for p in (art / category).iterdir() if p.name.endswith("_" + item_id)]
            if len(candidates) != 1:
                raise ValueError(f"Expected exactly one art folder for {item_id}")
            source = candidates[0]
            output = destination / item_id
            output.mkdir(parents=True, exist_ok=True)
            manifest = {"id": item_id, "name_zh": chinese,
                        "name_en": english.get("item.cataclysm." + item_id, item_id),
                        "category": "tool" if category == "工具" else "weapon", "kind": kind,
                        "files": {}, "meshes": [], "emissive": []}
            for file in sorted(source.rglob("*")):
                if not file.is_file() or file.suffix not in (".png", ".mcmeta", ".obj", ".mtl"):
                    continue
                target = output / file.name
                if file.suffix == ".mtl":
                    # OBJ and texture resources are deliberately colocated in the client bundle.
                    target.write_text(file.read_text(encoding="utf-8").replace("textures/", ""), encoding="utf-8")
                else:
                    shutil.copyfile(file, target)
                manifest["files"][file.name] = hashlib.sha256(target.read_bytes()).hexdigest()
                if file.suffix == ".obj":
                    manifest["meshes"].append(file.name)
                if file.suffix == ".png" and (file.stem.endswith("_layer") or file.stem.endswith("_ghost")):
                    manifest["emissive"].append(file.name)

            model = resolved_model(f"cataclysm:item/{item_id}")
            write_json(output / "source_item.json", model)
            variants = {}
            pending = [override["model"] for override in model.get("overrides", [])]
            while pending:
                reference = pending.pop()
                if reference in variants or not reference.startswith("cataclysm:"):
                    continue
                variant = resolved_model(reference)
                filename = reference.rsplit("/", 1)[-1] + ".json"
                write_json(output / filename, variant)
                variants[reference] = filename
                pending.extend(override["model"] for override in variant.get("overrides", []))
            manifest["variants"] = variants

            if manifest["meshes"]:
                primary = item_id + ".obj"
                if primary not in manifest["meshes"]:
                    primary = item_id + "_item.obj"
                if primary not in manifest["meshes"]:
                    raise ValueError(f"No held mesh for {item_id}")
                material = (output / primary).with_suffix(".mtl").read_text(encoding="utf-8")
                texture = next(row.split(maxsplit=1)[1] for row in material.splitlines() if row.startswith("map_Kd "))
                if not (output / texture).is_file():
                    raise ValueError(f"Missing primary texture for {item_id}: {texture}")
                manifest.update({"mesh": primary, "texture": texture})
            else:
                texture = item_id + ".png"
                manifest["texture"] = texture
                native_texture = assets / "textures/item/cataclysm" / texture
                native_texture.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(output / texture, native_texture)
                original_texture = f"assets/cataclysm/textures/item/{texture}"
                if original_texture in original_names:
                    if archive.read(original_texture) != native_texture.read_bytes():
                        raise ValueError(f"Art texture differs from the original mod: {item_id}")
                native_model = dict(model)
                native_model["textures"] = {
                    key: "setsuna:item/cataclysm/" + item_id if value == f"cataclysm:item/{item_id}" else value
                    for key, value in model.get("textures", {}).items()
                }
                if any(value.startswith("cataclysm:") for value in native_model["textures"].values()):
                    raise ValueError(f"Unmapped flat model texture for {item_id}")
                write_json(assets / f"models/item/cataclysm/{item_id}.json", native_model)
                write_json(assets / f"items/cataclysm/{item_id}.json",
                           {"model": {"type": "minecraft:model", "model": f"setsuna:item/cataclysm/{item_id}"}})
            entries.append(manifest)

    catalog = {"format_version": 1, "source_mod": "L_Ender's Cataclysm 3.31",
               "source_jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "items": entries}
    write_json(destination / "manifest.json", catalog)
    (destination / "SOURCE.txt").write_text(
        "Original art: L_Ender's Cataclysm 3.31, from the user-provided local art pack.\n"
        "source_item.json preserves the original item's display transforms and variant predicates.\n"
        "Model vertices use pixels with Y up. PNG bytes are copied without recoloring or resizing.\n"
        "Only local appearance is implemented; original mod combat behavior is not included.\n",
        encoding="utf-8")
    return {"items": len(entries), "mesh_items": sum(bool(row["meshes"]) for row in entries),
            "flat_items": sum(not row["meshes"] for row in entries), "manifest": str(destination / "manifest.json")}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--art-dir", type=Path, required=True)
    parser.add_argument("--mod-jar", type=Path, required=True)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    print(json.dumps(import_items(args.art_dir, args.mod_jar, args.project), ensure_ascii=False, indent=2))
