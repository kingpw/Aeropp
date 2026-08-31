"""Check the Create/Aeronautics states used by the structure templates.

This is deliberately a small, dependency-free smoke test. It does not start
Minecraft, but it catches the most expensive failure mode for data-only
buildings: a typo in a block ID or a block-state property that cannot exist in
the installed Create/Aeronautics version.
"""

from __future__ import annotations

import argparse
import gzip
import io
import json
import zipfile
from pathlib import Path
from typing import Any

from validate_templates import Reader


ROOT = Path(__file__).resolve().parents[1]
TEMPLATE_DIR = ROOT / "src" / "main" / "resources" / "data" / "aeropp_structures" / "structure"
REQUIRED_CREATE = {
    "create:andesite_belt_funnel", "create:andesite_casing", "create:andesite_encased_shaft",
    "create:andesite_scaffolding", "create:basin", "create:belt", "create:brass_casing",
    "create:brass_scaffolding", "create:chute", "create:cogwheel", "create:copper_casing", "create:depot",
    "create:encased_fan", "create:fluid_pipe", "create:fluid_tank", "create:flywheel",
    "create:gantry_carriage", "create:gantry_shaft", "create:gearbox", "create:item_vault",
    "create:large_cogwheel", "create:mechanical_bearing", "create:mechanical_drill",
    "create:mechanical_arm", "create:mechanical_mixer", "create:mechanical_press", "create:mechanical_saw",
    "create:metal_girder", "create:shaft", "create:smart_chute", "create:steam_engine",
    "create:water_wheel", "create:windmill_bearing",
}
REQUIRED_AERO = {
    "aeronautics:adjustable_burner", "aeronautics:levitite", "aeronautics:levitite_blend",
    "aeronautics:mounted_potato_cannon", "aeronautics:propeller_bearing",
    "aeronautics:steam_vent", "aeronautics:white_envelope", "aeronautics:wooden_propeller",
}
REQUIRED_SIMULATED = {
    "simulated:docking_connector", "simulated:navigation_table",
    "simulated:paired_docking_connector", "simulated:physics_assembler",
    "simulated:rope_winch", "simulated:steering_wheel",
    "simulated:throttle_lever", "simulated:white_portable_engine",
}


def palette(path: Path) -> list[dict[str, Any]]:
    return Reader(gzip.decompress(path.read_bytes())).root()["palette"][1]


def properties(entry: dict[str, Any]) -> dict[str, str]:
    field = entry.get("Properties")
    if not field:
        return {}
    return {name: value[1] for name, value in field[1].items()}


def block_names() -> tuple[set[str], dict[str, set[tuple[tuple[str, str], ...]]]]:
    names: set[str] = set()
    states: dict[str, set[tuple[tuple[str, str], ...]]] = {}
    for path in sorted(TEMPLATE_DIR.glob("*.nbt")):
        for entry in palette(path):
            name = entry["Name"][1]
            names.add(name)
            states.setdefault(name, set()).add(tuple(sorted(properties(entry).items())))
    return names, states


def placed_blocks(path: Path) -> list[tuple[tuple[int, int, int], str, dict[str, str]]]:
    """Return placed blocks with resolved palette states for topology checks."""
    root = Reader(gzip.decompress(path.read_bytes())).root()
    palette_entries = root["palette"][1]
    result = []
    for entry in root["blocks"][1]:
        position = tuple(entry["pos"][1])
        state = palette_entries[entry["state"][1]]
        result.append((position, state["Name"][1], properties(state)))
    return result


