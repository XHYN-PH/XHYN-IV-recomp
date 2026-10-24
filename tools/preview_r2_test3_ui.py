#!/usr/bin/env python3
"""Compact Test 3 layout illustrations; example values, not device screenshots."""
from pathlib import Path
import argparse
import cairosvg
from PIL import Image,ImageDraw,ImageFont
import preview_r2_ui as base
ROOT=base.ROOT
T=base.text;R=base.rect;P=base.picture
ACCENT=base.ACCENT;TEXT=base.TEXT;MUTED=base.MUTED
PATHS={
 'play':'M8 4L20 12L8 20Z','controls':'M5 6H19Q22 6 22 10V15Q22 19 18 19H6Q2 19 2 15V10Q2 6 5 6M6 12H12M9 9V15M17 11h.1M19 14h.1',
 'settings':'M3 5H21M3 12H21M3 19H21M16 3V7M8 10V14M16 17V21','folder':'M3 7V20H21V7H12L9 4H3Z',
 'driver':'M5 5H19V19H5ZM9 9H15V15H9ZM8 2V5M12 2V5M16 2V5M8 19V22M12 19V22M16 19V22',
 'trophy':'M7 4H17L16 13L12 16L8 13ZM12 16V21M8 21H16M7 6H3L4 12L8 13M17 6H21L20 12L16 13',
 'more':'M5 12h.1M12 12h.1M19 12h.1','cheats':'M13 2L5 13H11L10 22L20 10H13Z','exit':'M10 4H4V20H10M10 12H21L17 8M21 12L17 16','close':'M6 6L18 18M18 6L6 18','info':'M12 11V17M12 7h.1','diagnostics':'M2 13H6L9 6L13 19L16 10L19 13H22'}
def icon(x,y,name,color=TEXT,size=24):
 return f'<g transform="translate({x} {y}) scale({size/24})" fill="none" stroke="{color}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="{PATHS.get(name,PATHS["settings"])}"/></g>'
def button(x,y,w,label,glyph='settings',primary=False,h=52,size=19):
 color='#080808' if primary else TEXT
 return R(x,y,w,h,TEXT if primary else '#242629',12)+icon(x+16,y+(h-24)/2,glyph,color)+T(x+52,y+h/2+size*.34,label,size,color)
def wrap(x,y,value,width=38,size=18,color=TEXT):
 import textwrap
 return ''.join(T(x,y+i*(size+8),line,size,color) for i,line in enumerate(textwrap.wrap(value,width)))
