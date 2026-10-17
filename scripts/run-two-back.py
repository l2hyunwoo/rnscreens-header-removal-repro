#!/usr/bin/env python3
"""Natural two-Back sweep on R1: real UI taps + system Back key injection only.

No BEGIN/END broadcast, no startRemovalTransition call. The gap between the two
injected Back keys is a device-side schedule; the delivered gap is read from the
JS BackHandler probe (ReproBack js perfMs=...)."""
import argparse, json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--label', required=True)
p.add_argument('--gaps', required=True, help='comma list of ms; "seq" = one input process sending 4 4')
p.add_argument('--repeat', type=int, default=1)
p.add_argument('--settle', type=float, default=1.5)
p.add_argument('--entry', default='NESTED STACK AND TWO BACK EVENTS', help='empty = app starts on Home')
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
    sh('uiautomator', 'dump', '/sdcard/r1.xml')
    return [n.attrib for n in ET.fromstring(sh('cat', '/sdcard/r1.xml')).iter('node') if n.get('package') == PKG]


def texts(ns=None):
    return [n['text'] for n in (ns or nodes()) if n.get('text')]


def tap(label, wait=1.2):
    for _ in range(10):
        ns = nodes()
        found = [n for n in ns if n.get('text') == label]
        if found:
            b = list(map(int, re.findall(r'\d+', found[0]['bounds'])))
            sh('input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
            time.sleep(wait)
            return
        time.sleep(0.5)
    raise RuntimeError(f'{label} not found: {texts(ns)}')


def back_cmd(gap):
    if gap == 'seq':
        return 'input keyevent 4 4'
    g = int(gap)
    # Both processes pay the same startup cost; their difference approximates g.
    return f'(input keyevent 4 &) ; sleep {g/1000:.3f} ; input keyevent 4 ; wait'


results = []
for rep in range(a.repeat):
    for gap in a.gaps.split(','):
        name = f'{a.label}-{gap}-{rep:02d}'
        sh('am', 'force-stop', PKG)
        sh('logcat', '-c')
        sh('am', 'start', '-W', '-n', PKG + '/.MainActivity')
        time.sleep(1.5)
        if a.entry:
            tap(a.entry)
        tap('OPEN MIDDLE STACK')
        tap('OPEN INNER STACK')
        tap('OPEN FORM')
        time.sleep(a.settle)
        assert 'Inner form' in texts(), texts()
        pid = sh('pidof', PKG).strip()
        sh(back_cmd(gap))
        time.sleep(2.5)
        log = subprocess.run(A + ['logcat', '-d', '-v', 'threadtime'], capture_output=True, text=True, errors='replace').stdout
        (a.out / f'{name}.log').write_text(log)
        alive = sh('pidof', PKG, check=False).strip()
        js = [float(m) for m in re.findall(r'ReproBack js seq=\d+ perfMs=([\d.]+)', log)]
        cb = [int(m) for m in re.findall(r'ReproBack: callback uptime=(\d+)', log)]
        parent_exc = 'The specified child already has a parent' in log
        fatal = 'FATAL EXCEPTION' in log
        try:
            final = texts() if alive else []
        except Exception:
            final = []
        r = {
            'trial': name, 'scheduled_gap': gap, 'forced_transition': False,
            'js_back_count': len(js),
            'js_back_gap_ms': round(js[1] - js[0], 1) if len(js) >= 2 else None,
            'native_back_count': len(cb),
            'native_back_gap_ms': cb[1] - cb[0] if len(cb) >= 2 else None,
            'parent_exception': parent_exc, 'fatal': fatal,
            'process_alive': alive == pid,
            'middle_visible': 'Middle stack' in final,
        }
        results.append(r)
        print(json.dumps(r), flush=True)
        (a.out / f'{a.label}.json').write_text(json.dumps(results, indent=2))
