"""One-command offline NBT render for Aeropp structure review."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("template", type=Path)
    parser.add_argument("--instance", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "build" / "offline-viewer" / "renders")
    args = parser.parse_args()

    scene = ROOT / "build" / "offline-viewer" / f"{args.template.stem}.json"
    subprocess.run([
        sys.executable, str(HERE / "prepare_scene.py"), str(args.template), str(scene),
        "--instance", str(args.instance),
    ], check=True)

    nodes = sorted((HERE / "node_modules" / ".pnpm").glob("node@22*/node_modules/node/bin/node.exe"))
    if not nodes:
        raise FileNotFoundError("Node 22 runtime missing; run pnpm install in tools/offline_viewer")
    subprocess.run([
        str(nodes[-1]), str(HERE / "render_structure.mjs"), str(scene),
        str(scene.with_suffix(".packs.json")), str(args.output),
    ], check=True)


if __name__ == "__main__":
    main()
