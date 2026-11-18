#!/usr/bin/env python3
import json
import hashlib
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
lock = json.loads((root / "dependencies.lock.json").read_text())
failures = []
for name, dependency in lock["repositories"].items():
    path = root / lock["directory"] / name
    try:
        actual = subprocess.check_output(
            ["git", "-C", str(path), "rev-parse", "HEAD"], text=True
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        actual = "missing"
    expected = dependency["commit"]
    if actual != expected:
        failures.append(f"{name}: expected {expected}, found {actual}")
    for patch in dependency.get("patches", []):
        source = root / patch["path"]
        if hashlib.sha256(source.read_bytes()).hexdigest() != patch["sha256"]:
            failures.append(f"{name}: patch digest mismatch")
        elif subprocess.run(["git", "-C", str(path), "apply", "--reverse", "--check", str(source)],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            failures.append(f"{name}: required source patch is not applied")
if failures:
    raise SystemExit("Pinned library check failed:\n" + "\n".join(failures))
print(f"Verified {len(lock['repositories'])} pinned libraries.")