def check_topology(path: Path) -> dict[str, int]:
    """Catch visually plausible but non-connectable Create layouts."""
    blocks = placed_blocks(path)
    by_pos = {position: (name, props) for position, name, props in blocks}
    belts = [(position, props) for position, name, props in blocks if name == "create:belt"]
    seen: set[tuple[int, int, int]] = set()
    runs = 0
    for position, props in belts:
        if position in seen:
            continue
        facing = props.get("facing", "east")
        axis = 0 if facing in {"east", "west"} else 2
        component = {position}
        frontier = [position]
        while frontier:
            current = frontier.pop()
            for delta in (-1, 1):
                adjacent = list(current)
                adjacent[axis] += delta
                adjacent = tuple(adjacent)
                adjacent_state = by_pos.get(adjacent)
                if adjacent_state and adjacent_state[0] == "create:belt" and adjacent not in component:
                    component.add(adjacent)
                    frontier.append(adjacent)
        seen.update(component)
        runs += 1
        if len(component) > 20:
            raise SystemExit(f"{path.name}: belt run exceeds Create's 20-block limit ({len(component)})")
    for position, props in belts:
        facing = props.get("facing", "east")
        axis = 0 if facing in {"east", "west"} else 2
        neighbors = 0
        for delta in (-1, 1):
            adjacent = list(position)
            adjacent[axis] += delta
            if by_pos.get(tuple(adjacent), (None, {}))[0] == "create:belt":
                neighbors += 1
        expected = {"start": 1, "end": 1, "middle": 2}.get(props.get("part"), 0)
        if expected and neighbors != expected:
            raise SystemExit(f"{path.name}: belt {position} marked {props.get('part')} but has {neighbors} belt neighbours")

    processing_pairs = 0
    for position, name, _ in blocks:
        if name not in {"create:mechanical_press", "create:mechanical_mixer"}:
            continue
        support = (position[0], position[1] - 2, position[2])
        expected = "create:depot" if name.endswith("press") else "create:basin"
        if by_pos.get(support, (None, {}))[0] != expected:
            raise SystemExit(f"{path.name}: {name} at {position} is not two blocks above {expected}")
        processing_pairs += 1

    crane_pairs = 0
    for position, name, _ in blocks:
        if name != "create:mechanical_arm":
            continue
        support = (position[0], position[1] - 1, position[2])
        if by_pos.get(support, (None, {}))[0] not in {"create:shaft", "create:andesite_encased_shaft", "create:gearbox"}:
            raise SystemExit(f"{path.name}: mechanical arm at {position} has no vertical Create drive below it")
        crane_pairs += 1
    grounded_components = 0
    if path.stem == "clockwork_dock":
        # The flagship dock may be cantilevered locally, but it must not contain
        # any wholly floating island. Every six-neighbour component has to reach
        # the structure's foundation layer.
        remaining = set(by_pos)
        while remaining:
            start = remaining.pop()
            component = {start}
            frontier = [start]
            while frontier:
                x, y, z = frontier.pop()
                for adjacent in (
                    (x + 1, y, z), (x - 1, y, z), (x, y + 1, z),
                    (x, y - 1, z), (x, y, z + 1), (x, y, z - 1),
                ):
                    if adjacent in remaining:
                        remaining.remove(adjacent)
                        component.add(adjacent)
                        frontier.append(adjacent)
            if not any(y == 0 for _, y, _ in component):
                sample = min(component)
                raise SystemExit(
                    f"{path.name}: floating component of {len(component)} blocks near {sample}"
                )
            grounded_components += 1
        if len(blocks) < 30000:
            raise SystemExit(f"{path.name}: flagship dock is too sparse ({len(blocks)} blocks)")
    return {
        "belt_runs": runs,
        "processing_pairs": processing_pairs,
        "crane_pairs": crane_pairs,
        "grounded_components": grounded_components,
    }


def _record_blockstate(raw: bytes, entry_name: str, names: set[str], state_pairs: dict[str, set[tuple[tuple[str, str], ...]]]) -> None:
    if not (entry_name.startswith("assets/") and "/blockstates/" in entry_name and entry_name.endswith(".json")):
        return
    namespace, _, rest = entry_name[7:].partition("/blockstates/")
    name = f"{namespace}:{rest[:-5]}"
    names.add(name)
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return
    variants = data.get("variants")
    if isinstance(variants, dict):
        for key in variants:
            pairs = tuple(sorted(
                tuple(field.split("=", 1)) for field in key.split(",") if "=" in field
            ))
            state_pairs.setdefault(name, set()).add(pairs)


