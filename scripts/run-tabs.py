#!/usr/bin/env python3
"""Smoke-test native tab swap/root remount; does not inject removal transitions."""
import argparse,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--label',required=True);p.add_argument('--count',type=int,default=10);p.add_argument('--output',type=Path,default=Path('evidence'));args=p.parse_args()
root=args.output;root.mkdir(parents=True,exist_ok=True);a=['adb','-s',args.serial];pkg='com.screens3777repro'
def run(*x):return subprocess.check_output(a+list(x),text=True)
def ui():
 run('shell','rm','-f','/sdcard/rns3777-tabs.xml');run('shell','uiautomator','dump','/sdcard/rns3777-tabs.xml')
 return [n.attrib for n in ET.fromstring(run('shell','cat','/sdcard/rns3777-tabs.xml')).iter('node') if n.get('package')==pkg]
def texts(ns):return [n['text'] for n in ns if n.get('text')]
def tap(label):
 ns=ui();found=[n for n in ns if n.get('text')==label];assert len(found)==1,(label,texts(ns))
 b=list(map(int,re.findall(r'\d+',found[0]['bounds'])));run('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.7)
run('shell','am','force-stop',pkg);run('shell','am','start','-n',pkg+'/.MainActivity');time.sleep(2);tap('NATIVE TAB REMOVAL AND REMOUNT')
pid=run('shell','pidof',pkg).strip();lf=(root/(args.label+'.log')).open('w');lp=subprocess.Popen(a+['logcat','--pid='+pid,'-T','1','-v','threadtime'],stdout=lf)
results=[];revision=0;presses=0;tab='TabA'
for i in range(args.count):
 action=['SWAP SELECTED TAB A ↔ B','TOGGLE ROOT THROUGH LOADER','SWAP TAB + TOGGLE ROOT'][i%3]
 tap(action);time.sleep(.7);revision+=1
 if i%3!=1:tab='TabB' if tab=='TabA' else 'TabA'
 ns=texts(ui());assert f'Visible route: {tab} | header revision {revision}' in ns,ns
 assert f'L {revision}' in ns and f'{tab} r{revision}' in ns,ns
 tap(f'VERIFY CONTENT PRESS ({presses})');presses+=1
 tap(f'R {revision} ({presses})');presses+=1
 ns=texts(ui());assert any(f'presses {presses}' in t for t in ns),ns
 result={'cycle':i,'action':action,'revision':revision,'tab':tab,'presses':presses,'controlled_removal_start':False,'passed':True};results.append(result);print(json.dumps(result),flush=True);(root/(args.label+'.json')).write_text(json.dumps(results,indent=2))
lp.terminate();lp.wait();lf.close();log=(root/(args.label+'.log')).read_text();assert 'FATAL EXCEPTION' not in log and 'The specified child already has a parent' not in log
