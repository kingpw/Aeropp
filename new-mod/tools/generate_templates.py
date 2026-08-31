"""Generate the standalone NBT structure templates used by Aeropp Structures.

The project intentionally keeps its first buildings data-driven.  This small
generator uses only the Python standard library, so a reviewer can reproduce
all binary assets without installing a Minecraft toolchain.
"""

from __future__ import annotations

import hashlib
import gzip
import json
import math
import struct
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
# Minecraft 1.21.1 的 StructureTemplateManager 使用 FileToIdConverter("structure", ".nbt")。
OUT = ROOT / "src" / "main" / "resources" / "data" / "aeropp_structures" / "structure"
MOD_AUTHOR = "Aeropp Structures"


def u16(value: int) -> bytes:
    return struct.pack(">H", value)


def nbt_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return u16(len(encoded)) + encoded


def payload(tag: int, value: Any) -> bytes:
    if tag == 1:
        return struct.pack(">b", value)
    if tag == 2:
        return struct.pack(">h", value)
    if tag == 3:
        return struct.pack(">i", value)
    if tag == 4:
        return struct.pack(">q", value)
    if tag == 5:
        return struct.pack(">f", value)
    if tag == 6:
        return struct.pack(">d", value)
    if tag == 8:
        return nbt_string(value)
    if tag == 9:
        item_tag, values = value
        return bytes([item_tag]) + struct.pack(">i", len(values)) + b"".join(
            payload(item_tag, item) for item in values
        )
    if tag == 10:
        return compound_payload(value)
    if tag == 11:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">i", item) for item in value)
    if tag == 12:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">q", item) for item in value)
    raise ValueError(f"Unsupported NBT tag: {tag}")


def compound_payload(values: dict[str, tuple[int, Any]]) -> bytes:
    out = bytearray()
    for name, (tag, value) in values.items():
        out.append(tag)
        out.extend(nbt_string(name))
        out.extend(payload(tag, value))
    out.append(0)
    return bytes(out)


