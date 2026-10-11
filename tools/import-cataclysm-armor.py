"""Restore the original Cataclysm 3.31 humanoid cubes omitted by the OBJ export.

Run with Python, passing --exporter to the inspected pack_cataclysm_art.py.
That parser reads the decompiled original model constructors; it does not modify
the source asset folder. UVs and cube poses stay in Minecraft's native Y-down space.
"""
import argparse
import importlib.util
import json
from pathlib import Path


def export(exporter: Path, output: Path):
    spec = importlib.util.spec_from_file_location("cataclysm_source_parser", exporter)
    parser = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parser)
    models = [
        ("ignitium", "ignitium_armor", "Ignitium_Armor_Model", 0.6),
        ("ignitium", "ignitium_armor_legs", "Ignitium_Armor_Model", 0.2),
        ("ignitium", "ignitium_elytra", "Ignitium_Elytra_chestplate_Model", 0.5),
        ("cursium", "cursium_armor", "Cursium_Armor_Model", 0.5),
        ("cursium", "cursium_armor_legs", "Cursium_Armor_Model", 0.2),
        ("bone_reptile", "bone_reptile_armor", "Bone_Reptile_Armor_Model", 1.0),
        ("monstrous_helm", "monstrous_helm", "MonstrousHelm_Model", 0.3),
        ("bloom_stone_pauldrons", "bloom_stone_pauldrons", "Bloom_Stone_Pauldrons_Model", 0.5),
    ]
    for folder, name, source, deformation in models:
        mesh = parser.parse_model(source)
        # HumanoidModel.createMesh from Minecraft 1.20.1; all six original armor
        # constructors start with this inherited mesh. CMModelLayers supplies g.
        base = {
            "head": (0, 0, -4, -8, -4, 8, 8, 8, False),
            "hat": (32, 0, -4, -8, -4, 8, 8, 8, False),
            "body": (16, 16, -4, 0, -2, 8, 12, 4, False),
            "right_arm": (40, 16, -3, -2, -2, 4, 12, 4, False),
            "left_arm": (40, 16, -1, -2, -2, 4, 12, 4, True),
            "right_leg": (0, 16, -2, 0, -2, 4, 12, 4, False),
            "left_leg": (0, 16, -2, 0, -2, 4, 12, 4, True),
        }
        bones = []
        for bid, original in mesh.bones.items():
            bone = dict(original)
            bone["id"] = bid
            bone["rotation"] = bone.pop("rot")
            if bone["name"] in base and mesh.bones[bone["parent"]]["parent"] is None:
                assert not bone["cubes"], "Unexpected original humanoid replacement"
                cube = base[bone["name"]]
                grow = deformation + (0.5 if bone["name"] == "hat" else 0)
                bone["cubes"] = [(*cube[:8], grow, grow, grow, cube[8])]
            bones.append(bone)
        data = {"source": "L_Enders_Cataclysm-3.31.jar", "model": source,
                "humanoid_deformation": deformation,
                "texture_width": mesh.tex_w, "texture_height": mesh.tex_h,
                "bones": bones}
        path = output / folder / (name + ".mesh.json")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(path.name, "cubes:", sum(len(b["cubes"]) for b in bones))


if __name__ == "__main__":
    arguments = argparse.ArgumentParser()
    arguments.add_argument("--exporter", type=Path, required=True)
    arguments.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1]
                           / "src/main/resources/assets/setsuna/cataclysm/armor")
    args = arguments.parse_args()
    export(args.exporter, args.output)
