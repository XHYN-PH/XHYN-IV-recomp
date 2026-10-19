#!/usr/bin/env python3
"""Export native mobile control geometry for the launcher's offline editor."""
from pathlib import Path
import copy,json,re
root=Path(__file__).resolve().parents[1]
source=(root/'glue/rexglue-sdk-main/gta4-recomp/src/input/liberty_touch_overlay.cpp').read_text()
pattern=re.compile(r'\{"([^"]+)",\s*Kind::k(\w+),\s*Anchor::k(\w+),\s*([0-9.]+),\s*([0-9.]+),\s*([0-9.]+),\s*Icon::k(\w+),\s*(nullptr|"[^"]*")(?:\s*,\s*(\w+))?')
icon_files=dict(re.findall(r'case Icon::k(\w+): return "([^"]+)";',source))
button_hints=dict(A='A',B='B',X='X',Y='Y',LEFT_SHOULDER='LB',RIGHT_SHOULDER='RB',LEFT_THUMB='LS',RIGHT_THUMB='RS',START='START',BACK='BACK',DPAD_UP='UP',DPAD_DOWN='DOWN',DPAD_LEFT='LEFT',DPAD_RIGHT='RIGHT')
def specs(text):
 result=[]
 for identity,kind,anchor,x,y,r,icon,label,button in pattern.findall(text):
  if identity=='switch':continue
  hint=('LS' if button in ('','0') else 'RS') if kind=='Stick' else ('LT' if button=='kLT' else 'RT') if kind=='Trigger' else button_hints.get(button.removeprefix('X_INPUT_GAMEPAD_'),'') if kind=='Button' else ''
  result.append(dict(id=identity,kind=kind.lower(),anchor=anchor.lower(),x=float(x),y=float(y),r=float(r),icon=icon,image=icon_files.get(icon,''),hint=hint,label=identity.replace('_',' ') if label=='nullptr' else label[1:-1]))
 return result
def base(name):
 start=source.index('static const std::vector<Spec> '+name+' = {')
 return specs(source[start:source.index('\n  };',start)])
layouts={k:base(k) for k in ['on_foot','driving','phone','menu','cutscene']}
layouts['bike']=[copy.deepcopy(x) for x in layouts['driving'] if x['id'] not in ['steer_left','steer_right']]
layouts['bike']+=specs('{"steer_lean",Kind::kStick,Anchor::kLeft,400,540,112,Icon::kNone,"STEER / LEAN"')
layouts['helicopter']=[copy.deepcopy(x) for x in layouts['driving'] if x['id'] not in ['steer_left','steer_right','handbrake','look_back']]
for x in layouts['helicopter']:
 if x['id']=='gas':x.update(id='ascend',label='ASCEND',icon='Up')
 elif x['id']=='brake':x.update(id='descend',label='DESCEND',icon='Down')
 elif x['id']=='drive_by':x.update(id='fire',label='FIRE',hint='A')
 elif x['id']=='exit':x['label']='EXIT'
start=source.index('static const std::vector<Spec> helicopter =')
end=source.index('static const std::vector<Spec> passenger =',start)
layouts['helicopter']+=specs(source[start:end])
layouts['passenger']=[copy.deepcopy(x) for x in layouts['on_foot'] if x['id'] not in ['move','jump','sprint','crouch','cover','melee']]
for x in layouts['passenger']:
 if x['id']=='enter':x['label']='EXIT'
 elif x['id']=='shoot':x.update(kind='button',hint='LB')
order=[('on_foot','On foot'),('driving','Car / boat'),('bike','Bike'),('helicopter','Helicopter'),('passenger','Passenger'),('phone','Phone'),('menu','Menus / map'),('cutscene','Cutscene')]
for key,_ in order:
 assert layouts[key] and len({x['id'] for x in layouts[key]})==len(layouts[key]),key
 for control in layouts[key]:control['image']=icon_files.get(control['icon'],'')
out=root/'os/android/app/src/main/assets/touch-defaults.json'
out.write_text(json.dumps([dict(id=k,name=n,controls=layouts[k]) for k,n in order],indent=2)+'\n')
print('Exported',sum(len(v) for v in layouts.values()),'native control defaults across',len(layouts),'contexts')

# Xbox geometry is defined once in a pure native header, then exported here.
xbox_source=(root/'glue/rexglue-sdk-main/gta4-recomp/src/input/gamepad_touch_defaults.h').read_text()
xbox=[]
for row in re.findall(r'\{"([^"]+)","([^"]+)",([^{}]+)\}',xbox_source):
 values=[v.strip() for v in row[2].split(',')]
 assert len(values)==8,row
 item=dict(id=row[0],label=row[1],anchor='left')
 item.update(zip(['xf','xh','xs','yf','ys','r'],[float(v.removesuffix('f')) for v in values[:6]]))
 item.update(buttons=values[6]=='true',camera=values[7]=='true')
 xbox.append(item)
assert len(xbox)==18 and len({c['id'] for c in xbox})==18
(root/'os/android/app/src/main/assets/xbox-defaults.json').write_text(json.dumps([dict(id='xbox',name='Xbox controls',controls=xbox)],indent=2)+'\n')
print('Exported',len(xbox),'Xbox control defaults')
