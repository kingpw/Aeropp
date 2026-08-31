"""Validate generated structure-template NBT without Minecraft dependencies."""

from __future__ import annotations

import hashlib
import gzip
import json
import struct
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
# 与 Minecraft 1.21.1 StructureTemplateManager 的单数资源目录保持一致。
OUT = ROOT / "src" / "main" / "resources" / "data" / "aeropp_structures" / "structure"
LOOT_TABLE_DIR = ROOT / "src" / "main" / "resources" / "data" / "aeropp_structures" / "loot_table"
MANIFEST_PATH = ROOT / "tools" / "template_manifest.json"


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0
        self.depth = 0

    def take(self, size: int) -> bytes:
        result = self.data[self.pos : self.pos + size]
        if len(result) != size:
            raise ValueError("truncated NBT")
        self.pos += size
        return result

    def u8(self) -> int:
        return self.take(1)[0]

    def i8(self) -> int:
        return struct.unpack(">b", self.take(1))[0]

    def i16(self) -> int:
        return struct.unpack(">h", self.take(2))[0]

    def u16(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def i32(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def i64(self) -> int:
        return struct.unpack(">q", self.take(8))[0]

    def string(self) -> str:
        return self.take(self.u16()).decode("utf-8")

    def value(self, tag: int) -> Any:
        if tag == 1:
            return self.i8()
        if tag == 2:
            return self.i16()
        if tag == 3:
            return self.i32()
        if tag == 4:
            return self.i64()
        if tag == 5:
            return struct.unpack(">f", self.take(4))[0]
        if tag == 6:
            return struct.unpack(">d", self.take(8))[0]
        if tag == 7:
            return self.take(self.i32())
        if tag == 8:
            return self.string()
        if tag == 9:
            item_tag = self.u8()
            count = self.i32()
            values = []
            for index in range(count):
                try:
                    values.append(self.value(item_tag))
                except Exception as error:
                    raise ValueError(f"list item {index}/{count}, item tag {item_tag}, offset {self.pos}: {error}") from error
            return values
        if tag == 10:
            result = {}
            while True:
                child_tag = self.u8()
                if child_tag == 0:
                    return result
                name = self.string()
                try:
                    result[name] = (child_tag, self.value(child_tag))
                except Exception as error:
                    raise ValueError(f"compound field {name!r}, tag {child_tag}, offset {self.pos}: {error}") from error
        if tag == 11:
            return [self.i32() for _ in range(self.i32())]
        if tag == 12:
            return [self.i64() for _ in range(self.i32())]
        raise ValueError(f"unsupported tag {tag}")

    def root(self) -> dict[str, tuple[int, Any]]:
        if self.u8() != 10 or self.string() != "":
            raise ValueError("root is not an unnamed compound")
        result = self.value(10)
        if self.pos != len(self.data):
            raise ValueError("trailing bytes after root compound")
        return result


def validate(path: Path, expected_size: list[int]) -> dict[str, Any]:
    raw = path.read_bytes()
    try:
        data = gzip.decompress(raw)
        compressed = True
    except gzip.BadGzipFile:
        data = raw
        compressed = False
    reader = Reader(data)
    try:
        root = reader.root()
    except Exception as error:
        raise ValueError(f"{path.name}: parse failed at byte {reader.pos}: {error}") from error
    size_tag, size = root["size"]
    palette_tag, palette = root["palette"]
    blocks_tag, blocks = root["blocks"]
    entities_tag, entities = root["entities"]
    if size_tag != 9 or size != expected_size:
        raise ValueError(f"{path.name}: size mismatch: {size}")
    if palette_tag != 9 or not palette or palette[0]["Name"][1] != "minecraft:air":
        raise ValueError(f"{path.name}: palette must begin with air")
    for entry in palette:
        name = entry.get("Name")
        if not isinstance(name, tuple) or name[0] != 8 or ":" not in name[1]:
            raise ValueError(f"{path.name}: palette contains an invalid block name")
    if blocks_tag != 9 or entities_tag != 9 or entities:
        raise ValueError(f"{path.name}: invalid block/entity lists")
    if not compressed:
        raise ValueError(f"{path.name}: structure NBT must be GZIP compressed")
    for block in blocks:
        position = block["pos"][1]
        state = block["state"][1]
        if len(position) != 3 or any(not isinstance(coordinate, int) or coordinate < 0 or coordinate >= bound for coordinate, bound in zip(position, expected_size)) or not 0 <= state < len(palette):
            raise ValueError(f"{path.name}: invalid block entry")
        if "nbt" in block:
            nbt = block["nbt"][1]
            if "id" not in nbt:
                raise ValueError(f"{path.name}: block entity has no id")
            loot_table = nbt.get("LootTable")
            if loot_table and loot_table[0] == 8 and loot_table[1].startswith("aeropp_structures:"):
                relative = loot_table[1].split(":", 1)[1]
                if not (LOOT_TABLE_DIR / f"{relative}.json").exists():
                    raise ValueError(f"{path.name}: missing loot table {loot_table[1]}")
    return {
        "bytes": path.stat().st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "palette_entries": len(palette),
        "block_entries": len(blocks),
    }


def main() -> None:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    results = {}
    for name, metadata in manifest.items():
        results[name] = validate(OUT / f"{name}.nbt", metadata["size"])
        if results[name]["sha256"] != metadata["sha256"]:
            raise ValueError(f"{name}: checksum differs from manifest; regenerate the manifest")
    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
