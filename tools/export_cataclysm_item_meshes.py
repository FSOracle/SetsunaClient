"""Export original Cataclysm item cubes without flattening UVs, mirrored parts or animation bones."""
import argparse
import importlib.util
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--exporter", type=Path, required=True)
    parser.add_argument("--model-sources", type=Path, required=True)
    parser.add_argument("--output", type=Path,
                        default=Path(__file__).resolve().parents[1] / "src/main/resources/assets/setsuna/cataclysm/items")
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("cataclysm_source_parser", args.exporter)
    source = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(source)
    source.SRC = args.model_sources
    count = 0
    for entry in source.build_entries():
        if entry["cat"] != "武器" or not entry["models"]:
            continue
        model = source.parse_model(entry["models"][0][0])
        payload = {
            "texture_width": model.tex_w,
            "texture_height": model.tex_h,
            "bones": [{"id": key, "name": bone["name"], "parent": bone["parent"],
                       "pivot": bone["pivot"], "rotation": bone["rot"], "cubes": bone["cubes"]}
                      for key, bone in model.bones.items()],
        }
        # Citadel scales these cubes without scaling child pivots. A geometry child keeps
        # that behavior when Minecraft's ModelPart would otherwise scale the whole subtree.
        if entry["id"] in ("cursed_bow", "wrath_of_the_desert"):
            for bone in list(payload["bones"]):
                if "arrow_pivot" in bone["name"] or "_sand" in bone["name"]:
                    payload["bones"].append({
                        "id": bone["id"] + "_geometry", "name": "setsuna_geometry",
                        "parent": bone["id"], "pivot": [0, 0, 0], "rotation": [0, 0, 0],
                        "cubes": bone["cubes"],
                    })
                    bone["cubes"] = []
        destination = args.output / entry["id"] / "model.mesh.json"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        count += 1
    print(f"Exported {count} item models with original hierarchy, cube UVs and mirror flags.")


if __name__ == "__main__":
    main()
