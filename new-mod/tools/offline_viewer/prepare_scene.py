"""Convert a structure NBT to renderer JSON and expose bundled mod assets."""

from __future__ import annotations

import argparse
import gzip
import json
import sys
import zipfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from validate_templates import Reader  # noqa: E402


def value(field):
    return field[1] if isinstance(field, tuple) else field


def find_one(folder: Path, pattern: str) -> Path:
    matches = sorted(folder.glob(pattern))
    if not matches:
        raise FileNotFoundError(f"No file matched {pattern!r} in {folder}")
    return matches[-1]


def extract_nested(bundle: Path, cache: Path, marker: str) -> Path:
    with zipfile.ZipFile(bundle) as archive:
        names = [name for name in archive.namelist() if name.startswith("META-INF/jarjar/") and marker in name and name.endswith(".jar")]
        if len(names) != 1:
            raise ValueError(f"Expected one nested {marker} jar, found {names}")
        data = archive.read(names[0])
    target = cache / f"{marker}.jar"
    if not target.exists() or target.read_bytes() != data:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    return target


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("template", type=Path)
    parser.add_argument("scene", type=Path)
    parser.add_argument("--instance", type=Path, required=True)
    args = parser.parse_args()

    root = Reader(gzip.decompress(args.template.read_bytes())).root()
    palette = value(root["palette"])
    blocks = []
    for entry in value(root["blocks"]):
        state = palette[value(entry["state"])]
        props = {key: value(item) for key, item in value(state.get("Properties", (10, {}))).items()}
        block = {"id": value(state["Name"]), "pos": value(entry["pos"])}
        if props:
            block["properties"] = props
        blocks.append(block)

    args.scene.parent.mkdir(parents=True, exist_ok=True)
    args.scene.write_text(json.dumps({"size": value(root["size"]), "blocks": blocks}, separators=(",", ":")), encoding="utf-8")

    mods = args.instance / "mods"
    bundle = find_one(mods, "*aeronautics-bundled*.jar")
    cache = Path(__file__).resolve().parent / "cache"
    packs = [
        str(extract_nested(bundle, cache, "simulated")),
        str(extract_nested(bundle, cache, "aeronautics-neoforge")),
        str(find_one(mods, "*create-1.21.1-*.jar")),
        str(args.instance / f"{args.instance.name}.jar"),
    ]
    packs_path = args.scene.with_suffix(".packs.json")
    packs_path.write_text(json.dumps(packs, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"scene": str(args.scene), "blocks": len(blocks), "packs": packs}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
