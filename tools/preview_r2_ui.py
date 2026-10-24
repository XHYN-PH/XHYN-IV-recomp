#!/usr/bin/env python3
"""Render source-aligned R2 layout previews, not Android screenshots.

Example telemetry and achievement entries illustrate the states. Uses the
checked-in launcher art, current touch PNGs and the Java theme's colors.
"""
from pathlib import Path
import argparse
import base64
import html
import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "os/android/app/src/main"
TEXT = "#f4f1e8"
MUTED = "#aaa9a5"
ACCENT = "#dfc28a"
W, H = 1360, 612


def picture(path, x, y, w, h, opacity=1, fit="xMidYMid slice"):
    suffix = "jpeg" if path.suffix == ".jpg" else "png"
    data = base64.b64encode(path.read_bytes()).decode()
    return f'<image href="data:image/{suffix};base64,{data}" x="{x}" y="{y}" width="{w}" height="{h}" opacity="{opacity}" preserveAspectRatio="{fit}"/>'


def rect(x, y, w, h, fill="#242424", radius=18, opacity=1):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" opacity="{opacity}"/>'


def text(x, y, value, size=21, color=TEXT, weight="normal"):
    return f'<text x="{x}" y="{y}" fill="{color}" font-size="{size}" font-weight="{weight}">{html.escape(value)}</text>'


def button(x, y, w, label, primary=False, h=60, size=22):
    return rect(x, y, w, h, TEXT if primary else "#242424", 20) + text(x+22, y+h/2+size*.34, label, size, "#080808" if primary else TEXT, "bold")


def start(backdrop=False):
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><defs><linearGradient id="shade"><stop stop-color="#08090a" stop-opacity=".92"/><stop offset=".65" stop-color="#08090a" stop-opacity=".5"/><stop offset="1" stop-color="#08090a" stop-opacity=".08"/></linearGradient><linearGradient id="bottom" x2="0" y2="1"><stop stop-color="#08090a" stop-opacity="0"/><stop offset="1" stop-color="#08090a" stop-opacity=".8"/></linearGradient></defs><g font-family="DejaVu Sans, sans-serif">'
    svg += rect(0, 0, W, H, "#080808", 0)
    if backdrop:
        svg += picture(ASSETS/"res/drawable-nodpi/launcher_art.jpg", -24, -12, W+48, H+24)
        svg += rect(0, 0, W, H, "url(#shade)", 0)
        svg += rect(0, 210, W, H-210, "url(#bottom)", 0)
    return svg


def launcher():
    s = start(True)
    s += picture(ASSETS/"res/drawable-nodpi/launcher_iv.png", 1148, 22, 172, 172)
    s += text(38, 215, "LIBERTY CITY IS WAITING", 17, ACCENT)
    s += text(38, 279, "XHYN IV", 54, TEXT, "bold")
    s += text(38, 312, "720p · FPS limit 30 · Turnip_Gen8_V38-patched", 17, MUTED)
    s += text(38, 344, "Game files ready", 19)
    s += button(38, 362, 624, "▶   Play", True, 82, 30)
    s += button(38, 457, 306, "Settings", h=65)
    s += button(356, 457, 306, "Game files", h=65)
    for x, w, label in [(38, 144, "Controls"), (194, 136, "Drivers"), (342, 205, "Achievements"), (559, 103, "More")]:
        s += button(x, 534, w, label, h=50, size=17)
    s += text(38, 602, "0.5.9.3-xhyn-r2-test2", 14, MUTED)
    s += text(1000, 602, "YouTube ↗   Telegram ↗   GitHub ↗", 14, ACCENT)
    return s


