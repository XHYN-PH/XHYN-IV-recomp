#!/usr/bin/env python3
"""Import only mapped touch glyphs from the user-provided fan-game es2 ZIP.

Keep original glyph shapes, remove the outer ring/backing, and leave unmatched
IV actions and Xbox labels in the existing XHYN vector set. No game backgrounds,
logos, characters, map textures or unrelated archive contents are imported.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image

def glyph(raw):
    image=Image.open(io.BytesIO(raw)).convert("RGBA")
    if image.size!=(256,256):raise ValueError("Expected a 256 by 256 source icon")
    pixels=image.load()
    for y in range(256):
        for x in range(256):
            r,g,b,a=pixels[x,y]
            alpha=0 if (x-127.5)**2+(y-127.5)**2>=116**2 or max(r,g,b)<24 else min(255,round(a*2.5))
            pixels[x,y]=(r,g,b,alpha)
    return image

ROOT=Path(__file__).resolve().parents[1]
MAPPING={
    'gas':'accelerate.png','brake':'break.png','handbrake':'handbreak.png',
    'horn':'horn.png','jump':'jump.png','run':'run.png','fist':'punch.png',
    'aim':'lockon.png','bullet':'shoot.png',
    'door':'enterandexit.png','view':'cam-toggle.png',
    'menu':'pause.png','house':'pause.png','steer_left':'left.png',
    'steer_right':'right.png','left':'left.png','right':'right.png',
}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--zip',type=Path,required=True);args=parser.parse_args()
    out=ROOT/'os/android/app/src/main/assets/touch-icons';out.mkdir(parents=True,exist_ok=True);records=[]
    with zipfile.ZipFile(args.zip) as z:
        for name,source in MAPPING.items():
            path='es2/'+source;raw=z.read(path);image=glyph(raw);image.save(out/f'{name}.png')
            records.append({'icon':name,'source':path,'source_sha256':hashlib.sha256(raw).hexdigest(),'output_sha256':hashlib.sha256((out/f'{name}.png').read_bytes()).hexdigest()})
        raw=z.read('es2/right.png');right=glyph(raw)
        for name,degrees in [('up',90),('down',-90)]:
            right.rotate(degrees).save(out/f'{name}.png');records.append({'icon':name,'source':'es2/right.png','source_sha256':hashlib.sha256(raw).hexdigest(),'rotation':degrees,'output_sha256':hashlib.sha256((out/f'{name}.png').read_bytes()).hexdigest()})
    result={'source':'User-provided es2.zip, described by the user as a fan-game asset archive','archive_sha256':hashlib.sha256(args.zip.read_bytes()).hexdigest(),'creator_and_license':'Not supplied with the archive; no claim that imported assets are original XHYN work or covered by the project license','processing':'Keep original glyph shapes and colour; remove circular rim/black backing and normalize baked opacity. Aim uses lockon.png. Mobile Pause uses the supplied pause.png; pistol, eye and interact retain the project SVGs because no corresponding action artwork is supplied.','icons':records}
    (ROOT/'artwork/touch-icons/es2-provenance.json').write_text(json.dumps(result,indent=2)+'\n')
    print('Imported',len(records),'mapped ES2 variants; unrelated artwork excluded.')

if __name__=='__main__':main()
