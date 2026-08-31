"""Render quick orthographic previews of a structure NBT for design review."""

from __future__ import annotations

import argparse
import gzip
from pathlib import Path

from PIL import Image, ImageDraw

from validate_templates import Reader


COLORS = {
    "minecraft:stone_bricks": "#676b70",
    "minecraft:tuff_bricks": "#53605b",
    "minecraft:bricks": "#884b3d",
    "minecraft:deepslate_tiles": "#25282e",
    "minecraft:cut_copper": "#b8673f",
    "minecraft:polished_andesite": "#9ba0a2",
    "minecraft:spruce_planks": "#7b512e",
    "minecraft:stripped_spruce_log": "#8a633f",
    "minecraft:iron_bars": "#b7bec1",
    "minecraft:glass_pane": "#8ccbd2",
}


def color(name: str) -> str:
    if name.startswith("create:brass"):
        return "#c79a35"
    if name.startswith("create:"):
        return "#657980"
    if name.startswith("aeronautics:"):
        return "#cf753b"
    return COLORS.get(name, "#8b8377")


def load(path: Path):
    root = Reader(gzip.decompress(path.read_bytes())).root()
    palette = root["palette"][1]
    blocks = {}
    for entry in root["blocks"][1]:
        state = palette[entry["state"][1]]
        blocks[tuple(entry["pos"][1])] = state["Name"][1]
    return tuple(root["size"][1]), blocks


def panel(draw, origin, width, height, title):
    x, y = origin
    draw.rectangle((x, y, x + width, y + height), fill="#11151a", outline="#46515b", width=2)
    draw.text((x + 8, y + 6), title, fill="#e8ecef")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("template", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--level", type=int, default=4)
    args = parser.parse_args()
    (sx, sy, sz), blocks = load(args.template)
    scale = 10
    margin = 28
    header = 30
    panel_w = max(sx, sz) * scale + margin * 2
    panel_h = max(sz, sy) * scale + margin * 2 + header
    image = Image.new("RGB", (panel_w * 4, panel_h), "#0b0e11")
    draw = ImageDraw.Draw(image)

    origins = [(0, 0), (panel_w, 0), (panel_w * 2, 0), (panel_w * 3, 0)]
    for origin, title in zip(origins, ("ROOF PLAN", f"LEVEL Y={args.level}", "SOUTH ELEVATION", "CENTRE SECTION")):
        panel(draw, origin, panel_w, panel_h, title)

    ox, oy = origins[0]
    for x in range(sx):
        for z in range(sz):
            ys = [y for px, y, pz in blocks if px == x and pz == z]
            if ys:
                name = blocks[(x, max(ys), z)]
                draw.rectangle((ox + margin + x * scale, oy + header + margin + z * scale,
                                ox + margin + (x + 1) * scale - 1, oy + header + margin + (z + 1) * scale - 1), fill=color(name))

    ox, oy = origins[1]
    for x in range(sx):
        for z in range(sz):
            name = blocks.get((x, args.level, z))
            if name:
                draw.rectangle((ox + margin + x * scale, oy + header + margin + z * scale,
                                ox + margin + (x + 1) * scale - 1, oy + header + margin + (z + 1) * scale - 1), fill=color(name))

    ox, oy = origins[2]
    for x in range(sx):
        for y in range(sy):
            zs = [z for px, py, z in blocks if px == x and py == y]
            if zs:
                name = blocks[(x, y, max(zs))]
                y0 = oy + header + margin + (sy - y - 1) * scale
                draw.rectangle((ox + margin + x * scale, y0,
                                ox + margin + (x + 1) * scale - 1, y0 + scale - 1), fill=color(name))

    ox, oy = origins[3]
    section_x = sx // 2
    for z in range(sz):
        for y in range(sy):
            name = blocks.get((section_x, y, z))
            if name:
                y0 = oy + header + margin + (sy - y - 1) * scale
                draw.rectangle((ox + margin + z * scale, y0,
                                ox + margin + (z + 1) * scale - 1, y0 + scale - 1), fill=color(name))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)
    print(args.output)


if __name__ == "__main__":
    main()
