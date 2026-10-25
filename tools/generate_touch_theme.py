#!/usr/bin/env python3
"""Render original, borderless touch glyphs. Requires CairoSVG only to regenerate.

These SVG drawings are original project artwork. Imported PNG variants
are retained with their separate provenance. Gameplay supplies the disc,
opacity, tint and press animation separately from these transparent glyphs.
"""
from pathlib import Path
import json
import argparse
import html
import math
import base64
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'os/android/app/src/main/assets/touch-icons'
SOURCE = ROOT / 'artwork/touch-icons'

def path(d):
    return f'<path d="{d}" fill-rule="evenodd"/>'

def rect(x,y,w,h,r=0):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/>'

def circle(x,y,r):
    return f'<circle cx="{x}" cy="{y}" r="{r}"/>'

def line(d,width=8):
    return f'<path d="{d}" fill="none" stroke="white" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>'

arrow = path('M16 50 47 23V40H84V60H47V77Z')
person_head = circle(56,22,11)
icons = {
 'interact': path('M30 82V49L19 44Q12 38 18 32Q22 28 29 34L37 40V16Q37 7 45 7Q52 7 52 16V38L57 34Q64 29 68 36Q76 32 80 40Q89 39 89 50L80 80Q77 91 65 92H44Q35 91 30 82Z'),
 'phone': path('M34 9H66Q74 9 74 17V83Q74 91 66 91H34Q26 91 26 83V17Q26 9 34 9Z M36 20V48H64V20Z M36 58V65H43V58Z M47 58V65H54V58Z M58 58V65H65V58Z M36 71V78H43V71Z M47 71V78H54V71Z M58 71V78H65V71Z'),
 'cover': circle(36,23,10)+path('M32 36 44 35 56 48 49 57 37 47 29 60 47 60 52 70 43 89H29L37 73H20Q11 70 17 58Z')+rect(65,33,22,56,2),
 'crouch': person_head+path('M48 35 61 35 73 48 85 49 83 60 66 59 56 49 44 62 66 62Q75 62 70 74L58 91H44L53 74H38L25 87H11L31 61Z'),
 'reload': path('M57 22 77 29 63 88 43 81Z M58 35 55 48 60 49 63 36Z')+line('M40 73C13 63 17 27 43 24',8)+path('M48 23 30 12 32 37Z'),
 'left': arrow, 'right': f'<g transform="translate(100 0) scale(-1 1)">{arrow}</g>',
 'up': f'<g transform="rotate(90 50 50)">{arrow}</g>',
 'down': f'<g transform="rotate(-90 50 50)">{arrow}</g>',
 'steer_left': path('M65 15H43L12 50 43 85H65L34 50Z'),
 'steer_right': path('M35 15H57L88 50 57 85H35L66 50Z'),
 'radio': path('M18 32H82V82H18Z M26 40V47H59V40Z M26 55V74H47V55Z M55 57V64H74V57Z M55 68V74H74V68Z')+line('M27 29 66 13',5),
 'eye': path('M8 50Q50 6 92 50Q50 94 8 50Z M50 29A21 21 0 1 0 50 71A21 21 0 1 0 50 29Z')+circle(50,50,11),
 'check': path('M9 52 23 38 42 57 77 17 92 31 43 86Z'),
 'back': path('M10 41 40 15V32H64Q89 32 89 60Q89 86 64 86H46V68H64Q71 68 71 60Q71 51 64 51H40V68Z'),
 'hang_up': path('M12 48Q50 12 88 48L83 66 64 64 61 51Q50 45 39 51L36 64 17 66Z'),
 'house': path('M9 44 50 9 91 44 81 55 75 50V88H57V63H43V88H25V50L19 55Z'),
 'menu': rect(17,23,66,11,4)+rect(17,45,66,11,4)+rect(17,67,66,11,4),
 'view': path('M13 19H65V30H24V63H13Z')+rect(34,40,53,41,5),
 'skip': path('M12 22 45 50 12 78Z M43 22 76 50 43 78Z')+rect(79,22,10,56),
 'pistol': path('M12 26H86V43H64L62 52H53L64 82H44L32 48H12Z M42 44 46 51H53L55 44Z'),
 'bullet': path('M39 88V40Q39 22 50 10Q61 22 61 40V88Z M43 68V76H57V68Z'),
 'aim': path('M45 9H55V29H45Z M45 71H55V91H45Z M9 45H29V55H9Z M71 45H91V55H71Z M50 30A20 20 0 1 0 50 70A20 20 0 1 0 50 30Z M50 38A12 12 0 1 1 50 62A12 12 0 1 1 50 38Z'),
 'door': path('M16 32H45L63 14H84V83H16Z M45 43H73V26H66Z M59 55V61H73V55Z'),
 'horn': path('M12 40H33Q64 40 86 17V76Q64 55 33 55H12Z M33 57Q18 87 53 82L65 73 53 65Q42 76 35 69Z'),
 'fist': path('M20 47V32Q20 24 30 27V23Q30 15 40 20V17Q42 10 51 19Q58 10 64 20L70 47 79 36Q88 34 88 44L72 73 65 87H32L24 69 13 58Q9 46 20 47Z'),
 'jump': person_head+line('M51 39 38 57 63 57 79 78M38 57 21 70M50 39 28 31 18 18M50 39 74 35 83 20',10),
 'run': person_head+line('M50 38 38 60 59 70 48 90M38 60 21 78M48 40 31 43 20 59M48 40 65 53 80 47',10),
 'handbrake': path('M28 15H56Q79 15 79 39Q79 63 56 63H45V87H28Z M45 31V47H55Q62 47 62 39Q62 31 55 31Z'),
 'gas': path('M48 10H73Q80 10 80 18V83Q80 90 72 90H28Q20 90 20 82V56L40 32V18Q40 10 48 10Z M51 23V30H58V23Z M65 23V30H72V23Z M51 39V46H58V39Z M65 39V46H72V39Z M32 59V66H39V59Z M48 59V66H55V59Z M64 59V66H71V59Z M32 75V82H39V75Z M48 75V82H55V75Z M64 75V82H71V75Z'),
 'brake': path('M14 32H86Q91 32 91 38V72Q91 78 84 78H16Q9 78 9 72V38Q9 32 14 32Z M20 43V50H27V43Z M36 43V50H43V43Z M52 43V50H59V43Z M68 43V50H75V43Z M20 59V66H27V59Z M36 59V66H43V59Z M52 59V66H59V59Z M68 59V66H75V59Z'),
 'gamepad': path('M26 26H74Q83 26 87 39L94 68Q95 86 82 82L65 68H35L18 82Q5 86 6 68L13 39Q17 26 26 26Z M24 39V47H16V55H24V63H32V55H40V47H32V39Z M64 40A5 5 0 1 0 64 50A5 5 0 1 0 64 40Z M79 52A5 5 0 1 0 79 62A5 5 0 1 0 79 52Z'),
 'reset': line('M22 39A31 31 0 1 1 23 69',10)+path('M17 17 39 37 11 43Z'),
}
for label in ('lb','rb','lt','rt','l3','r3','a','b','x','y'):
    # The Xbox overlay uses these glyphs, with text/vector fallbacks if absent.
    size=60 if len(label)==1 else 43
    icons[label]=f'<text x="50" y="52" text-anchor="middle" dominant-baseline="central" font-family="DejaVu Sans" font-weight="bold" font-size="{size}">{label.upper()}</text>'

