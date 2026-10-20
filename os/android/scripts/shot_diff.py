"""Compares benchmark screenshots against a reference run.

usage: shot_diff.py <bench dir> <reference label> <label> [label ...]
Prints the mean absolute difference and the share of pixels that differ by
more than 24 levels, ignoring the FPS counter corner, and writes
<label>/diff.png (differences amplified) for a look.
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageStat

root, reference = sys.argv[1], sys.argv[2]
base = Image.open(os.path.join(root, reference, "shot.png")).convert("RGB")
mask_box = (0, 0, base.width // 8, base.height // 12)  # FPS overlay
for label in sys.argv[3:]:
    path = os.path.join(root, label, "shot.png")
    if not os.path.exists(path):
        print(f"{label:<14} no screenshot")
        continue
    shot = Image.open(path).convert("RGB")
    if shot.size != base.size:
        shot = shot.resize(base.size)
    diff = ImageChops.difference(base, shot)
    ImageDraw.Draw(diff).rectangle(mask_box, fill=(0, 0, 0))
    gray = diff.convert("L")
    mean = ImageStat.Stat(gray).mean[0]
    histogram = gray.histogram()
    changed = sum(histogram[25:]) / (gray.width * gray.height)
    gray.point(lambda v: min(255, v * 8)).save(os.path.join(root, label, "diff.png"))
    print(f"{label:<14} mean {mean:5.2f}  changed {changed * 100:5.2f}%")