def session():
    s = start(True) + rect(0, 0, W, H, "#000", 0, .32)
    s += text(42, 51, "PAUSED GAME AREA", 17, MUTED)
    s += text(42, 78, "Artwork used here for the layout preview", 15, MUTED)
    # 440 dp panel at the preview's ~1.4 px/dp density.
    x, y, w = 720, 18, 616
    s += rect(x, y, w, 576, "#151515", 28, .91)
    s += text(x+22, 66, "XHYN IV", 35, TEXT, "bold") + text(x+22, 89, "SESSION PAUSED", 15, ACCENT)
    s += button(x+w-88, 35, 64, "×", h=50, size=25)
    s += text(x+22, 132, "Last active: 29.7 FPS · 33.7 ms average", 19)
    s += text(x+22, 160, "App RAM 2940 MB · Available RAM 4120 MB", 19)
    s += text(x+22, 189, "vivo V2425A · Android 16", 17, MUTED)
    s += text(x+22, 214, "Adreno 750 · Turnip_Gen8_V38-patched", 17, MUTED)
    s += button(x+22, 230, w-44, "Session details ▾", h=45, size=18)
    for xx, label, active in [(x+22, "Session", True), (x+139, "Controls", False), (x+262, "Controller", False), (x+402, "Graphics", False)]:
        s += rect(xx, 285, 110 if label != "Controller" else 128, 50, "#242424", 16)
        s += text(xx+10, 317, label, 17, ACCENT if active else TEXT)
    s += text(x+w-37, 318, "›", 23, MUTED)
    for xx, yy, ww, label in [(x+22, 352, 280, "Resume"), (x+314, 352, 280, "Cheats"), (x+22, 432, 280, "Achievements"), (x+314, 432, 280, "Settings"), (x+22, 512, 572, "Quit to launcher")]:
        s += button(xx, yy, ww, label, h=67, size=22)
    return s


def frame(title):
    s = start() + rect(27, 18, 1306, 576, "#151515", 28)
    s += text(45, 68, title, 31, TEXT, "bold")
    s += button(1086, 35, 102, "Back", h=50, size=21)
    return s


def settings():
    s = frame("SETTINGS") + button(1200, 35, 110, "Save", True, 50, 21)
    tabs = [(45, 166, "Basic video", True), (220, 196, "Advanced video", False), (426, 178, "Performance", False), (614, 137, "Controls", False), (761, 111, "Audio", False)]
    for x, width, label, active in tabs:
        s += rect(x, 103, width, 58, "#242424", 19) + text(x+15, 139, label, 19, ACCENT if active else TEXT)
    s += rect(1000, 103, 310, 58, "#202020", 10) + text(1014, 139, "Search all settings", 19, MUTED)
    rows = [("Output resolution", "720p", True), ("Upscaler", "Native", True), ("FSR quality", "Quality", True), ("FSR softness", "0.2×", True), ("Anti-aliasing", "FXAA", False)]
    for i, (name, value, restart) in enumerate(rows):
        y = 181+i*67
        s += rect(45, y, 777, 59, "#1c1d1f", 12)
        s += text(61, y+28, name, 21)
        if restart: s += text(61, y+47, "Next launch", 13, MUTED)
        s += rect(534, y+4, 279, 51, "#242424", 16)
        s += text(552, y+36, value, 20) + text(782, y+35, "›", 22, MUTED)
    s += rect(846, 181, 464, 328, "#1c1d1f", 18)
    s += text(869, 223, "Output resolution", 25, TEXT, "bold")
    for i, line in enumerate(["Sets the size of the final image.", "Higher resolutions can improve detail", "and increase GPU work and memory.", "", "Default: 720p", "Applies on the next launch.", "Select a setting to see its explanation."]):
        s += text(869, 263+i*29, line, 18, MUTED)
    s += text(45, 556, "No unsaved changes", 17, MUTED)
    s += button(927, 527, 135, "Discard", h=49, size=17)
    s += button(1074, 527, 236, "Reset this category", h=49, size=17)
    return s


def badge(x, y, unlocked, scale=1):
    fill, ink = ("#44371f", ACCENT) if unlocked else ("#303236", "#b6b8bd")
    s = f'<g transform="translate({x} {y}) scale({scale})">' + rect(1, 1, 62, 62, fill, 16)
    if unlocked:
        s += rect(20, 14, 24, 21, ink, 5)
        s += f'<g fill="none" stroke="{ink}" stroke-width="4" stroke-linecap="round"><path d="M20 17H12Q12 33 25 34M44 17H52Q52 33 39 34M32 35V47M23 49H41"/></g>'
    else:
        s += f'<rect x="22" y="13" width="20" height="25" rx="10" fill="none" stroke="{ink}" stroke-width="4"/>' + rect(16, 29, 32, 23, ink, 5)
        s += '<circle cx="32" cy="38" r="3" fill="#151515"/>' + rect(30, 39, 4, 6, "#151515", 0)
    return s + '</g>'