def launcher():
 s=base.start(True)+P(base.ASSETS/'res/drawable-nodpi/launcher_iv.png',1150,28,160,160)
 s+=T(38,120,'LIBERTY CITY IS WAITING',16,ACCENT)+T(38,182,'XHYN IV',53,TEXT,'bold')
 s+=T(38,217,'720p · FPS limit 30 · Turnip_Gen8_V38-patched',16,MUTED)+T(38,246,'Game installed',17,MUTED)
 s+=button(38,267,626,'Play','play',True,66,25)
 for i,(label,glyph) in enumerate([('Settings','settings'),('Game files','folder'),('Controls','controls'),('Drivers','driver'),('Achievements','trophy'),('More','more')]):s+=button(38+(i%2)*321,345+(i//2)*62,305,label,glyph,h=53,size=19)
 s+=R(1000,412,320,145,'#181a1c',14,.9)+T(1018,440,'ACHIEVEMENTS',14,ACCENT)+T(1018,477,'1 / 65 unlocked',23)+button(1018,496,284,'View achievements','trophy',h=44,size=16)
 s+=T(38,591,'0.5.9.3-xhyn-r2-test3',14,MUTED)+T(1000,591,'YouTube ↗   Telegram ↗   GitHub ↗',14,ACCENT)
 return s

def session():
 s=base.start(True)+R(0,0,1360,612,'#000',0,.35)+R(674,18,666,576,'#121416',19,.96)
 s+=T(693,64,'XHYN IV',28)+T(1057,60,'PAUSED',13,ACCENT)
 for x,glyph in [(1215,'exit'),(1268,'close')]:s+=R(x,34,45,44,'#242629',10)+icon(x+11,44,glyph)
 s+=T(693,106,'Last active 29.0 FPS · 34.5 ms',18)+T(693,132,'RAM 2,100 MB · Free 1,600 MB',17)
 s+=T(693,156,'vivo V2425A · Android 16 · Turnip_Gen8_V38-patched',13,MUTED)
 for i,(label,glyph) in enumerate([('Session','info'),('Controls','controls'),('Controller','controls'),('Diagnostics','diagnostics')]):s+=button(693,181+i*57,166,label,glyph,h=50,size=15)
 s+=T(879,193,'SESSION',14,ACCENT)
 for i,(label,glyph) in enumerate([('Resume','play'),('Cheats','cheats'),('Achievements','trophy'),('Settings','settings')]):s+=button(879,210+i*59,442,label,glyph,h=52,size=19)
 s+=button(879,455,442,'Session details','info',h=43,size=16)
 return s

def settings():
 s=base.start(True)+R(20,16,1320,580,'#151515',19)
 s+=T(40,64,'SETTINGS',28,TEXT,'bold')+button(1090,30,100,'Back','exit',h=43,size=15)+button(1202,30,118,'Save','settings',True,43,15)
 for x,w,label in [(40,104,'Video'),(150,130,'Graphics'),(286,165,'Performance'),(457,136,'Controls'),(599,125,'Overlay'),(730,113,'Audio')]:s+=R(x,93,w,43,'#30291e' if label=='Controls' else '#242629',10)+T(x+14,121,label,17,ACCENT if label=='Controls' else TEXT)
 s+=T(1088,120,'Search settings',17,MUTED)
 labels=[('Gyroscope camera','Aim only'),('Gyro sensitivity','1.0×'),('Gyro smoothing','30 ms'),('Invert gyro horizontally','Off'),('Hold-to-lock aim','On'),('Aim lock hold duration','400 ms')]
 for i,(label,value) in enumerate(labels):
  y=155+i*60;s+=R(40,y,840,54,'#202123',10)+T(54,y+33,label,19)
  if i in [1,2,5]:s+=T(516,y+33,value,17,ACCENT)+R(634,y+25,212,4,'#4c4d4e',2)+R(634,y+25,78,4,ACCENT,2)+f'<circle cx="712" cy="{y+27}" r="8" fill="{ACCENT}"/>'
  else:s+=R(600,y+6,265,42,'#292b2e',10)+T(682,y+33,value,18)
 s+=T(918,181,'GYROSCOPE CAMERA',21,ACCENT)+wrap(918,218,'Aim only activates while aiming. Gameplay follows your phone during active play.',32,18)+wrap(918,332,'Stops in menus, loading screens and the background. Sensitivity and smoothing are adjustable.',32,17,MUTED)
 s+=T(40,571,'No unsaved changes',15,MUTED)+button(1090,542,100,'Reset','settings',h=37,size=14)+button(1202,542,118,'Discard','close',h=37,size=14)
 return s

def achievements():
 s=base.start(True)+R(20,16,1320,580,'#151515',19)+T(40,64,'ACHIEVEMENTS',28,TEXT,'bold')+button(1202,30,118,'Back','exit',h=43,size=15)
 s+=T(40,113,'1 / 65 unlocked · 64 locked',24)+button(1185,85,135,'Options','settings',h=40,size=15)
 for i,label in enumerate(['All','Locked','Unlocked']):s+=R(40+i*151,140,141,40,'#30291e' if i==0 else '#242629',10)+T(57+i*151,166,label,17,ACCENT if i==0 else TEXT)
 s+=T(900,166,'Search achievements',16,MUTED)
 entries=[('Off the Boat','Complete the first mission.','UNLOCKED · 5 points',True),('One Hundred and Eighty','Score 180 with three darts.','LOCKED · 10 points',False),('Secret achievement','Keep playing to discover this achievement.','LOCKED',False),('Warm Coffee','Successfully date a girl to be invited into her house.','LOCKED · 5 points',False)]
 for i,(name,desc,state,earned) in enumerate(entries):
  y=196+i*83;s+=R(40,y,1280,76,'#252119' if earned else '#1c1d1f',12)+R(53,y+12,50,50,'#44371f' if earned else '#303236',10)+icon(65,y+24,'trophy',ACCENT if earned else MUTED)
  s+=T(121,y+25,name,20)+T(121,y+47,desc,16,MUTED)+T(121,y+66,state,12,ACCENT if earned else MUTED)
 s+=T(40,571,'Local progress · Included in save backups · No Xbox Gamerscore',15,MUTED)
 return s

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--output',type=Path,required=True);args=ap.parse_args();args.output.mkdir(parents=True,exist_ok=True)
 images=[]
 for name,render in [('launcher',launcher),('session',session),('settings',settings),('achievements',achievements)]:
  svg=render()+'</g></svg>';target=args.output/(name+'.png');cairosvg.svg2png(bytestring=svg.encode(),write_to=str(target));images.append(Image.open(target).convert('RGB'))
 sheet=Image.new('RGB',(2720,1296),'#080808');draw=ImageDraw.Draw(sheet);font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',22)
 draw.text((24,12),'XHYN IV · Test 3 layout preview · Example values; not Android screenshots',font=font,fill='#dfc28a')
 for i,img in enumerate(images):sheet.paste(img,((i%2)*1360,56+(i//2)*612))
 sheet.save(args.output/'XHYN-IV-r2-test3-ui-preview.png');print(args.output/'XHYN-IV-r2-test3-ui-preview.png')
if __name__=='__main__':main()
