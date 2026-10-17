#!/usr/bin/env python3
"""Natural tab-removal sweep on R2: UI taps only. No BEGIN/END broadcast, no startRemovalTransition call.

Each case: fresh launch, set root mode / loader delay / root offset through the UI, then press
"Swap tab + toggle root" N times. The selected tab is always the one that gets removed."""
import argparse, json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--label', required=True)
p.add_argument('--modes', default='unmount,hide,wrap')
p.add_argument('--delays', default='0,100,450,1000,3000')
p.add_argument('--offsets', default='-450,-100,-16,0,16,100,450')
p.add_argument('--cycles', type=int, default=3)
p.add_argument('--action', default='SWAP TAB + TOGGLE ROOT')
p.add_argument('--out', type=Path, required=True)
a = p.parse_args()
A = ['adb', '-s', a.serial]
PKG = 'com.screens3777repro'
a.out.mkdir(parents=True, exist_ok=True)


def sh(*x, check=True):
    r = subprocess.run(A + ['shell', *x], capture_output=True, text=True, errors='replace')
    if check and r.returncode:
        raise RuntimeError(r.stderr)
    return r.stdout


def nodes():
    sh('uiautomator', 'dump', '/sdcard/r2.xml')
    return [n.attrib for n in ET.fromstring(sh('cat', '/sdcard/r2.xml')).iter('node') if n.get('package') == PKG]


def find(pred, tries=12):
    ns = []
    for _ in range(tries):
        ns = nodes()
        hit = [n for n in ns if n.get('text') and pred(n['text'])]
        if hit:
            return hit[0], ns
        time.sleep(0.4)
    raise RuntimeError(f'not found; texts={[n["text"] for n in ns if n.get("text")]}')


def tap_node(n, wait=0.6):
    b = list(map(int, re.findall(r'\d+', n['bounds'])))
    sh('input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
    time.sleep(wait)


def tap(label, wait=0.6):
    tap_node(find(lambda t: t == label)[0], wait)


def cycle_to(prefix, value):
    for _ in range(10):
        n, _ = find(lambda t: t.startswith(prefix))
        if n['text'] == f'{prefix}{value}':
            return
        tap_node(n, 0.4)
    raise RuntimeError(f'cannot set {prefix}{value}')


results = []
for mode in a.modes.split(','):
    for delay in a.delays.split(','):
        for offset in a.offsets.split(','):
            name = f'{a.label}-{mode}-d{delay}-o{offset}'
            sh('am', 'force-stop', PKG)
            sh('logcat', '-c')
            sh('am', 'start', '-W', '-n', PKG + '/.MainActivity')
            time.sleep(1.5)
            tap('NATIVE TAB REMOVAL AND REMOUNT', 1.2)
            pid = sh('pidof', PKG).strip()
            cycle_to('ROOT MODE: ', mode.upper())
            cycle_to('ROOT OFFSET: ', f'{offset} MS')
            delay_btn = [n for n in nodes() if n.get('text') == f'{delay} MS']
            if delay_btn and delay_btn[0].get('enabled') == 'true':
                tap_node(delay_btn[0])
            find(lambda t: t == f'Loader delay: {delay} ms')
            done = 0
            for c in range(a.cycles):
                try:
                    tap(a.action, 0.1)
                except RuntimeError:
                    break
                time.sleep((int(delay) + abs(int(offset))) / 1000 + 1.2)
                if sh('pidof', PKG, check=False).strip() != pid:
                    break
                done += 1
            log = subprocess.run(A + ['logcat', '-d', '-v', 'threadtime'], capture_output=True, text=True, errors='replace').stdout
            (a.out / f'{name}.log').write_text(log)
            alive = sh('pidof', PKG, check=False).strip() == pid
            usable = False
            if alive:
                try:
                    find(lambda t: t.startswith('Visible route:'), 6)
                    usable = True
                except Exception:
                    pass
            fatal_cause = re.search(r'Caused by: (?:java\.lang\.)?(\w+Exception: [^\n]+)', log[log.find('FATAL EXCEPTION'):]) if 'FATAL EXCEPTION' in log else None
            r = {
                'trial': name, 'root_mode': mode, 'loader_delay_ms': int(delay), 'root_offset_ms': int(offset),
                'cycles_completed': done, 'forced_transition': False,
                'parent_exception': 'already has a parent' in log,
                'fatal': 'FATAL EXCEPTION' in log,
                'fatal_cause': fatal_cause.group(1).strip() if fatal_cause else None, 'process_alive': alive, 'route_visible': usable,
            }
            results.append(r)
            print(json.dumps(r), flush=True)
            (a.out / f'{a.label}.json').write_text(json.dumps(results, indent=2))
