"""Render source-geometry comparison images without launching Minecraft (requires Pillow and NumPy)."""
import argparse
import importlib.util
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


def load_parser(path):
    spec = importlib.util.spec_from_file_location("source_geometry", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def model_faces(path, parser, allowed=None):
    data = json.loads(path.read_text(encoding="utf-8"))
    nodes = {node["id"]: node for node in data["bones"]}
    matrices, branches = {}, {}

    def world(key):
        if key not in matrices:
            bone = nodes[key]
            local = np.array(parser.bone_local(bone["pivot"], bone["rotation"]))
            matrices[key] = local if bone["parent"] is None else world(bone["parent"]) @ local
        return matrices[key]

    def branch(key):
        bone = nodes[key]
        if bone["parent"] is None:
            return bone["name"]
        parent = nodes[bone["parent"]]
        return bone["name"] if parent["name"] == "root" else branch(bone["parent"])

    result = []
    for key, bone in nodes.items():
        if allowed and branch(key) not in allowed:
            continue
        for cube in bone["cubes"]:
            for pairs in parser.cube_quads(cube, data["texture_width"], data["texture_height"]):
                positions, uv = [], []
                for xyz, tex in pairs:
                    v = world(key) @ np.array([*xyz, 1.0])
                    positions.append([v[0], -v[1], -v[2]])
                    uv.append(tex)
                result.append((np.array(positions), np.array(uv)))
    return result


def obj_faces(path):
    vertices, uv, result = [], [], []
    for line in path.read_text(encoding="utf-8").splitlines():
        tokens = line.split()
        if not tokens:
            continue
        if tokens[0] == "v":
            vertices.append([float(tokens[1]), float(tokens[2]), -float(tokens[3])])
        elif tokens[0] == "vt":
            uv.append([float(tokens[1]), 1 - float(tokens[2])])
        elif tokens[0] == "f":
            refs = [list(map(int, ref.split("/")[:2])) for ref in tokens[1:]]
            result.append((np.array([vertices[v-1] for v, t in refs]), np.array([uv[t-1] for v, t in refs])))
    return result


def render(faces, texture, size=500, frame=None):
    pixels = np.array(Image.open(texture).convert("RGBA"))
    th, tw = pixels.shape[:2]
    yaw, pitch = np.radians(25), np.radians(-8)
    ry = np.array([[np.cos(yaw), 0, np.sin(yaw)], [0, 1, 0], [-np.sin(yaw), 0, np.cos(yaw)]])
    rx = np.array([[1, 0, 0], [0, np.cos(pitch), -np.sin(pitch)], [0, np.sin(pitch), np.cos(pitch)]])
    rotation = rx @ ry
    rotated = [(points @ rotation.T, uv) for points, uv in faces]
    allpoints = np.concatenate([points for points, _ in rotated])
    if frame is None:
        lower, upper = allpoints.min(axis=0), allpoints.max(axis=0)
        frame = ((lower + upper) / 2, (size - 54) / max((upper-lower)[:2]))
    center, scale = frame
    image = np.zeros((size, size, 4), dtype=np.uint8)
    image[:] = [23, 27, 33, 255]
    depth = np.full((size, size), -np.inf)
    for points, uv in rotated:
        projected = (points - center) * scale
        projected[:, 0] += size / 2
        projected[:, 1] = size / 2 - projected[:, 1]
        n = np.cross(points[1]-points[0], points[2]-points[0])
        magnitude = np.linalg.norm(n)
        if magnitude < 1e-8:
            continue
        shade = 0.78 + 0.22 * abs(float(np.dot(n/magnitude, [0.3, 0.6, 0.74])))
        for indices in ((0, 1, 2), (0, 2, 3)):
            p, tex = projected[list(indices)], uv[list(indices)]
            lo = np.maximum(np.floor(p[:, :2].min(axis=0)).astype(int), 0)
            hi = np.minimum(np.ceil(p[:, :2].max(axis=0)).astype(int), size-1)
            if (hi < lo).any():
                continue
            xx, yy = np.meshgrid(np.arange(lo[0], hi[0]+1)+0.5, np.arange(lo[1], hi[1]+1)+0.5)
            den = (p[1, 1]-p[2, 1])*(p[0, 0]-p[2, 0])+(p[2, 0]-p[1, 0])*(p[0, 1]-p[2, 1])
            if abs(den) < 1e-8:
                continue
            a = ((p[1, 1]-p[2, 1])*(xx-p[2, 0])+(p[2, 0]-p[1, 0])*(yy-p[2, 1]))/den
            b = ((p[2, 1]-p[0, 1])*(xx-p[2, 0])+(p[0, 0]-p[2, 0])*(yy-p[2, 1]))/den
            c = 1-a-b
            z = a*p[0, 2]+b*p[1, 2]+c*p[2, 2]
            u = np.clip(((a*tex[0, 0]+b*tex[1, 0]+c*tex[2, 0])*tw).astype(int), 0, tw-1)
            v = np.clip(((a*tex[0, 1]+b*tex[1, 1]+c*tex[2, 1])*th).astype(int), 0, th-1)
            color = pixels[v, u].copy()
            color[:, :, :3] = np.clip(color[:, :, :3]*shade, 0, 255)
            target_depth = depth[lo[1]:hi[1]+1, lo[0]:hi[0]+1]
            mask = (a >= 0) & (b >= 0) & (c >= 0) & (z > target_depth) & (color[:, :, 3] >= 26)
            target = image[lo[1]:hi[1]+1, lo[0]:hi[0]+1]
            target[mask] = color[mask]
            target_depth[mask] = z[mask]
    return Image.fromarray(image), frame


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("--exporter", type=Path, required=True)
    args.add_argument("--output", type=Path, default=Path("build/reports/cataclysm"))
    cfg = args.parse_args()
    parser = load_parser(cfg.exporter)
    root = Path(__file__).resolve().parents[1] / "src/main/resources/assets/setsuna/cataclysm/armor"
    cases = [("ignitium", "ignitium_armor", "armor_ignitium_armor.png", "IGNITIUM"),
             ("cursium", "cursium_armor", "cursium_armor.png", "CURSIUM"),
             ("bone_reptile", "bone_reptile_armor", "bone_reptile_armor.png", "BONE REPTILE")]
    board = Image.new("RGB", (1500, 1110), "#10151d")
    draw = ImageDraw.Draw(board)
    font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 23)
    for index, (folder, model, texture, label) in enumerate(cases):
        base = root / folder
        complete, frame = render(model_faces(base / (model+".mesh.json"), parser,
                                {"head", "hat", "body", "left_arm", "right_arm"}), base/texture)
        incomplete, _ = render(obj_faces(base/(model+".obj")), base/texture, frame=frame)
        board.paste(incomplete.convert("RGB"), (index*500, 42))
        board.paste(complete.convert("RGB"), (index*500, 600))
        draw.text((index*500+24, 12), label+" / PREVIOUS OBJ", font=font, fill="#B1BCCA")
        draw.text((index*500+24, 568), label+" / RESTORED SOURCE", font=font, fill="#E9DDA5")
    cfg.output.mkdir(parents=True, exist_ok=True)
    output = cfg.output / "armor-source-comparison.png"
    board.save(output)
    print(output)


if __name__ == "__main__":
    main()