def write_nbt(path: Path, values: dict[str, tuple[int, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    # Minecraft 1.21.1 的 StructureTemplateManager 使用 NbtIo.readCompressed，
    # 因此结构模板必须是 GZIP 压缩的 NBT，而不是裸 NBT。
    raw = bytes([10]) + nbt_string("") + compound_payload(values)
    path.write_bytes(gzip.compress(raw, mtime=0))


class Structure:
    def __init__(self, name: str, size: tuple[int, int, int]):
        self.name = name
        self.size = size
        # Structure palettes store complete block states, not only block IDs.
        # This matters for Create belts, shafts and bearings: their orientation
        # is part of the template and cannot be recovered from the block name.
        self.palette: list[dict[str, tuple[int, Any]]] = [{"Name": (8, "minecraft:air")}]
        self.palette_index = {("minecraft:air", ()): 0}
        self.blocks: dict[
            tuple[int, int, int],
            tuple[str, dict[str, Any] | None, dict[str, str]],
        ] = {}

    def _index(self, block: str, properties: dict[str, str] | None = None) -> int:
        normalized = tuple(sorted((properties or {}).items()))
        key = (block, normalized)
        if key not in self.palette_index:
            entry: dict[str, tuple[int, Any]] = {"Name": (8, block)}
            if normalized:
                entry["Properties"] = (10, {name: (8, value) for name, value in normalized})
            self.palette_index[key] = len(self.palette)
            self.palette.append(entry)
        return self.palette_index[key]

    def set(
        self,
        x: int,
        y: int,
        z: int,
        block: str,
        nbt: dict[str, Any] | None = None,
        properties: dict[str, str] | None = None,
    ) -> None:
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            return
        if block == "minecraft:air":
            self.blocks.pop((x, y, z), None)
        else:
            normalized = dict(properties or {})
            self.blocks[(x, y, z)] = (block, nbt, normalized)
            self._index(block, normalized)

    def fill(self, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block: str) -> None:
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, block)

    def chest(self, x: int, y: int, z: int, loot: str = "minecraft:chests/simple_dungeon") -> None:
        if loot == "minecraft:chests/simple_dungeon":
            loot = f"aeropp_structures:chests/{self.loot_category()}"
        self.set(x, y, z, "minecraft:chest", {"id": "minecraft:chest", "LootTable": loot})

    def barrel(self, x: int, y: int, z: int, loot: str = "minecraft:chests/abandoned_mineshaft") -> None:
        if loot == "minecraft:chests/abandoned_mineshaft":
            loot = f"aeropp_structures:chests/{self.loot_category()}"
        self.set(x, y, z, "minecraft:barrel", {"id": "minecraft:barrel", "LootTable": loot})

    def loot_category(self) -> str:
        if self.name in {"field_battery", "ironclad_watchtower", "clockwork_dock", "bombed_outpost"}:
            return "target_cache"
        if self.name in {"ruined_engine_works", "buried_reliquary", "skywarden_fort"}:
            return "dungeon_cache"
        return "landmark_cache"

    def nbt(self) -> dict[str, tuple[int, Any]]:
        blocks: list[dict[str, tuple[int, Any]]] = []
        for (x, y, z), (block, block_nbt, properties) in sorted(self.blocks.items(), key=lambda item: item[0]):
            entry: dict[str, tuple[int, Any]] = {
                "pos": (9, (3, [x, y, z])),
                "state": (3, self._index(block, properties)),
            }
            if block_nbt:
                entry["nbt"] = (10, {key: (8, value) for key, value in block_nbt.items()})
            blocks.append(entry)
        return {
            "DataVersion": (3, 3953),
            "author": (8, MOD_AUTHOR),
            "size": (9, (3, list(self.size))),
            "palette": (9, (10, self.palette)),
            "blocks": (9, (10, blocks)),
            "entities": (9, (10, [])),
        }

    def save(self) -> None:
        write_nbt(OUT / f"{self.name}.nbt", self.nbt())


def create(s: Structure, x: int, y: int, z: int, name: str, properties: dict[str, str] | None = None) -> None:
    """Place a Create block while keeping its block-state properties explicit."""
    s.set(x, y, z, f"create:{name}", properties=properties)


def aero(s: Structure, x: int, y: int, z: int, name: str, properties: dict[str, str] | None = None) -> None:
    """Place a Create Aeronautics block from the bundled mod's stable namespace."""
    s.set(x, y, z, f"aeronautics:{name}", properties=properties)


def simulated(s: Structure, x: int, y: int, z: int, name: str, properties: dict[str, str] | None = None) -> None:
    """Place a Create Simulated block bundled with Create Aeronautics."""
    s.set(x, y, z, f"simulated:{name}", properties=properties)


def belt(s: Structure, x: int, y: int, z: int, facing: str = "east", part: str = "middle") -> None:
    create(s, x, y, z, "belt", {
        "facing": facing, "part": part, "slope": "horizontal", "waterlogged": "false",
    })


def shaft(s: Structure, x: int, y: int, z: int, axis: str = "y", encased: bool = False) -> None:
    create(s, x, y, z, "andesite_encased_shaft" if encased else "shaft", {"axis": axis})


def cog(s: Structure, x: int, y: int, z: int, axis: str = "y", large: bool = False) -> None:
    create(s, x, y, z, "large_cogwheel" if large else "cogwheel", {"axis": axis})


def pipe(s: Structure, x: int, y: int, z: int) -> None:
    # A bare pipe is a valid neutral state; players can connect it with a wrench.
    create(s, x, y, z, "fluid_pipe")


def tank(s: Structure, x: int, y: int, z: int, bottom: bool = False, top: bool = False) -> None:
    create(s, x, y, z, "fluid_tank", {
        "bottom": str(bottom).lower(), "shape": "plain", "top": str(top).lower(),
    })


def scaffold(s: Structure, x: int, y: int, z: int, bottom: bool = False) -> None:
    """A lightweight brass frame used for the diesel-punk facade language."""
    create(s, x, y, z, "brass_scaffolding", {"bottom": str(bottom).lower()})


def recenter(s: Structure, pad_x: int, pad_y: int, pad_z: int, size: tuple[int, int, int]) -> None:
    """Move an existing functional core into a larger architectural envelope."""
    s.blocks = {
        (x + pad_x, y + pad_y, z + pad_z): value
        for (x, y, z), value in s.blocks.items()
    }
    s.size = size


def corner_tower(s: Structure, x: int, z: int, height: int, block: str) -> None:
    """Three-by-three buttress tower; open corners keep silhouettes irregular."""
    s.fill(x, 0, z, x + 2, 0, z + 2, "minecraft:stone_bricks")
    s.fill(x, 1, z, x + 2, height, z + 2, block)
    create(s, x + 1, height + 1, z + 1, "metal_girder")
    s.set(x + 1, height, z + 1, "minecraft:lantern")


def stepped_ring(s: Structure, center_x: int, center_z: int, y: int, radius: int, block: str) -> None:
    """Square ring with clipped corners, useful for ziggurats and keeps."""
    for x in range(center_x - radius, center_x + radius + 1):
        for z in range(center_z - radius, center_z + radius + 1):
            if abs(x - center_x) == radius or abs(z - center_z) == radius:
                if abs(x - center_x) + abs(z - center_z) < radius * 2:
                    s.set(x, y, z, block)


def clip_floor_corners(s: Structure, x1: int, z1: int, x2: int, z2: int, cut: int) -> None:
    """Remove only vanilla floor tiles at core corners to break the box silhouette."""
    for xa, xb in ((x1, x1 + cut - 1), (x2 - cut + 1, x2)):
        for za, zb in ((z1, z1 + cut - 1), (z2 - cut + 1, z2)):
            for x in range(xa, xb + 1):
                for z in range(za, zb + 1):
                    for y in (0, 1):
                        current = s.blocks.get((x, y, z))
                        if current and current[0].startswith("minecraft:"):
                            s.set(x, y, z, "minecraft:air")


def epicize(s: Structure) -> Structure:
    """Wrap each functional core in a distinct, non-rectangular landmark shell."""
    name = s.name
    # The dock is the flagship room-graph build and is authored at final scale;
    # it must not receive the generic shell a second time.
    if name == "clockwork_dock":
        return s
    if name in {"field_battery", "bombed_outpost"}:
        recenter(s, 4, 0, 4, (23, 10, 23))
        material = "create:copper_casing" if name == "bombed_outpost" else "create:brass_casing"
        for x, z in ((1, 1), (19, 1), (1, 19), (19, 19)):
            corner_tower(s, x, z, 6 if name == "field_battery" else 4, material)
        clip_floor_corners(s, 4, 4, 18, 18, 2)
        for x in range(4, 19, 3):
            scaffold(s, x, 3, 2)
            scaffold(s, x, 3, 20, bottom=True)
        if name == "bombed_outpost":
            s.fill(2, 1, 9, 5, 1, 13, "minecraft:gravel")
            s.fill(17, 1, 4, 21, 1, 8, "minecraft:gravel")
    elif name == "clockwork_dock":
        recenter(s, 5, 0, 5, (35, 15, 27))
        for x, z in ((1, 1), (31, 1), (1, 23), (31, 23)):
            corner_tower(s, x, z, 11, "create:brass_casing")
        for x in range(5, 31, 4):
            scaffold(s, x, 3, 3)
            scaffold(s, x, 3, 23, bottom=True)
        for x in range(4, 32, 2):
            create(s, x, 12, 3, "metal_girder")
            create(s, x, 12, 23, "metal_girder")
        clip_floor_corners(s, 5, 5, 29, 21, 3)
        # A raised transverse gantry makes the dock legible from a distance.
        for z in range(6, 22):
            scaffold(s, 3, 9, z)
            scaffold(s, 31, 9, z, bottom=True)
    elif name == "ruined_engine_works":
        recenter(s, 5, 0, 3, (29, 14, 25))
        for x, z in ((1, 1), (25, 1), (1, 21), (25, 21)):
            corner_tower(s, x, z, 8, "minecraft:deepslate_bricks")
        clip_floor_corners(s, 5, 3, 23, 21, 3)
        # Asymmetric side wings preserve the abandoned-dungeon feeling.
        s.fill(2, 1, 6, 4, 2, 18, "minecraft:tuff_bricks")
        s.fill(24, 1, 8, 26, 2, 16, "minecraft:deepslate_bricks")
        for z in range(6, 19, 3):
            scaffold(s, 4, 4, z)
    elif name == "buried_reliquary":
        recenter(s, 4, 0, 4, (25, 16, 25))
        for radius, y in ((11, 1), (9, 3), (7, 5)):
            stepped_ring(s, 12, 12, y, radius, "minecraft:deepslate_bricks")
        for x, z in ((2, 2), (20, 2), (2, 20), (20, 20)):
            corner_tower(s, x, z, 7, "create:brass_casing")
        for x, z in ((3, 12), (21, 12), (12, 3), (12, 21)):
            create(s, x, 6, z, "metal_girder")
    elif name == "skywarden_fort":
        recenter(s, 6, 0, 6, (35, 16, 35))
        for x, z in ((1, 1), (31, 1), (1, 31), (31, 31)):
            corner_tower(s, x, z, 13, "create:brass_casing")
        clip_floor_corners(s, 6, 6, 28, 28, 4)
        for x in range(6, 29, 2):
            create(s, x, 3, 3, "metal_girder")
            create(s, x, 3, 31, "metal_girder")
        for z in range(6, 29, 2):
            create(s, 3, 3, z, "metal_girder")
            create(s, 31, 3, z, "metal_girder")
        for y, radius in ((10, 10), (13, 7)):
            stepped_ring(s, 17, 17, y, radius, "create:brass_scaffolding")
    elif name == "ironclad_watchtower":
        recenter(s, 3, 0, 3, (17, 30, 17))
        for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
            corner_tower(s, x, z, 23, "create:brass_scaffolding")
        clip_floor_corners(s, 3, 3, 13, 13, 2)
        for y in (8, 15, 22):
            for x in range(4, 13, 2):
                scaffold(s, x, y, 2)
                scaffold(s, x, y, 14, bottom=True)
        for y in (24, 27, 29):
            stepped_ring(s, 8, 8, y, max(1, 4 - (y - 24) // 2), "create:brass_casing")
    elif name == "signal_obelisk":
        recenter(s, 3, 0, 3, (15, 26, 15))
        for radius, y in ((6, 1), (5, 3), (4, 5), (3, 7)):
            stepped_ring(s, 7, 7, y, radius, "create:andesite_casing")
        for x, z in ((2, 7), (12, 7), (7, 2), (7, 12)):
            s.fill(x, 2, z, x, 11, z, "create:brass_scaffolding")
            create(s, x, 12, z, "metal_girder")
    return s


def field_battery() -> Structure:
    s = Structure("field_battery", (15, 8, 15))
    s.fill(0, 0, 0, 14, 0, 14, "minecraft:stone_bricks")
    s.fill(0, 1, 0, 14, 1, 14, "minecraft:polished_andesite")
    for x, z in ((1, 1), (1, 13), (13, 1), (13, 13)):
        s.fill(x, 1, z, x, 5, z, "create:andesite_casing")
        create(s, x, 6, z, "metal_girder")
    # A short ammunition line: barrel -> chute -> belt -> press/depot -> vault.
    s.barrel(2, 2, 7)
    create(s, 3, 3, 7, "chute", {"facing": "down", "shape": "normal", "waterlogged": "false"})
    for x in range(3, 12):
        shaft(s, x, 1, 7, axis="x")
        if x in range(3, 7) or x in range(8, 11):
            part = "start" if x in (3, 8) else "end" if x in (6, 10) else "middle"
            belt(s, x, 2, 7, part=part)
    create(s, 7, 2, 7, "depot")
    create(s, 7, 4, 7, "mechanical_press", {"facing": "east"})
    create(s, 10, 3, 7, "andesite_belt_funnel", {
        "facing": "east", "powered": "false", "shape": "pushing", "waterlogged": "false",
    })
    create(s, 11, 2, 7, "item_vault", {"axis": "x", "large": "false"})
    # Compact power spine and a visible gun mount make the target read as a machine.
    create(s, 4, 1, 6, "gearbox", {"axis": "x"})
    cog(s, 5, 1, 6, axis="x")
    create(s, 6, 1, 6, "encased_fan", {"facing": "north"})
    s.fill(6, 1, 10, 8, 1, 12, "minecraft:iron_block")
    s.set(7, 2, 11, "minecraft:redstone_block")
    s.set(7, 3, 11, "minecraft:target")
    aero(s, 7, 4, 11, "mounted_potato_cannon", {
        "axis_along_first": "false", "blocked": "false", "facing": "north", "powered": "false",
    })
    s.chest(2, 2, 3)
    return s


def ironclad_watchtower() -> Structure:
    s = Structure("ironclad_watchtower", (11, 20, 11))
    s.fill(0, 0, 0, 10, 0, 10, "minecraft:stone_bricks")
    s.fill(1, 1, 1, 9, 1, 9, "create:andesite_casing")
    for x, z in ((1, 1), (1, 9), (9, 1), (9, 9)):
        s.fill(x, 2, z, x, 18, z, "create:brass_scaffolding")
        create(s, x, 19, z, "metal_girder")
    for y in (5, 9, 13, 17):
        s.fill(1, y, 1, 9, y, 9, "minecraft:spruce_planks")
        for x in range(2, 9, 2):
            create(s, x, y + 1, 1, "andesite_scaffolding", {"bottom": "false"})
    # Continuous vertical transmission couples the floors and gives the tower a
    # readable Create silhouette instead of a decorative empty shell.
    for y in range(2, 18):
        shaft(s, 5, y, 5, axis="y", encased=y % 4 == 0)
    for y in (5, 9, 13, 17):
        cog(s, 4, y, 5, axis="x", large=y in (9, 17))
        create(s, 6, y, 5, "gearbox", {"axis": "x"})
    create(s, 5, 17, 5, "mechanical_bearing", {"facing": "up"})
    aero(s, 5, 18, 5, "propeller_bearing", {"facing": "up"})
    aero(s, 5, 19, 5, "wooden_propeller", {"facing": "up", "reversed": "false"})
    aero(s, 3, 14, 5, "mounted_potato_cannon", {
        "axis_along_first": "false", "blocked": "false", "facing": "east", "powered": "false",
    })
    s.set(5, 17, 6, "minecraft:target")
    s.chest(2, 6, 2)
    return s


def chamfered_footprint(x1: int, z1: int, x2: int, z2: int, cut: int = 2) -> set[tuple[int, int]]:
    """Return a rectangle with stair-stepped corners instead of a box footprint."""
    cells = set()
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            corner_distances = (
                (x - x1) + (z - z1),
                (x2 - x) + (z - z1),
                (x - x1) + (z2 - z),
                (x2 - x) + (z2 - z),
            )
            if min(corner_distances) >= cut:
                cells.add((x, z))
    return cells


def room_shell(
    s: Structure,
    cells: set[tuple[int, int]],
    height: int,
    wall: str,
    floor: str = "minecraft:polished_andesite",
    windows: bool = True,
    boundary_domain: set[tuple[int, int]] | None = None,
) -> None:
    """Build a grounded room with real perimeter walls and a readable interior."""
    for x, z in cells:
        s.set(x, 0, z, "minecraft:stone_bricks")
        s.set(x, 1, z, floor)
        domain = boundary_domain or cells
        boundary = any((x + dx, z + dz) not in domain for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if not boundary:
            continue
        for y in range(2, height + 1):
            material = wall if y > 3 else "minecraft:stone_bricks"
            s.set(x, y, z, material)
        if windows and height >= 7 and (x * 3 + z * 5) % 7 == 0:
            s.set(x, 5, z, "minecraft:iron_bars")
            s.set(x, 6, z, "minecraft:iron_bars")


def hipped_roof(
    s: Structure,
    cells: set[tuple[int, int]],
    eave_y: int,
    block: str = "minecraft:deepslate_tiles",
    rise: int = 4,
) -> None:
    """Make a watertight roof with a solid deck and a stair-tile outer skin."""
    stair = {
        "minecraft:deepslate_tiles": "minecraft:deepslate_tile_stairs",
        "minecraft:bricks": "minecraft:brick_stairs",
        "minecraft:cut_copper": "minecraft:cut_copper_stairs",
    }.get(block)
    layer = set(cells)
    for level in range(rise + 1):
        for x, z in layer:
            s.set(x, eave_y + level, z, block)
        inset = {
            (x, z)
            for x, z in layer
            if all((x + dx, z + dz) in layer for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        }
        if stair:
            center_x = sum(x for x, _ in layer) / len(layer)
            center_z = sum(z for _, z in layer) / len(layer)
            for x, z in layer - inset:
                dx, dz = center_x - x, center_z - z
                facing = ("east" if dx > 0 else "west") if abs(dx) >= abs(dz) else ("south" if dz > 0 else "north")
                s.set(x, eave_y + level + 1, z, stair, properties={
                    "facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false",
                })
        if not inset:
            break
        layer = inset


def barrel_roof(
    s: Structure,
    x1: int,
    z1: int,
    x2: int,
    z2: int,
    eave_y: int,
    rise: int,
    roof: str = "minecraft:deepslate_tiles",
    gable: str = "minecraft:stone_bricks",
    skip: set[tuple[int, int]] | None = None,
) -> None:
    """Build a watertight barrel vault with framed skylights and stair ribs."""
    center = (x1 + x2) / 2
    radius = max(1.0, (x2 - x1) / 2)
    heights = {
        x: eave_y + round(rise * math.sqrt(max(0.0, 1.0 - ((x - center) / radius) ** 2)))
        for x in range(x1, x2 + 1)
    }
    for x in range(x1, x2 + 1):
        lower = min(heights[x], heights.get(x - 1, heights[x]))
        for z in range(z1, z2 + 1):
            if skip and (x, z) in skip:
                continue
            material = "create:framed_glass" if 35 <= x <= 45 and z % 9 in (4, 5) else roof
            for y in range(lower, heights[x] + 1):
                s.set(x, y, z, material)
            if material == roof and x not in (x1, x2):
                s.set(x, heights[x] + 1, z, "minecraft:deepslate_tile_stairs", properties={
                    "facing": "east" if x < center else "west",
                    "half": "bottom", "shape": "straight", "waterlogged": "false",
                })
        for z in (z1, z2):
            for y in range(eave_y, heights[x] + 1):
                s.set(x, y, z, gable)


def carve(s: Structure, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int) -> None:
    """Cut a deliberate doorway or passage after overlapping room shells are built."""
    s.fill(x1, y1, z1, x2, y2, z2, "minecraft:air")


def clockwork_dock() -> Structure:
    s = Structure("clockwork_dock", (81, 40, 70))

    # Narrative plan: public order hall -> production basilica -> cathedral
    # drydock. Power, logistics, control and a ruined test bay branch from that
    # central procession and explain why every wing exists.
    hangar = chamfered_footprint(14, 3, 66, 47, 4)
    assembly = chamfered_footprint(20, 43, 60, 67, 3)
    boiler = chamfered_footprint(3, 31, 23, 62, 3) | {
        (x, z) for x in range(17, 30) for z in range(49, 65)
    }
    warehouse = chamfered_footprint(57, 36, 77, 63, 3) | {
        (x, z) for x in range(52, 65) for z in range(49, 66)
    }
    control = chamfered_footprint(60, 10, 75, 31, 3)
    test_bay = chamfered_footprint(3, 6, 22, 28, 3)
    site = hangar | assembly | boiler | warehouse | control | test_bay

    # One shared perimeter prevents overlapping shells becoming blind corridors.
    room_shell(s, hangar, 18, "minecraft:stone_bricks", boundary_domain=site)
    room_shell(s, assembly, 13, "minecraft:tuff_bricks", boundary_domain=site)
    room_shell(s, boiler, 11, "minecraft:bricks", boundary_domain=site)
    room_shell(s, warehouse, 10, "minecraft:tuff_bricks", boundary_domain=site)
    # The control tower is a building within the nave and therefore keeps its
    # own closed envelope, with only deliberate doors and observation glazing.
    room_shell(s, control, 22, "minecraft:stone_bricks")
    room_shell(s, test_bay, 8, "minecraft:cracked_stone_bricks", windows=False, boundary_domain=site)
    barrel_roof(s, 14, 3, 66, 47, 18, 16, skip=control)
    hipped_roof(s, assembly, 14, rise=6)
    # Low roofs terminate against higher walls instead of protruding indoors.
    hipped_roof(s, boiler - hangar - assembly, 12, "minecraft:bricks", rise=4)
    hipped_roof(s, warehouse - hangar - assembly, 11, rise=4)
    hipped_roof(s, control, 23, "minecraft:cut_copper", rise=4)
    hipped_roof(s, test_bay, 9, "minecraft:deepslate_tiles", rise=3)

    # The route deliberately frames the flagship from the south entrance.
    carve(s, 36, 2, 65, 44, 9, 69)
    carve(s, 32, 2, 42, 48, 11, 48)
    carve(s, 17, 2, 49, 24, 7, 56)
    carve(s, 57, 2, 49, 64, 7, 56)
    carve(s, 59, 4, 18, 65, 9, 24)
    carve(s, 17, 2, 20, 24, 7, 33)
    # Paired bearing leaves can be assembled and redstone-driven in place.
    carve(s, 29, 2, 2, 51, 18, 5)
    for bearing_x, leaf in ((29, range(29, 40)), (51, range(41, 52))):
        create(s, bearing_x, 1, 3, "mechanical_bearing", {"facing": "up"})
        for x in leaf:
            for y in range(2, 17):
                frame = x in (leaf.start, leaf.stop - 1) or y in (2, 16) or (x + y) % 5 == 0
                s.set(x, y, 3, "create:metal_girder" if frame else "minecraft:iron_bars")
        s.set(bearing_x, 1, 4, "minecraft:lever", properties={
            "face": "floor", "facing": "north", "powered": "false",
        })

    # A human-scale version explains the same mechanism at the public entrance.
    for bearing_x, leaf in ((36, range(36, 40)), (44, range(41, 45))):
        create(s, bearing_x, 1, 66, "mechanical_bearing", {"facing": "up"})
        for x in leaf:
            for y in range(2, 9):
                frame = x in (leaf.start, leaf.stop - 1) or y in (2, 8)
                s.set(x, y, 66, "create:andesite_casing" if frame else "minecraft:spruce_planks")

    # Buttress rhythm and lamps turn the hangar into an industrial nave rather
    # than one oversized empty room.
    for z in range(8, 45, 6):
        for x in (14, 66):
            s.fill(x, 0, z, x, 21, z, "create:andesite_casing")
            create(s, x, 22, z, "metal_girder")
        for x in (19, 61):
            s.fill(x, 1, z, x, 8, z, "minecraft:stone_bricks")
            s.set(x, 9, z, "minecraft:lantern")

    # Order hall and production basilica. A clear centre aisle preserves the
    # first view of the ship while the complete line runs across the transept.
    s.set(24, 2, 56, "minecraft:spruce_planks")
    s.barrel(24, 3, 56)
    create(s, 25, 4, 56, "chute", {"facing": "down", "shape": "normal", "waterlogged": "false"})
    for x in range(25, 56):
        shaft(s, x, 2, 56, axis="x", encased=x in (31, 49))
    for start, end in ((25, 33), (35, 44), (46, 55)):
        for x in range(start, end + 1):
            belt(s, x, 3, 56, part="start" if x == start else "end" if x == end else "middle")
    create(s, 34, 3, 56, "depot")
    create(s, 34, 5, 56, "mechanical_press", {"facing": "east"})
    create(s, 45, 3, 56, "basin", {"facing": "north"})
    create(s, 45, 5, 56, "mechanical_mixer")
    for x in (34, 45):
        for y in range(6, 11):
            shaft(s, x, y, 56, axis="y", encased=y == 10)
    create(s, 51, 4, 56, "andesite_belt_funnel", {
        "facing": "east", "powered": "false", "shape": "pushing", "waterlogged": "false",
    })
    create(s, 57, 3, 56, "item_vault", {"axis": "x", "large": "true"})
    s.fill(23, 2, 56, 23, 11, 56, "create:brass_casing")
    s.fill(57, 2, 56, 57, 11, 56, "create:brass_casing")
    for x in range(24, 57):
        create(s, x, 11, 56, "metal_girder")
    for y in range(6, 11):
        shaft(s, 34, y, 56, axis="y", encased=y == 10)
        shaft(s, 45, y, 56, axis="y", encased=y == 10)
    for x in (34, 46):
        s.set(x, 2, 62, "minecraft:spruce_planks")
        create(s, x, 3, 62, "depot")
    shaft(s, 40, 2, 62, axis="y")
    create(s, 40, 3, 62, "mechanical_arm", {"ceiling": "false"})
    # Shift bell/order dais: the human reason for the factory's entrance hall.
    s.fill(38, 2, 64, 42, 2, 66, "minecraft:polished_blackstone")
    create(s, 39, 3, 65, "display_board")
    create(s, 41, 3, 65, "display_board")
    s.set(40, 3, 64, "minecraft:bell")

    # Boiler cathedral: fuel -> contiguous boilers -> pumped water -> engines
    # -> flywheels -> a shared drive spine entering production.
    for x in (7, 14):
        s.fill(x - 2, 0, 34, x + 2, 24, 38, "minecraft:bricks")
        s.fill(x, 2, 36, x, 24, 36, "minecraft:air")
        s.fill(x - 2, 25, 34, x + 2, 27, 38, "create:copper_casing")
    for bx, bz in ((7, 44), (13, 50)):
        for x in range(bx, bx + 3):
            for z in range(bz, bz + 3):
                create(s, x, 2, z, "blaze_burner")
                for y in range(3, 8):
                    tank(s, x, y, z, bottom=y == 3, top=y == 7)
        for z in (bz, bz + 2):
            create(s, bx + 3, 6, z, "steam_engine", {
                "face": "ceiling", "facing": "east", "waterlogged": "false",
            })
            s.fill(bx + 4, 2, z, bx + 4, 5, z, "create:andesite_casing")
            create(s, bx + 4, 6, z, "flywheel", {"axis": "x"})
        for x in range(4, bx + 2):
            pipe(s, x, 4, bz + 1)
        create(s, bx - 1, 4, bz + 1, "mechanical_pump", {"facing": "east"})
        s.fill(4, 2, bz + 1, 4, 3, bz + 1, "minecraft:water")
    for x in range(17, 30):
        shaft(s, x, 6, 47, axis="x", encased=x in (20, 24, 28))
    for z in range(47, 57):
        shaft(s, 29, 6, z, axis="z", encased=z in (47, 52, 56))
    for x, z in ((17, 47), (24, 47), (29, 47), (29, 55)):
        s.fill(x, 1, z, x, 5, z, "create:andesite_casing")
    for x in (6, 12, 18):
        s.fill(x, 8, 42, x, 8, 56, "create:metal_girder")
        s.fill(x, 1, 42, x, 7, 42, "minecraft:stone_bricks")
        s.fill(x, 1, 56, x, 7, 56, "minecraft:stone_bricks")
        s.set(x, 7, 42, "minecraft:lantern")

    # Logistics wing: dense racking, loading throat and one dispatch vault.
    for x in (61, 65, 69, 73):
        for z in (43, 49, 55):
            create(s, x, 2, z, "item_vault", {"axis": "z", "large": "false"})
    for z in range(42, 61, 4):
        s.fill(76, 2, z, 76, 5, z, "minecraft:spruce_planks")
    s.set(72, 2, 59, "minecraft:spruce_planks")
    s.chest(72, 3, 59)

    # Four stone-and-andesite cradles carry the incomplete flagship. The hull
    # is not a floating prop: each cradle reaches the foundation and touches it.
    for z in (14, 22, 30, 38):
        s.fill(31, 1, z, 34, 9, z, "create:andesite_casing")
        s.fill(46, 1, z, 49, 9, z, "create:andesite_casing")
        s.fill(34, 10, z, 46, 10, z, "minecraft:stone_bricks")

    # Tapered wooden gondola with a continuous keel, deck, windows, lift cells
    # and twin side propellers. It reads as a nearly finished vehicle.
    for z in range(11, 43):
        taper = min(z - 11, 42 - z)
        half = max(2, min(6, 2 + taper // 4))
        s.fill(40 - half, 11, z, 40 + half, 11, z, "minecraft:spruce_planks")
        for y in range(12, 16):
            s.set(40 - half, y, z, "minecraft:stripped_spruce_log")
            s.set(40 + half, y, z, "minecraft:stripped_spruce_log")
        s.fill(40 - half, 16, z, 40 + half, 16, z, "minecraft:deepslate_tiles")
        if z % 4 == 0:
            s.set(40 - half, 14, z, "minecraft:glass_pane")
            s.set(40 + half, 14, z, "minecraft:glass_pane")
    s.fill(40, 9, 9, 40, 11, 44, "minecraft:stripped_spruce_log")
    for z in (18, 26, 34):
        s.fill(36, 17, z, 36, 21, z, "create:brass_casing")
        s.fill(44, 17, z, 44, 21, z, "create:brass_casing")
    for x in (38, 42):
        for z in (24, 28):
            aero(s, x, 12, z, "levitite")
            aero(s, x, 13, z, "adjustable_burner", {"powered": "false", "variant": "fire"})
    # Flight deck and propulsion make this a testable Aeronautics vessel, not a
    # decorative balloon. Players can replace or reconfigure these before scan.
    simulated(s, 40, 12, 13, "physics_assembler", {"face": "floor", "facing": "north"})
    simulated(s, 39, 12, 14, "navigation_table", {"facing": "up"})
    simulated(s, 40, 12, 14, "steering_wheel", {
        "facing": "north", "on_floor": "true", "waterlogged": "false",
    })
    simulated(s, 41, 12, 14, "throttle_lever", {
        "face": "floor", "facing": "north", "inverted": "false",
    })
    simulated(s, 37, 12, 30, "white_portable_engine", {"facing": "south", "lit": "false"})
    simulated(s, 43, 12, 30, "white_portable_engine", {"facing": "south", "lit": "false"})
    aero(s, 35, 13, 32, "propeller_bearing", {"facing": "west"})
    aero(s, 34, 13, 32, "wooden_propeller", {"facing": "west", "reversed": "false"})
    aero(s, 45, 13, 32, "propeller_bearing", {"facing": "east"})
    aero(s, 46, 13, 32, "wooden_propeller", {"facing": "east", "reversed": "true"})

    # The envelope is the visual climax. Its full ellipsoid is deliberately
    # restrained to one ship and tied into the gondola by six brass struts.
    for x in range(30, 51):
        for y in range(21, 34):
            for z in range(9, 44):
                d = ((x - 40) / 10) ** 2 + ((y - 27) / 6) ** 2 + ((z - 26) / 17) ** 2
                if d <= 1:
                    aero(s, x, y, z, "white_envelope")

    # Two transverse gantries and docking arms connect roof, ship and floor.
    for z in (18, 34):
        for x in (22, 58):
            s.fill(x, 1, z, x, 18, z, "create:brass_casing")
        for x in range(23, 58):
            create(s, x, 18, z, "gantry_shaft", {
                "facing": "east", "part": "start" if x == 23 else "end" if x == 57 else "middle", "powered": "false",
            })
        create(s, 40, 18, z, "gantry_carriage", {"axis_along_first": "true", "facing": "east"})
        simulated(s, 40, 17, z, "rope_winch", {"axis_along_first": "true", "facing": "down"})
        for y in range(15, 17):
            s.set(40, y, z, "minecraft:chain")
    for z in (18, 34):
        for x1, x2 in ((20, 30), (50, 60)):
            for x in range(x1, x2 + 1):
                create(s, x, 24, z, "metal_girder")
            s.fill(x1, 1, z, x1, 23, z, "minecraft:stone_bricks")
            s.fill(x2, 21, z, x2, 24, z, "create:brass_casing")
    # An explicit dock connection shows where a completed ship enters the
    # factory logistics network and also ties the hull back to the east wall.
    simulated(s, 46, 13, 26, "docking_connector", {
        "extended": "false", "facing": "east", "powered": "false",
    })
    simulated(s, 47, 13, 26, "paired_docking_connector")
    for x in range(48, 59):
        create(s, x, 13, 26, "metal_girder")
    s.fill(58, 1, 26, 58, 13, 26, "create:brass_casing")

    # Multi-floor control tower overlooks the airship; its floors have an
    # actual inspection role instead of being an empty tall shell.
    for y in (7, 13, 19):
        s.fill(63, y, 14, 72, y, 28, "minecraft:polished_andesite")
        carve(s, 67, y, 20, 68, y, 21)
    for y in range(2, 22):
        scaffold(s, 67, y, 20, bottom=y == 2)
    # Full-height framed observation facade, tied into every floor slab.
    for y in (7, 13, 19, 23):
        for x in range(60, 64):
            for z in (14, 28):
                create(s, x, y, z, "metal_girder")
    for z in range(15, 28):
        for y in list(range(8, 13)) + list(range(14, 19)) + list(range(20, 23)):
            create(s, 60, y, z, "framed_glass")
        if z % 4 == 0:
            s.fill(60, 7, z, 60, 23, z, "create:metal_girder")
    create(s, 64, 14, 19, "brass_casing")
    create(s, 64, 15, 19, "display_board")
    create(s, 64, 14, 22, "brass_casing")
    create(s, 64, 15, 22, "gearbox", {"axis": "y"})
    s.fill(63, 14, 25, 63, 16, 25, "create:brass_casing")
    aero(s, 62, 16, 25, "mounted_potato_cannon", {
        "axis_along_first": "false", "blocked": "false", "facing": "west", "powered": "false",
    })

    # Ruined test bay supplies the site's past: scorched floor, blast crater,
    # broken transmission and a propeller rig abandoned after an accident.
    # Blast damage breaks walls, not the weatherproof roof deck.
    carve(s, 3, 4, 6, 12, 6, 16)
    carve(s, 13, 4, 6, 22, 6, 11)
    for x in range(7, 19):
        for z in range(10, 24):
            if (x - 13) ** 2 + (z - 17) ** 2 <= 22:
                s.set(x, 1, z, "minecraft:blackstone")
                if (x + z) % 4 == 0:
                    s.set(x, 2, z, "minecraft:magma_block")
    for x in (6, 7, 8, 13, 17, 18):
        shaft(s, x, 2, 22, axis="x", encased=x in (8, 17))
    s.fill(10, 2, 14, 10, 5, 14, "create:andesite_casing")
    aero(s, 10, 6, 14, "propeller_bearing", {"facing": "up"})
    aero(s, 10, 7, 14, "wooden_propeller", {"facing": "up", "reversed": "false"})
    aero(s, 16, 3, 20, "steam_vent", {
        "facing": "south", "powered": "false", "variant": "iron", "waterlogged": "false",
    })
    s.set(6, 2, 25, "minecraft:spruce_planks")
    s.chest(6, 3, 25)
    return s


def clockwork_dock_v2() -> Structure:
    """A watertight dock complex whose wings never overlap in plan or roof."""
    s = Structure("clockwork_dock", (81, 40, 70))

    # Independent volumes: shared walls are intentional two-block firewalls,
    # pierced only by the doors below.  This makes indoor roof intersections
    # impossible by construction.
    hangar = chamfered_footprint(14, 3, 66, 47, 4)
    assembly = chamfered_footprint(20, 48, 60, 68, 3)
    boiler = chamfered_footprint(2, 31, 13, 62, 2)
    warehouse = chamfered_footprint(67, 36, 78, 63, 2)
    control = chamfered_footprint(67, 9, 79, 32, 2)
    test_bay = chamfered_footprint(2, 6, 13, 28, 2)
    zones = [hangar, assembly, boiler, warehouse, control, test_bay]
    assert all(a.isdisjoint(b) for i, a in enumerate(zones) for b in zones[i + 1:])

    room_shell(s, hangar, 18, "minecraft:stone_bricks")
    room_shell(s, assembly, 13, "minecraft:tuff_bricks")
    room_shell(s, boiler, 12, "minecraft:bricks")
    room_shell(s, warehouse, 10, "minecraft:tuff_bricks")
    room_shell(s, control, 22, "minecraft:stone_bricks")
    room_shell(s, test_bay, 9, "minecraft:cracked_stone_bricks", windows=False)
    barrel_roof(s, 14, 3, 66, 47, 18, 14)
    hipped_roof(s, assembly, 14, rise=5)
    hipped_roof(s, boiler, 13, "minecraft:bricks", rise=4)
    hipped_roof(s, warehouse, 11, rise=4)
    hipped_roof(s, control, 23, "minecraft:cut_copper", rise=4)
    hipped_roof(s, test_bay, 10, rise=3)

    # Five deliberate internal connections; no accidental black corridors.
    for bounds in (
        (34, 2, 46, 46, 9, 49),   # hangar <-> assembly
        (12, 2, 41, 15, 5, 43),   # boiler fire door
        (65, 2, 41, 69, 6, 45),   # warehouse fire door
        (65, 3, 18, 69, 8, 24),   # control gallery
        (11, 2, 17, 15, 7, 23),   # test bay blast door
    ):
        carve(s, *bounds)

    # Closed paired fire doors at the boiler threshold. The surrounding brick
    # bulkhead remains intact above and beside them.
    s.fill(13, 2, 40, 14, 6, 44, "minecraft:bricks")
    carve(s, 13, 2, 41, 14, 3, 42)
    for z, hinge in ((41, "left"), (42, "right")):
        for y, half in ((2, "lower"), (3, "upper")):
            s.set(13, y, z, "create:brass_door", properties={
                "facing": "east", "half": half, "hinge": hinge, "open": "false", "visible": "true",
            })

    # Sliding hangar gate: massive leaves live in side pockets. Gantry rails
    # and counterweight towers explain how the clear opening moves.
    carve(s, 29, 2, 2, 51, 18, 5)
    for x in range(28, 53):
        create(s, x, 17, 4, "gantry_shaft", {
            "facing": "east", "part": "start" if x == 28 else "end" if x == 52 else "middle",
            "powered": "false",
        })
    for carriage_x in (31, 49):
        create(s, carriage_x, 17, 4, "gantry_carriage", {"axis_along_first": "true", "facing": "east"})
    for x1, x2 in ((27, 31), (49, 53)):
        s.fill(x1, 0, 3, x2, 18, 6, "minecraft:stone_bricks")
        carve(s, x1 + 1, 2, 3, x2 - 1, 16, 5)
        for x in range(x1 + 1, x2):
            for y in range(2, 17):
                s.set(x, y, 4, "create:metal_girder" if y % 4 == 2 or x in (x1 + 1, x2 - 1) else "minecraft:iron_bars")
    for x in (27, 53):
        s.fill(x, 1, 3, x, 21, 6, "create:andesite_casing")
        create(s, x, 22, 4, "metal_girder")
    # Stepped voussoirs and a keyed crown make the opening read as supported,
    # not as a rectangle cut from a blank wall.
    for inset, y in ((0, 18), (2, 19), (4, 20), (7, 21)):
        s.fill(29 + inset, y, 3, 51 - inset, y, 3, "minecraft:polished_blackstone_bricks")
    s.fill(39, 21, 2, 41, 23, 4, "create:brass_casing")
    for x in (23, 57):
        s.fill(x, 2, 3, x, 16, 3, "minecraft:polished_blackstone_bricks")
        for offset in range(1, 5):
            s.set(x + (offset if x == 23 else -offset), 16 + offset, 3, "minecraft:polished_blackstone_bricks")

    # Human entrance is a real brass double door under a stone lintel.
    carve(s, 38, 2, 67, 42, 6, 69)
    for x, hinge in ((39, "left"), (40, "right")):
        for y, half in ((2, "lower"), (3, "upper")):
            s.set(x, y, 68, "create:brass_door", properties={
                "facing": "south", "half": half, "hinge": hinge, "open": "false", "visible": "true",
            })
    s.fill(37, 2, 68, 37, 7, 68, "minecraft:stone_bricks")
    s.fill(42, 2, 68, 42, 7, 68, "minecraft:stone_bricks")
    s.fill(37, 7, 68, 42, 8, 68, "minecraft:stone_bricks")

    # A brass-and-glass gear rose relieves the enormous rear gable and marks
    # the assembly axis from outside without opening the weather envelope.
    for x in range(34, 47):
        for y in range(11, 18):
            dx, dy = x - 40, y - 14
            if dx * dx + (dy * 2) * (dy * 2) <= 36:
                create(s, x, y, 47, "framed_glass")
    for x, y in ((40, 10), (40, 18), (33, 14), (47, 14)):
        s.fill(x, y, 47, x, y, 47, "create:brass_casing")
    cog(s, 40, 14, 47, axis="z", large=True)

    # Nave rhythm and grounded crane supports.
    for z in range(9, 46, 6):
        for x in (14, 66):
            s.fill(x, 0, z, x, 21, z, "create:andesite_casing")
        for x in (19, 61):
            s.fill(x, 1, z, x, 8, z, "minecraft:stone_bricks")
            s.set(x, 9, z, "minecraft:lantern")
    for z in (18, 34):
        for x in (22, 58):
            s.fill(x, 1, z, x, 18, z, "create:brass_casing")
        for x in range(23, 58):
            create(s, x, 18, z, "gantry_shaft", {
                "facing": "east", "part": "start" if x == 23 else "end" if x == 57 else "middle", "powered": "false",
            })
        create(s, 40, 18, z, "gantry_carriage", {"axis_along_first": "true", "facing": "east"})
        simulated(s, 40, 17, z, "rope_winch", {"axis_along_first": "true", "facing": "down"})
        s.fill(40, 15, z, 40, 16, z, "minecraft:chain")

    # Assembly transept: one readable material path rather than repeated lines.
    s.barrel(23, 3, 56)
    for x in range(24, 57):
        shaft(s, x, 2, 56, axis="x", encased=x in (31, 49))
    for start, end in ((24, 33), (35, 44), (46, 56)):
        for x in range(start, end + 1):
            belt(s, x, 3, 56, part="start" if x == start else "end" if x == end else "middle")
    create(s, 34, 3, 56, "depot")
    create(s, 34, 5, 56, "mechanical_press", {"facing": "east"})
    create(s, 45, 3, 56, "basin", {"facing": "north"})
    create(s, 45, 5, 56, "mechanical_mixer")
    create(s, 57, 3, 56, "item_vault", {"axis": "x", "large": "true"})
    s.set(52, 2, 60, "create:andesite_casing")
    shaft(s, 52, 3, 60, axis="y", encased=True)
    create(s, 52, 4, 60, "mechanical_arm", {"ceiling": "false"})
    for x in (34, 45):
        for y in range(6, 11):
            shaft(s, x, y, 56, axis="y", encased=y == 10)
    for x in range(23, 58):
        create(s, x, 11, 56, "metal_girder")
    for x in (23, 57):
        s.fill(x, 2, 56, x, 10, 56, "create:brass_casing")
    s.fill(38, 2, 64, 42, 2, 67, "minecraft:polished_blackstone")
    create(s, 39, 3, 65, "display_board")
    create(s, 41, 3, 65, "display_board")
    s.set(40, 3, 64, "minecraft:bell")

    # Boiler house: two valid 3x3 heated tank stacks, a water header, engines,
    # flywheels and one supported transmission spine. Everything is inside the
    # fire-rated brick wing and follows an input -> heat -> steam -> work story.
    for bz in (36, 49):
        for x in range(5, 8):
            for z in range(bz, bz + 3):
                create(s, x, 2, z, "blaze_burner")
                for y in range(3, 8):
                    tank(s, x, y, z, bottom=y == 3, top=y == 7)
        create(s, 8, 6, bz + 1, "steam_engine", {"face": "ceiling", "facing": "east", "waterlogged": "false"})
        create(s, 9, 6, bz + 1, "flywheel", {"axis": "x"})
        shaft(s, 10, 6, bz + 1, axis="x", encased=True)
        create(s, 11, 6, bz + 1, "gearbox", {"axis": "y"})
        pipe(s, 4, 4, bz + 1)
        create(s, 4, 4, bz, "mechanical_pump", {"facing": "south"})
    for z in range(34, 58):
        pipe(s, 3, 4, z)
        shaft(s, 11, 6, z, axis="z", encased=z in (37, 50, 57))
    s.fill(3, 2, 57, 4, 3, 60, "minecraft:water")
    s.fill(3, 1, 57, 4, 1, 60, "create:copper_casing")
    for z in (37, 50):
        shaft(s, 10, 6, z, axis="x", encased=True)
        s.fill(11, 1, z, 11, 5, z, "create:andesite_casing")
    # Chimneys rise from supported masonry flues clear of the water header.
    for z in (33, 59):
        s.fill(9, 2, z, 10, 12, z + 1, "minecraft:bricks")
        s.fill(9, 13, z, 10, 19, z + 1, "create:copper_casing")
    for z in (40, 45, 54, 59):
        s.fill(3, 8, z, 11, 8, z, "create:metal_girder")
    # Walkable service gallery with repeated floor supports and a safety rail.
    for z in range(34, 59):
        s.fill(9, 8, z, 11, 8, z, "create:andesite_scaffolding")
        create(s, 11, 9, z, "metal_girder")
    for z in (34, 40, 47, 54, 58):
        s.fill(9, 1, z, 9, 7, z, "create:andesite_casing")
    for y in range(2, 9):
        scaffold(s, 10, y, 58, bottom=y == 2)
    s.set(10, 9, 58, "minecraft:lantern")

    # Warehouse racking and a dispatch vault stay within their own shell.
    for z in (41, 47, 53, 59):
        for x in (70, 74):
            create(s, x, 2, z, "item_vault", {"axis": "z", "large": "false"})
    s.barrel(76, 2, 59)

    # Multi-floor control annex, now outside the hangar roof volume.
    for y in (7, 13, 19):
        s.fill(69, y, 12, 77, y, 29, "minecraft:polished_andesite")
        carve(s, 72, y, 20, 73, y, 21)
    for y in range(2, 22):
        scaffold(s, 72, y, 20, bottom=y == 2)
    for z in range(14, 28):
        for y in list(range(8, 13)) + list(range(14, 19)) + list(range(20, 22)):
            create(s, 67, y, z, "framed_glass")
    create(s, 70, 14, 18, "display_board")
    create(s, 70, 14, 23, "gearbox", {"axis": "y"})
    s.fill(69, 14, 26, 69, 15, 26, "create:brass_casing")
    aero(s, 69, 16, 26, "mounted_potato_cannon", {
        "axis_along_first": "false", "blocked": "false", "facing": "west", "powered": "false",
    })

    # Grounded drydock cradles and a coherent incomplete airship.
    for z in (14, 22, 30, 38):
        s.fill(31, 1, z, 34, 9, z, "create:andesite_casing")
        s.fill(46, 1, z, 49, 9, z, "create:andesite_casing")
        s.fill(34, 10, z, 46, 10, z, "minecraft:stone_bricks")
    for z in range(11, 43):
        taper = min(z - 11, 42 - z)
        half = max(2, min(6, 2 + taper // 4))
        s.fill(40 - half, 11, z, 40 + half, 11, z, "minecraft:spruce_planks")
        for y in range(12, 16):
            s.set(40 - half, y, z, "minecraft:stripped_spruce_log")
            s.set(40 + half, y, z, "minecraft:stripped_spruce_log")
        s.fill(40 - half, 16, z, 40 + half, 16, z, "minecraft:deepslate_tiles")
    s.fill(40, 9, 9, 40, 11, 44, "minecraft:stripped_spruce_log")
    for x in range(30, 51):
        for y in range(21, 34):
            for z in range(9, 44):
                d = ((x - 40) / 10) ** 2 + ((y - 27) / 6) ** 2 + ((z - 26) / 17) ** 2
                if d <= 1:
                    aero(s, x, y, z, "white_envelope")
    for x in (38, 42):
        for z in (24, 28):
            aero(s, x, 12, z, "levitite")
            aero(s, x, 13, z, "adjustable_burner", {"powered": "false", "variant": "fire"})
    simulated(s, 40, 12, 13, "physics_assembler", {"face": "floor", "facing": "north"})
    simulated(s, 39, 12, 14, "navigation_table", {"facing": "up"})
    simulated(s, 40, 12, 14, "steering_wheel", {"facing": "north", "on_floor": "true", "waterlogged": "false"})
    simulated(s, 41, 12, 14, "throttle_lever", {"face": "floor", "facing": "north", "inverted": "false"})
    simulated(s, 37, 12, 30, "white_portable_engine", {"facing": "south", "lit": "false"})
    simulated(s, 43, 12, 30, "white_portable_engine", {"facing": "south", "lit": "false"})
    aero(s, 35, 13, 32, "propeller_bearing", {"facing": "west"})
    aero(s, 34, 13, 32, "wooden_propeller", {"facing": "west", "reversed": "false"})
    aero(s, 45, 13, 32, "propeller_bearing", {"facing": "east"})
    aero(s, 46, 13, 32, "wooden_propeller", {"facing": "east", "reversed": "true"})
    simulated(s, 46, 13, 26, "docking_connector", {"extended": "false", "facing": "east", "powered": "false"})
    simulated(s, 47, 13, 26, "paired_docking_connector")
    for x in range(48, 60):
        create(s, x, 13, 26, "metal_girder")
    s.fill(59, 1, 26, 59, 13, 26, "create:brass_casing")

    # Test bay: contained blast scar, broken rig and salvage chest.
    for x in range(4, 12):
        for z in range(10, 25):
            if (x - 8) ** 2 + (z - 17) ** 2 <= 16:
                s.set(x, 1, z, "minecraft:blackstone")
                if (x + z) % 5 == 0:
                    s.set(x, 2, z, "minecraft:magma_block")
    s.fill(8, 2, 15, 8, 5, 15, "create:andesite_casing")
    aero(s, 8, 6, 15, "propeller_bearing", {"facing": "up"})
    aero(s, 8, 7, 15, "wooden_propeller", {"facing": "up", "reversed": "false"})
    s.chest(5, 2, 25)
    return s


def bombed_outpost() -> Structure:
    s = Structure("bombed_outpost", (15, 8, 15))
    s.fill(0, 0, 0, 14, 0, 14, "minecraft:gravel")
    for x, z in ((1, 1), (1, 13), (13, 1), (13, 13)):
        s.fill(x, 1, z, x, 5, z, "create:copper_casing")
    # A deliberately broken Create line is both a target and a repair puzzle.
    for x in (2, 3, 4, 9, 10, 11):
        shaft(s, x, 1, 7, axis="x")
        belt(s, x, 2, 7, part="start" if x in (2, 9) else "end" if x in (4, 11) else "middle")
    create(s, 6, 3, 7, "mechanical_drill", {"facing": "east", "waterlogged": "false"})
    create(s, 7, 3, 7, "mechanical_saw", {
        "axis_along_first": "false", "facing": "east", "flipped": "false",
    })
    create(s, 4, 3, 10, "item_vault", {"axis": "z", "large": "false"})
    create(s, 5, 1, 6, "gearbox", {"axis": "x"})
    cog(s, 6, 1, 6, axis="x", large=True)
    s.set(7, 1, 7, "minecraft:iron_block")
    s.set(7, 2, 7, "minecraft:target")
    aero(s, 7, 4, 7, "mounted_potato_cannon", {
        "axis_along_first": "false", "blocked": "false", "facing": "north", "powered": "false",
    })
    aero(s, 10, 3, 4, "steam_vent", {
        "facing": "south", "powered": "false", "variant": "iron", "waterlogged": "false",
    })
    s.fill(3, 1, 3, 5, 1, 5, "minecraft:air")
    s.barrel(2, 2, 10)
    return s


def ruined_engine_works() -> Structure:
    s = Structure("ruined_engine_works", (19, 11, 19))
    s.fill(0, 0, 0, 18, 0, 18, "minecraft:tuff_bricks")
    for x, z in ((1, 1), (1, 17), (17, 1), (17, 17)):
        s.fill(x, 1, z, x, 9, z, "minecraft:deepslate_bricks")
        create(s, x, 10, z, "metal_girder")
    for x in range(3, 17, 4):
        scaffold(s, x, 2, 2)
        scaffold(s, x, 6, 2, bottom=True)
    s.fill(1, 1, 1, 17, 1, 17, "create:andesite_casing")
    # Ruined but legible processing chain.
    for x in range(4, 15):
        shaft(s, x, 2, 9, axis="x")
    for start, end in ((4, 6), (8, 10), (12, 14)):
        for x in range(start, end + 1):
            belt(s, x, 3, 9, part="start" if x == start else "end" if x == end else "middle")
    create(s, 7, 3, 9, "depot")
    create(s, 7, 5, 9, "mechanical_press", {"facing": "east"})
    create(s, 11, 3, 9, "basin", {"facing": "north"})
    create(s, 11, 5, 9, "mechanical_mixer")
    create(s, 14, 4, 9, "encased_fan", {"facing": "east"})
    for y in (3, 4, 5):
        tank(s, 4, y, 5, bottom=y == 3, top=y == 5)
    pipe(s, 5, 4, 5)
    create(s, 6, 4, 5, "steam_engine", {"face": "ceiling", "facing": "east", "waterlogged": "false"})
    create(s, 8, 4, 5, "flywheel", {"axis": "x"})
    for x in range(9, 14):
        shaft(s, x, 4, 5, axis="x", encased=x == 12)
    create(s, 15, 4, 5, "mechanical_saw", {
        "axis_along_first": "false", "facing": "east", "flipped": "false",
    })
    s.set(9, 4, 9, "minecraft:redstone_block")
    s.set(9, 5, 9, "minecraft:target")
    aero(s, 9, 7, 9, "white_envelope")
    aero(s, 9, 8, 9, "propeller_bearing", {"facing": "up"})
    s.chest(2, 2, 2)
    s.chest(16, 2, 16)
    return s


def buried_reliquary() -> Structure:
    s = Structure("buried_reliquary", (17, 11, 17))
    s.fill(0, 0, 0, 16, 0, 16, "minecraft:deepslate")
    s.fill(1, 1, 1, 15, 1, 15, "minecraft:deepslate_bricks")
    s.fill(1, 2, 1, 1, 8, 15, "minecraft:deepslate_bricks")
    s.fill(15, 2, 1, 15, 8, 15, "minecraft:deepslate_bricks")
    s.fill(1, 2, 1, 15, 8, 1, "minecraft:deepslate_bricks")
    s.fill(1, 2, 15, 15, 8, 15, "minecraft:deepslate_bricks")
    s.fill(1, 9, 1, 15, 9, 15, "minecraft:deepslate")
    s.fill(6, 2, 6, 10, 2, 10, "create:brass_casing")
    create(s, 8, 3, 8, "item_vault", {"axis": "z", "large": "true"})
    create(s, 8, 4, 8, "display_board")
    create(s, 8, 5, 8, "smart_chute", {"powered": "false"})
    for x, z in ((4, 4), (12, 4), (4, 12), (12, 12)):
        cog(s, x, 3, z, axis="y", large=True)
        create(s, x, 4, z, "andesite_casing")
        s.set(x, 5, z, "minecraft:lantern")
    shaft(s, 8, 2, 3, axis="y")
    shaft(s, 8, 2, 13, axis="y")
    create(s, 8, 2, 4, "mechanical_bearing", {"facing": "up"})
    aero(s, 8, 6, 8, "levitite_blend")
    s.chest(3, 3, 8)
    s.chest(13, 3, 8)
    return s


def skywarden_fort() -> Structure:
    s = Structure("skywarden_fort", (23, 10, 23))
    s.fill(0, 0, 0, 22, 0, 22, "minecraft:stone_bricks")
    s.fill(1, 1, 1, 21, 1, 21, "minecraft:polished_andesite")
    for x, z in ((1, 1), (1, 21), (21, 1), (21, 21)):
        s.fill(x, 1, z, x, 8, z, "create:brass_casing")
        create(s, x, 9, z, "metal_girder")
        aero(s, x, 5, z, "mounted_potato_cannon", {
            "axis_along_first": "false", "blocked": "false", "facing": "north" if z == 1 else "south", "powered": "false",
        })
    # Walkable walls with a synchronized transmission ring.
    for x in range(2, 21):
        s.set(x, 2, 1, "minecraft:stone_bricks")
        s.set(x, 2, 21, "minecraft:stone_bricks")
        shaft(s, x, 3, 1, axis="x")
        shaft(s, x, 3, 21, axis="x")
    for z in range(2, 21):
        s.set(1, 2, z, "minecraft:stone_bricks")
        s.set(21, 2, z, "minecraft:stone_bricks")
    for x in range(2, 21, 2):
        cog(s, x, 3, 2, axis="x", large=x in (6, 16))
        cog(s, x, 3, 20, axis="x", large=x in (6, 16))
    # Airship launch court: bearing, envelope and a powered-looking dock.
    s.fill(7, 2, 7, 15, 2, 15, "create:andesite_casing")
    create(s, 11, 3, 11, "mechanical_bearing", {"facing": "up"})
    aero(s, 11, 4, 11, "propeller_bearing", {"facing": "up"})
    for x in range(8, 15):
        aero(s, x, 6, 11, "white_envelope")
    aero(s, 11, 3, 8, "adjustable_burner", {"powered": "false", "variant": "fire"})
    create(s, 6, 3, 11, "water_wheel", {"facing": "east"})
    create(s, 5, 3, 11, "gearbox", {"axis": "x"})
    s.set(11, 3, 16, "minecraft:redstone_block")
    s.set(11, 4, 16, "minecraft:target")
    s.chest(3, 2, 11)
    return s


def signal_obelisk() -> Structure:
    s = Structure("signal_obelisk", (9, 16, 9))
    s.fill(0, 0, 0, 8, 0, 8, "minecraft:stone_bricks")
    for y in range(1, 12):
        width = 2 if y < 8 else 1
        s.fill(4 - width, y, 4 - width, 4 + width, y, 4 + width, "create:andesite_casing")
        shaft(s, 4, y, 4, axis="y")
        if y % 3 == 0:
            cog(s, 3, y, 4, axis="x", large=y == 9)
    create(s, 4, 11, 4, "windmill_bearing", {"facing": "up"})
    aero(s, 4, 12, 4, "propeller_bearing", {"facing": "up"})
    aero(s, 4, 13, 4, "wooden_propeller", {"facing": "up", "reversed": "false"})
    aero(s, 4, 14, 4, "steam_vent", {
        "facing": "north", "powered": "false", "variant": "gold", "waterlogged": "false",
    })
    aero(s, 4, 15, 4, "levitite")
    create(s, 2, 2, 2, "display_board")
    s.chest(2, 1, 6)
    return s


BUILDERS = [
    field_battery,
    ironclad_watchtower,
    clockwork_dock_v2,
    bombed_outpost,
    ruined_engine_works,
    buried_reliquary,
    skywarden_fort,
    signal_obelisk,
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    manifest: dict[str, dict[str, Any]] = {}
    for builder in BUILDERS:
        structure = epicize(builder())
        structure.save()
        path = OUT / f"{structure.name}.nbt"
        manifest[structure.name] = {
            "size": list(structure.size),
            "palette_entries": len(structure.palette),
            "block_entries": len(structure.blocks),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        }
    (ROOT / "tools" / "template_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
