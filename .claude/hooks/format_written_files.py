#!/usr/bin/env python3
"""PostToolUse hook (Write|Edit): format files created/edited by agents.

- json/yaml/yml/md/html/css/js/ts: prettier (via npx) if available
- xml (incl. pom.xml), java, sql, properties, others text: whitespace hygiene
  (strip trailing whitespace, single final newline, LF line endings)
Never fails the session: always exits 0.
"""
import json
import os
import shutil
import subprocess
import sys

PRETTIER = {".json", ".yaml", ".yml", ".md", ".html", ".css", ".js", ".ts"}
HYGIENE = {".java", ".xml", ".sql", ".properties", ".txt", ".gitignore", ".sh"}
SKIP_DIRS = {"target", ".git", ".idea", "node_modules", ".mvn"}


def hygiene(path: str) -> None:
    with open(path, "rb") as f:
        raw = f.read()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        return
    bom = text.startswith("\ufeff")
    text = text.lstrip("\ufeff").replace("\r\n", "\n")
    lines = [ln.rstrip(" \t") for ln in text.split("\n")]
    out = "\n".join(lines).rstrip("\n") + "\n"
    if bom:
        out = "\ufeff" + out
    if out.encode("utf-8") != raw:
        with open(path, "wb") as f:
            f.write(out.encode("utf-8"))


def main() -> int:
    try:
        data = json.load(sys.stdin)
        ti = data.get("tool_input", {}) or {}
        tr = data.get("tool_response", {}) or {}
        path = (tr.get("filePath") if isinstance(tr, dict) else None) or ti.get("file_path")
    except Exception:
        return 0
    if not path or not os.path.isfile(path):
        return 0
    parts = set(os.path.normpath(path).split(os.sep))
    if parts & SKIP_DIRS:
        return 0

    ext = os.path.splitext(path)[1].lower()
    try:
        if ext in PRETTIER and shutil.which("npx"):
            r = subprocess.run(["npx", "--yes", "prettier", "--write", path],
                               capture_output=True, timeout=60, shell=(os.name == "nt"))
            if r.returncode == 0:
                return 0
        if ext in PRETTIER | HYGIENE:
            hygiene(path)
    except Exception:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
