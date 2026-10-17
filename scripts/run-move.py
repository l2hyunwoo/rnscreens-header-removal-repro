#!/usr/bin/env python3
"""Natural reorder path on R3: UI taps only (navigate with getId moves an existing route to top).
No BEGIN/END broadcast, no startRemovalTransition call."""
import argparse, json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
p = argparse.ArgumentParser()
p.add_argument('--serial', required=True); p.add_argument('--label', required=True)
p.add_argument('--entry', default='MOVE EXISTING SCREEN TO TOP'); p.add_argument('--name', default='ITEM')
p.add_argument('--repeat', type=int, default=1); p.add_argument('--hops', type=int, default=4)
p.add_argument('--wait', type=float, default=1.5); p.add_argument('--out', type=Path, required=True)
a = p.parse_args(); A = ['adb', '-s', a.serial]; PKG = 'com.screens3777repro'; a.out.mkdir(parents=True, exist_ok=True)
def sh(*x, check=True):
    r = subprocess.run(A + ['shell', *x], capture_output=True, text=True, errors='replace')
    if check and r.returncode: raise RuntimeError(r.stderr)
    return r.stdout
def nodes():
    sh('uiautomator', 'dump', '/sdcard/r3.xml')
    return [n.attrib for n in ET.fromstring(sh('cat', '/sdcard/r3.xml')).iter('node') if n.get('package') == PKG]
def tap(label, wait):
    for _ in range(10):
        try: ns = nodes()
        except Exception: time.sleep(0.5); continue
        hit = [n for n in ns if n.get('text') == label]
        if hit:
            b = list(map(int, re.findall(r'\d+', hit[-1]['bounds'])))
            sh('input', 'tap', str((b[0]+b[2])//2), str((b[1]+b[3])//2)); time.sleep(wait); return True
        time.sleep(0.4)
    return False
results = []
for rep in range(a.repeat):
    name = f'{a.label}-{rep:02d}'
    sh('am', 'force-stop', PKG); sh('logcat', '-c'); sh('am', 'start', '-W', '-n', PKG + '/.MainActivity'); time.sleep(1.5)
    tap(a.entry, 1.5); pid = sh('pidof', PKG).strip(); hops = 0
    for h in range(a.hops):
        target = 'B' if h % 2 == 0 else 'A'
        if not tap(f'OPEN {a.name} {target}', a.wait) or sh('pidof', PKG, check=False).strip() != pid: break
        hops += 1
    time.sleep(1)
    log = subprocess.run(A + ['logcat', '-d', '-v', 'threadtime'], capture_output=True, text=True, errors='replace').stdout
    (a.out / f'{name}.log').write_text(log)
    alive = sh('pidof', PKG, check=False).strip() == pid
    status = []
    if alive:
        try: status = [n['text'] for n in nodes() if n.get('text', '').startswith(('Visible:', 'Item ', 'Flow '))]
        except Exception: pass
    m = re.search(r'Caused by: (?:java\.lang\.)?(\w+Exception: [^\n]+)', log[log.find('FATAL EXCEPTION'):]) if 'FATAL EXCEPTION' in log else None
    fatal_text = log[log.find('FATAL EXCEPTION'):] if 'FATAL EXCEPTION' in log else ''
    header_frames = re.findall(r'ScreenStackHeaderConfig\.(\w+)\(', fatal_text[:6000])
    r = {'trial': name, 'forced_transition': False, 'hops_completed': hops,
         'header_parent_crash': 'The specified child already has a parent' in fatal_text and 'ScreenStackHeaderConfig.onUpdate' in fatal_text,
         'header_crash_frames': header_frames[:3],
         'screen_reinsert_soft_exceptions': len(re.findall(r'addViewAt: cannot insert view \[\d+\] into parent \[\d+\]: View already has a parent.*ScreensCoordinatorLayout', log)),
         'fatal': 'FATAL EXCEPTION' in log,
         'fatal_cause': m.group(1).strip() if m else None, 'process_alive': alive, 'final_texts': status}
    results.append(r); print(json.dumps(r), flush=True); (a.out / f'{a.label}.json').write_text(json.dumps(results, indent=2))
