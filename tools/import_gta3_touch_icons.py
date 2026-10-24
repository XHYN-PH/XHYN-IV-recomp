#!/usr/bin/env python3
"""Import the user's GTA III touch glyphs into the shared mobile icon atlas.

The outer circular border and black backing are separated from the glyph:
our overlay already draws a scalable backing. Original glyph shapes are kept.
No APK or unrelated game files are copied into the output.
"""
import argparse,hashlib,io,json,math
from pathlib import Path
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
MAPPING={
 'gas':'accelerate.png','brake':'break.png','handbrake':'handbreak.png',
 'horn':'horn.png','jump':'jump.png','run':'run.png','fist':'punch.png',
 'aim':'target.png','bullet':'shoot.png','pistol':'shoot.png',
 'door':'enterandexit.png','eye':'Camera-big.png','menu':'pause.png',
 'steer_left':'left.png','steer_right':'right.png','left':'left.png','right':'right.png',
}
def glyph(raw):
 image=Image.open(io.BytesIO(raw)).convert('RGBA');w,h=image.size;out=Image.new('RGBA',(w,h));src=image.load();dst=out.load()
 for y in range(h):
  for x in range(w):
   # The border lies outside the original 114/128 inner radius.
   radius=math.hypot((x+.5-w/2)/(w/2),(y+.5-h/2)/(h/2))
   r,g,b,a=src[x,y]
   alpha=round(255*min(1,a/102)*min(1,max(r,g,b)/160)) if radius<.895 else 0
   dst[x,y]=(255,255,255,alpha)
 return out

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apk',type=Path,required=True);args=parser.parse_args()
 out=ROOT/'os/android/app/src/main/assets/touch-icons';out.mkdir(exist_ok=True,parents=True);records=[]
 with zipfile.ZipFile(args.apk) as z:
  for name,source in MAPPING.items():
   path='assets/es2/'+source;raw=z.read(path);image=glyph(raw);image.save(out/(name+'.png'))
   records.append({'icon':name,'source':path,'source_sha256':hashlib.sha256(raw).hexdigest()})
  right=glyph(z.read('assets/es2/right.png'))
  right.rotate(90).save(out/'up.png');right.rotate(-90).save(out/'down.png')
 records.extend([{'icon':'up','source':'assets/es2/right.png','rotation':90},{'icon':'down','source':'assets/es2/right.png','rotation':-90}])
 manifest={'source':'User-provided GTA III mobile APK','apk_sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),'processing':'Original glyph shapes; remove circular outline/backing and normalize tint; remaining actions use the matching vector set.','icons':records}
 (ROOT/'artwork/touch-icons/gta3-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print('Imported',len(records),'GTA III glyph variants; matching vector fallbacks retained')
if __name__=='__main__':main()
