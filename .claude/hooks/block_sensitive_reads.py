#!/usr/bin/env python3
"""PreToolUse hook: block agents from reading sensitive files.

Exit 2 + stderr message = tool call is blocked and the message goes to the agent.
"""
import json
import re
import sys

# Basenames / path fragments considered sensitive.
SENSITIVE = [
    r"(^|[\/])\.env(\.[^\/\s]+)?$",          # .env, .env.local, .env.prod ...
    r"\.(pem|key|p12|pfx|jks|keystore|crt|cer|asc|gpg|kdbx)$",
    r"(^|[\/])id_(rsa|dsa|ecdsa|ed25519)(\.pub)?$",
    r"(^|[\/])\.(ssh|aws|gnupg|kube|docker)[\/]",
    r"(^|[\/])\.(netrc|npmrc|pypirc|pgpass|git-credentials)$",
    r"(^|[\/])(credentials?|secrets?)([._-][^\/]*)?$",
    r"(^|[\/])[^\/]*(credentials?|secrets?)[^\/]*\.(json|ya?ml|properties|txt|xml|env)$",
    r"(^|[\/])application-(local|secret|secrets|prod)[^\/]*\.(ya?ml|properties)$",
    r"(^|[\/])settings\.local\.json$",
]
# Templates are safe to read.
ALLOWED = re.compile(r"\.(example|sample|template|dist)$", re.I)

SENSITIVE_RE = [re.compile(p, re.I) for p in SENSITIVE]


def is_sensitive(path: str) -> bool:
    if not path:
        return False
    p = path.strip().strip("\"'").replace("\\", "/")
    if ALLOWED.search(p):
        return False
    return any(r.search(p) for r in SENSITIVE_RE)


def bash_tokens(cmd: str):
    return re.split(r"[\s;|&<>()=]+", cmd)


def main() -> int:
    try:
        data = json.load(sys.stdin)
    except Exception:
        return 0  # never break the session on bad input

    tool = data.get("tool_name", "")
    ti = data.get("tool_input", {}) or {}
    candidates = []

    if tool == "Read":
        candidates.append(ti.get("file_path", ""))
    elif tool in ("Grep", "Glob"):
        candidates += [ti.get("path", ""), ti.get("glob", ""), ti.get("pattern", "")] if tool == "Grep" \
            else [ti.get("path", ""), ti.get("pattern", "")]
    elif tool in ("Bash", "PowerShell"):
        candidates += bash_tokens(ti.get("command", ""))

    for c in candidates:
        if is_sensitive(c):
            print(
                f"Blocked by project hook: '{c}' looks like a sensitive file "
                "(env/secret/key/credential). Do not read it; ask the user for the "
                "specific non-secret values you need, or use a .example/.template file.",
                file=sys.stderr,
            )
            return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
