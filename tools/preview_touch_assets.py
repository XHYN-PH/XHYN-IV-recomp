#!/usr/bin/env python3
"""Show exact APK glyph pixels in the shared disc theme; not a phone screenshot."""
from pathlib import Path
import argparse
import io
import math
import zipfile
from PIL import Image,ImageDraw,ImageFont

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apk',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    canvas=Image.new('RGB',(1680,960),'#101113');draw=ImageDraw.Draw(canvas)
    font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    title=ImageFont.truetype(font,34);body=ImageFont.truetype(font,21);small=ImageFont.truetype(font,18)
    draw.text((34,22),'XHYN IV · BUTTON ICONS FIX',font=title,fill='#f4f1e8')
    draw.text((34,74),'Your es2.zip selection · No outer white ring · Actual APK assets, not a phone screenshot',font=body,fill='#aaa9a5')
    groups=[('MOBILE · ON FOOT',[('aim','Aim'),('bullet','Fire'),('fist','Melee'),('jump','Jump'),('run','Sprint'),('door','Enter / Exit'),('view','Camera')]),('DRIVING',[('gas','Accelerate'),('brake','Brake'),('handbrake','Handbrake'),('horn','Horn'),('steer_left','Steer left'),('steer_right','Steer right'),('house','Pause')]),('ADDITIONAL IV ACTIONS · MATCHING XHYN ART',[('cover','Cover'),('crouch','Crouch'),('reload','Reload'),('phone','Phone'),('radio','Radio'),('back','Back'),('skip','Skip')])]
    with zipfile.ZipFile(args.apk) as apk:
        for row,(heading,items) in enumerate(groups):
            y=130+row*215;draw.text((34,y),heading,font=body,fill='#dfc28a')
            for i,(name,label) in enumerate(items):
                x=34+i*235;draw.ellipse((x+47,y+37,x+157,y+147),fill='#1e2023')
                image=Image.open(io.BytesIO(apk.read('assets/touch-icons/'+name+'.png'))).convert('RGBA').resize((100,100),Image.Resampling.LANCZOS)
                tinted=Image.new('RGBA',image.size,'#c2c2c2');tinted.putalpha(image.getchannel('A'));canvas.paste(tinted,(x+52,y+42),tinted)
                draw.text((x+102,y+161),label,font=small,anchor='mt',fill='#f4f1e8')
        y=802;draw.text((34,y),'XBOX LABELS + MATCHING ANALOG STICKS',font=body,fill='#dfc28a')
        for i,(name,color) in enumerate([('a','#41be50'),('b','#dc3c3c'),('x','#4b91f0'),('y','#f5cd32')]):
            x=70+i*170;draw.ellipse((x,y+40,x+80,y+120),fill='#1e2023');im=Image.open(io.BytesIO(apk.read('assets/touch-icons/'+name+'.png'))).convert('RGBA').resize((72,72),Image.Resampling.LANCZOS);tinted=Image.new('RGBA',im.size,color);tinted.putalpha(im.getchannel('A'));canvas.paste(tinted,(x+4,y+44),tinted)
        for x,offset in [(970,0),(1270,20)]:
            cy=y+80;draw.ellipse((x-50,cy-50,x+50,cy+50),fill='#1e2023');cx=x+offset;draw.ellipse((cx-15,cy-15,cx+15,cy+15),fill='#c2c2c2')
            for angle in (0,90,180,270):draw.arc((cx-23,cy-23,cx+23,cy+23),angle-22,angle+22,fill='#c2c2c2',width=3)
    args.output.parent.mkdir(parents=True,exist_ok=True);canvas.save(args.output,optimize=True);print(args.output)

if __name__=='__main__':main()
