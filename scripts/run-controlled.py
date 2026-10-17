#!/usr/bin/env python3
"""Exercise real Screen/Toolbar classes with a controlled removal-start boundary."""
import argparse,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--label',required=True);p.add_argument('--count',type=int,default=1);p.add_argument('--fixed',action='store_true');p.add_argument('--record',action='store_true');p.add_argument('--reuse',action='store_true',help='Keep the fixed screen mounted between cycles');p.add_argument('--output',type=Path,default=Path('evidence'));args=p.parse_args()
root=args.output;root.mkdir(parents=True,exist_ok=True);adb=['adb','-s',args.serial];package='com.screens3777repro'
def run(*a):return subprocess.check_output(adb+list(a),text=True)
def ui():
 run('shell','rm','-f','/sdcard/rns3777-ui.xml')
 run('shell','uiautomator','dump','/sdcard/rns3777-ui.xml')
 tree=ET.fromstring(run('shell','cat','/sdcard/rns3777-ui.xml'))
 return [n.attrib for n in tree.iter('node') if n.get('package')==package]
def texts(nodes):return [n['text'] for n in nodes if n.get('text')]
def tap(label):
 for attempt in range(4):
  nodes=ui();found=[n for n in nodes if n.get('text')==label]
  if len(found)==1:break
  time.sleep(1)
 assert len(found)==1,(label,texts(nodes))
 b=list(map(int,re.findall(r'\d+',found[0]['bounds'])));run('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.6)
def setup():
 run('shell','am','force-stop',package);run('shell','am','start','-n',package+'/.MainActivity');time.sleep(2)
 initial=texts(ui())
 if 'OPEN MIDDLE STACK' not in initial:tap('NESTED STACK AND TWO BACK EVENTS')
 for label in ['OPEN MIDDLE STACK','OPEN INNER STACK','OPEN FORM']:tap(label)
 assert 'Header revision: 0' in texts(ui())
results=[]
for i in range(args.count):
 if i==0 or not (args.fixed and args.reuse):setup()
 revision=2*i if args.fixed and args.reuse else 0
 name=f'{args.label}-{i:02d}';pid=run('shell','pidof',package).strip()
 lf=(root/(name+'.log')).open('w');lp=subprocess.Popen(adb+['logcat','--pid='+pid,'-T','1','-v','threadtime'],stdout=lf)
 record=None
 if args.record and i==0:
  record=subprocess.Popen(adb+['shell','screenrecord','--time-limit','45','/sdcard/rns3777-controlled.mp4']);time.sleep(1.5)
 run('shell','am','broadcast','-a',package+'.BEGIN','-p',package);tap('REFRESH HEADER AFTER BEGIN');time.sleep(1)
 first=texts(ui());(root/(name+'-after-update.json')).write_text(json.dumps(first,indent=2))
 if args.fixed:
  assert f'Header revision: {revision+1}' in first,first
  run('shell','am','broadcast','-a',package+'.END','-p',package);tap('REFRESH HEADER AFTER BEGIN')
  last=texts(ui());assert f'Header revision: {revision+2}' in last,last
  if not args.reuse or i in (0,args.count-1):
   run('shell','input','keyevent','4');time.sleep(.8);assert 'OPEN FORM' in texts(ui())
   tap('OPEN FORM');assert f'Header revision: {revision+2}' in texts(ui())
 else:last=first
 time.sleep(1)
 if record:
  remote=run('shell','pidof','screenrecord').strip();run('shell','kill','-2',remote);record.wait()
  subprocess.run(adb+['pull','/sdcard/rns3777-controlled.mp4',str(root/(args.label+'.mp4'))],check=True,stdout=subprocess.DEVNULL)
 lp.terminate();lp.wait();lf.close();log=(root/(name+'.log')).read_text()
 error='The specified child already has a parent' in log or any('The specified child already has a parent' in x for x in first)
 result={'run':name,'controlled_removal_start':True,'begin_observed':'BEGIN owner=' in log,'same_parent_exception':error,'onUpdate_exception':error and ('ScreenStackHeaderConfig.onUpdate' in log or any('ScreenStackHeaderConfig.kt:' in x for x in first)),'post_end_ui':last,'expected_result':not error if args.fixed else error}
 results.append(result);(root/(args.label+'.json')).write_text(json.dumps(results,indent=2));print(json.dumps(result),flush=True)
 assert result['begin_observed'] and result['expected_result'],result