def preview(destination):
    names=list(icons)
    columns=7;cell=150;rows=math.ceil(len(names)/columns);height=rows*cell+260
    parts=[f'<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="{height}"><rect width="1080" height="{height}" fill="#101113"/><g font-family="DejaVu Sans" fill="#dedede"><text x="35" y="42" font-size="26" font-weight="bold">XHYN IV · TOUCH ARTWORK</text><text x="35" y="69" font-size="14" fill="#999">Current source glyphs · borderless discs · matching analog sticks</text>']
    colors={'a':'#41be50','b':'#dc3c3c','x':'#4b91f0','y':'#f5cd32'}
    for i,name in enumerate(names):
        x=15+(i%columns)*cell;y=93+(i//columns)*cell;color=colors.get(name,'#c2c2c2')
        asset=base64.b64encode((OUT/f'{name}.png').read_bytes()).decode()
        parts.append(f'<g transform="translate({x} {y})"><circle cx="75" cy="53" r="48" fill="#1e2023"/><image href="data:image/png;base64,{asset}" x="29" y="7" width="92" height="92" opacity=".76"/><text x="75" y="122" font-size="13" text-anchor="middle" fill="#aaa">{html.escape(name.replace("_"," "))}</text></g>')
    y=rows*cell+170
    for x,offset,label in [(340,0,'Stick · idle'),(640,21,'Stick · held')]:
        parts.append(f'<circle cx="{x}" cy="{y}" r="49" fill="#1e2023"/><g fill="#c2c2c2" opacity=".8"><circle cx="{x+offset}" cy="{y}" r="14.7"/>')
        for mark in range(4):
            a=mark*math.pi/2-.38;b=a+.76;r=49*.41;cx=x+offset
            parts.append(f'<path d="M{cx+r*math.cos(a)} {y+r*math.sin(a)} A{r} {r} 0 0 1 {cx+r*math.cos(b)} {y+r*math.sin(b)}" fill="none" stroke="#c2c2c2" stroke-width="2.94"/>')
        parts.append(f'</g><text x="{x}" y="{y+71}" text-anchor="middle" font-size="14" fill="#aaa">{label}</text>')
    parts.append('</g></svg>');destination.parent.mkdir(parents=True,exist_ok=True)
    cairosvg.svg2png(bytestring=''.join(parts).encode(),write_to=str(destination))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview',type=Path,help='Also render a labeled source-art preview PNG')
    args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True);SOURCE.mkdir(parents=True,exist_ok=True)
    imported=set()
    for provenance in (SOURCE/"gta3-provenance.json",SOURCE/"es2-provenance.json"):
        if provenance.exists():imported.update(entry["icon"] for entry in json.loads(provenance.read_text())["icons"])
    for name,body in icons.items():
        svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" fill="white">{body}</svg>\n'
        (SOURCE/f'{name}.svg').write_text(svg)
        if name not in imported or name in {"pistol","eye","interact"}:cairosvg.svg2png(bytestring=svg.encode(),write_to=str(OUT/f'{name}.png'),output_width=256,output_height=256)
    # Xbox Start keeps its three-line symbol when mobile Menu uses an imported pause glyph.
    menu_svg=(SOURCE/'menu.svg').read_bytes()
    cairosvg.svg2png(bytestring=menu_svg,write_to=str(OUT/'xbox_menu.png'),output_width=256,output_height=256)
    (SOURCE/'README.md').write_text('The SVG files are original XHYN IV geometric glyphs under the project license.\nImported PNG provenance is retained separately; pistol, eye and interact use the original SVGs: see es2-provenance.json for the current user-provided archive and gta3-provenance.json for the previous import. Their creator/redistribution license is not established by the code license.\nRegenerate original glyphs with `python3 tools/generate_touch_theme.py` (CairoSVG required). Reimport the current set with `python3 tools/import_es2_touch_icons.py --zip /path/to/es2.zip`.\nDiscs, tint and animations are drawn by the app; glyph backgrounds are transparent.\n')
    print(f'Prepared {len(icons)} glyphs; retained imported variants except the documented original pistol/eye/interact replacements')
    if args.preview:preview(args.preview);print(args.preview)

if __name__=='__main__':main()
