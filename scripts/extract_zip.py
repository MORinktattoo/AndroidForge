#!/usr/bin/env python3
"""AndroidForge — Extract Source ZIP.

Extracts the source ZIP to a clean workspace and writes the extracted root
directory to $GITHUB_OUTPUT.

The script strips macOS metadata directories (`__MACOSX`) and unwraps a
top-level single directory if present (so e.g. `MyProject-1.2.3/` becomes
the workspace root when it's the only top-level entry).
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parent.parent


def extract(zip_path: Path, dest_dir: Path) -> Path:
    dest_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "r") as zf:
        # Filter out __MACOSX entries
        members = [m for m in zf.infolist() if not m.filename.startswith("__MACOSX") and "/.DS_Store" not in m.filename]
        zf.extractall(dest_dir, members=members)

    # Remove any leftover __MACOSX dirs on disk
    for macosx in dest_dir.rglob("__MACOSX"):
        if macosx.is_dir():
            shutil.rmtree(macosx, ignore_errors=True)

    # Python's zipfile.extractall() does NOT preserve the Unix execute bit
    # on shell scripts (e.g. `gradlew`). Without this, running `./gradlew`
    # later fails with PermissionError. We restore the +x bit on every
    # shell script and binary we can recognise by extension/name.
    for p in dest_dir.rglob("*"):
        if not p.is_file():
            continue
        name = p.name
        if name == "gradlew" or name == "gradlew.bat" or name.endswith(".sh") or name == "flutter" or name == "dart":
            try:
                cur = p.stat().st_mode
                p.chmod(cur | 0o111)  # set +x for user/group/other
            except Exception:
                pass

    # If the archive contains exactly one top-level directory, return that.
    top = [p for p in dest_dir.iterdir() if not p.name.startswith(".")]
    if len(top) == 1 and top[0].is_dir():
        return top[0]
    return dest_dir


def main() -> int:
    parser = argparse.ArgumentParser(description="AndroidForge ZIP extraction")
    parser.add_argument("--zip", required=True, help="Path to the source ZIP")
    parser.add_argument("--dest-dir", default=None, help="Where to extract (default: <repo>/.androidforge/workspace)")
    args = parser.parse_args()

    zip_path = Path(args.zip).resolve()
    if not zip_path.exists():
        print(f"ERROR: ZIP not found: {zip_path}", file=sys.stderr)
        return 1

    dest_dir = Path(args.dest_dir) if args.dest_dir else (REPO_ROOT / ".androidforge" / "workspace")
    # Clean workspace
    if dest_dir.exists():
        shutil.rmtree(dest_dir, ignore_errors=True)
    dest_dir.mkdir(parents=True, exist_ok=True)

    project_root = extract(zip_path, dest_dir)

    # Apply the maintained Mor Ink Soundtrack UI source to this exact project archive.
    # This keeps the build using the redesigned Kotlin UI without changing other uploaded projects.
    override_file = REPO_ROOT / "overrides" / "MorInkSoundtrack" / "MainActivity.kt"
    listener_override = REPO_ROOT / "overrides" / "MorInkSoundtrack" / "NowPlayingListener.kt"
    target_file = project_root / "app" / "src" / "main" / "java" / "com" / "morink" / "soundtrack" / "MainActivity.kt"
    listener_file = project_root / "app" / "src" / "main" / "java" / "com" / "morink" / "soundtrack" / "NowPlayingListener.kt"
    # Apply maintained Mor Ink overrides only to the matching native app source.
    if target_file.is_file() and listener_file.is_file():
        if override_file.is_file():
            shutil.copy2(override_file, target_file)
            print("Applied Mor Ink Soundtrack MainActivity UI override.")
        if listener_override.is_file():
            shutil.copy2(listener_override, listener_file)
            print("Applied Mor Ink Soundtrack Now Playing notification parser override.")

    print(json.dumps({
        "zip_path": str(zip_path),
        "extract_root": str(project_root),
        "dest_dir": str(dest_dir),
    }, indent=2))

    gh_output = os.environ.get("GITHUB_OUTPUT")
    if gh_output:
        with open(gh_output, "a", encoding="utf-8") as f:
            f.write(f"project_root={project_root}\n")
            f.write(f"extract_root={project_root}\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