def achievements():
    s = frame("ACHIEVEMENTS")
    s += text(45, 125, "1 / 65 unlocked · 64 locked", 31)
    s += button(1160, 96, 150, "Options", h=50, size=21)
    s += rect(45, 158, 1265, 8, "#303236", 3) + rect(45, 158, 20, 8, ACCENT, 3)
    for x, width, label, on in [(45, 113, "All", True), (170, 151, "Locked", False), (333, 172, "Unlocked", False)]:
        s += rect(x, 180, width, 58, "#242424", 19) + text(x+20, 217, label, 21, ACCENT if on else TEXT)
    s += text(553, 217, "Search achievements", 20, MUTED)
    sample = [(True, "Off the Boat", "Complete the first mission.", "UNLOCKED · Saved unlock date · 5 points"), (False, "One Hundred and Eighty", "Score three 60s in a row in a game of darts.", "LOCKED · 10 points"), (False, "Secret achievement", "Keep playing to discover this achievement.", "LOCKED")]
    for i, (earned, name, detail, status) in enumerate(sample):
        y = 251+i*98
        s += rect(45, y, 1265, 88, "#252119" if earned else "#1c1d1f", 17)
        s += badge(57, y+10, earned, 1.03)
        s += text(144, y+29, name, 25) + text(144, y+53, detail, 18, MUTED)
        s += text(144, y+74, status, 15, ACCENT if earned else MUTED)
        s += badge(1251, y+24, earned, .6)
    s += text(45, 582, "Local progress · Included in save backups · No Xbox Gamerscore", 15, MUTED)
    return s


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    previews = [("launcher", launcher()), ("session", session()), ("settings", settings()), ("achievements", achievements())]
    for name, source in previews:
        source += '</g></svg>'
        (args.output/f"{name}.svg").write_text(source)
        cairosvg.svg2png(bytestring=source.encode(), write_to=str(args.output/f"{name}.png"))
    sheet = Image.new("RGB", (2800, 1610), "#080808")
    d = ImageDraw.Draw(sheet)
    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    title_font, small_font = ImageFont.truetype(font_path, 38), ImageFont.truetype(font_path, 23)
    d.text((30, 22), "XHYN IV · R2 TEST 2", font=title_font, fill=TEXT)
    d.text((30, 76), "Layout previews from the implemented UI · Example telemetry and catalog entries · Not phone screenshots", font=small_font, fill=MUTED)
    labels = ["01  Launcher · actions moved down", "02  Paused session · expandable information", "03  Settings · categories, options and help", "04  Achievements · locked, unlocked and details"]
    for i, (name, _) in enumerate(previews):
        x, y = 30+(i%2)*1380, 120+(i//2)*675
        d.text((x, y), labels[i], font=small_font, fill=ACCENT)
        sheet.paste(Image.open(args.output/f"{name}.png").convert("RGB"), (x, y+38))
    d.text((30, 1475), "Actual GTA III glyphs with the outer white ring removed + matching XHYN additions", font=small_font, fill=TEXT)
    names = ["gas", "brake", "handbrake", "door", "horn", "aim", "bullet", "fist", "jump", "run", "phone", "cover", "crouch", "reload", "radio", "menu"]
    for i, name in enumerate(names):
        x = 43+i*171
        d.ellipse((x, 1517, x+65, 1582), fill="#242424")
        icon = Image.open(ASSETS/f"assets/touch-icons/{name}.png").convert("RGBA").resize((65, 65), Image.Resampling.LANCZOS)
        sheet.paste(icon, (x, 1517), icon)
        d.text((x+73, 1539), name.replace("handbrake", "hand\nbrake"), font=ImageFont.truetype(font_path, 16), fill=MUTED)
    destination = args.output/"XHYN-IV-r2-test2-ui-preview.png"
    sheet.save(destination, optimize=True)
    print(destination)


if __name__ == "__main__":
    main()
