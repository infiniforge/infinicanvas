#!/usr/bin/env python3
"""Apply checked-in source patches to exact pinned library revisions, without overwriting local edits."""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[1]
lock=json.loads((ROOT/'dependencies.lock.json').read_text())
for name,dependency in lock['repositories'].items():
    repo=ROOT/lock['directory']/name
    actual=subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip()
    if actual!=dependency['commit']:raise SystemExit(f'{name}: initialize the pinned submodule revision before applying patches')
    for entry in dependency.get('patches',[]):
        patch=ROOT/entry['path']
        if hashlib.sha256(patch.read_bytes()).hexdigest()!=entry['sha256']:raise SystemExit(f'Patch digest mismatch: {patch}')
        def run(*args):return subprocess.run(['git','-C',str(repo),'apply',*args,str(patch)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        if run('--reverse','--check').returncode==0:continue
        checked=run('--check')
        if checked.returncode:raise SystemExit(f'Cannot safely apply {patch.name}; the library has overlapping local changes.\n'+checked.stderr)
        applied=run()
        if applied.returncode:raise SystemExit(applied.stderr)
        print(f'Applied {patch.name} to {name}')