def installed_blockstates(mods_dir: Path) -> tuple[set[str], dict[str, set[tuple[tuple[str, str], ...]]]]:
    """Read blockstate assets from the actual jars without loading Minecraft."""
    result: set[str] = set()
    state_pairs: dict[str, set[tuple[tuple[str, str], ...]]] = {}
    for jar in mods_dir.glob("*.jar"):
        try:
            with zipfile.ZipFile(jar) as outer:
                for entry in outer.namelist():
                    if entry.startswith("assets/") and "/blockstates/" in entry and entry.endswith(".json"):
                        _record_blockstate(outer.read(entry), entry, result, state_pairs)
                    if entry.endswith(".jar") and entry.startswith("META-INF/jarjar/"):
                        try:
                            nested = zipfile.ZipFile(io.BytesIO(outer.read(entry)))
                        except zipfile.BadZipFile:
                            continue
                        with nested:
                            for nested_entry in nested.namelist():
                                if nested_entry.startswith("assets/") and "/blockstates/" in nested_entry and nested_entry.endswith(".json"):
                                    _record_blockstate(nested.read(nested_entry), nested_entry, result, state_pairs)
        except (OSError, zipfile.BadZipFile):
            continue
    return result, state_pairs


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", type=Path, help="optional actual mods directory")
    args = parser.parse_args()
    names, states = block_names()
    missing = sorted((REQUIRED_CREATE | REQUIRED_AERO | REQUIRED_SIMULATED) - names)
    if missing:
        raise SystemExit("template palette is missing required integration blocks: " + ", ".join(missing))

    def require_keys(block: str, keys: set[str]) -> None:
        actual = set().union(*(dict(state).keys() for state in states.get(block, set())))
        if not keys <= actual:
            raise SystemExit(f"{block} is missing state keys: {sorted(keys - actual)}")

    require_keys("create:belt", {"facing", "part", "slope", "waterlogged"})
    require_keys("create:andesite_belt_funnel", {"facing", "powered", "shape", "waterlogged"})
    require_keys("create:fluid_tank", {"bottom", "shape", "top"})
    require_keys("create:steam_engine", {"face", "facing", "waterlogged"})
    require_keys("create:mechanical_arm", {"ceiling"})
    require_keys("aeronautics:mounted_potato_cannon", {"axis_along_first", "blocked", "facing", "powered"})
    require_keys("aeronautics:wooden_propeller", {"facing", "reversed"})
    require_keys("simulated:physics_assembler", {"face", "facing"})
    require_keys("simulated:steering_wheel", {"facing", "on_floor", "waterlogged"})
    require_keys("simulated:throttle_lever", {"face", "facing", "inverted"})
    require_keys("simulated:docking_connector", {"extended", "facing", "powered"})
    require_keys("simulated:rope_winch", {"axis_along_first", "facing"})
    topology = {}
    for path in sorted(TEMPLATE_DIR.glob("*.nbt")):
        topology[path.stem] = check_topology(path)
    result = {
        "templates": len(list(TEMPLATE_DIR.glob("*.nbt"))),
        "palette_blocks": sorted(names),
        "topology": topology,
    }
    if args.mods:
        available, available_states = installed_blockstates(args.mods)
        required = REQUIRED_CREATE | REQUIRED_AERO | REQUIRED_SIMULATED
        absent = sorted(required - available)
        if absent:
            raise SystemExit("installed mods do not expose required blockstates: " + ", ".join(absent))
        for name, template_states in states.items():
            valid_states = available_states.get(name)
            if not valid_states:
                continue  # multipart blockstates (e.g. fluid_pipe) have no variant keys.
            for template_state in template_states:
                for key, value in template_state:
                    if not any(dict(valid).get(key) == value for valid in valid_states):
                        raise SystemExit(f"{name} uses unsupported state {key}={value}")
        result["installed_blockstates"] = len(available)
        result["mods_dir"] = str(args.mods)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
