#!/usr/bin/env python3
"""Apply/reverse only the documented comparison patch; preserve unrelated edits."""
import argparse, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
repo=root/'vendor/react-native-screens'
p=argparse.ArgumentParser();p.add_argument('variant',choices=['before','after']);args=p.parse_args()
def git(*a,check=True):return subprocess.run(['git','-C',str(repo),*a],check=check,capture_output=True,text=True)
expected='561feef37a2713bafd3652f8ed140b452e26ccbb'
assert git('rev-parse','HEAD').stdout.strip()==expected,'Initialize the pinned submodule first.'
for filename,want in [('tabs-prerequisites.patch',True),('header-removal.patch',args.variant=='after')]:
 patch=str(root/'patches'/filename)
 applies=git('apply','--check',patch,check=False).returncode==0
 reverses=git('apply','--reverse','--check',patch,check=False).returncode==0
 if want and applies:git('apply',patch)
 elif not want and reverses:git('apply','--reverse',patch)
 elif not ((want and reverses) or (not want and applies)):
  raise SystemExit(f'{filename}: source differs from both expected states; no files reset.')
print(args.variant)
