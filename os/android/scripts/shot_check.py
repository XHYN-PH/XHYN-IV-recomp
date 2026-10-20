"""Checks a regression screenshot for a broken image, and against a reference.

usage: shot_check.py <shot.png> [<reference shot.png>]

The save point is a live scene (traffic, people, clock), so pixels differ a
lot between any two runs of the same build. This looks at what a rendering
bug changes instead: a black, flat or single-colour frame, and the frame's
distribution of brightness and colour against the reference (histogram
intersection, 1.0 = identical distributions). Prints one line of numbers and
"broken: <reason>" when the frame itself is unusable.
"""
import sys

from PIL import Image, ImageStat


def load(path):
    image = Image.open(path).convert("RGB")
    # Ignore the FPS overlay corner and work at a size noise averages out in.
    width, height = image.size
    image.paste((0, 0, 0), (0, 0, width // 8, height // 12))
    return image.resize((320, 180), Image.BILINEAR)


def histogram(image):
    hsv = image.convert("HSV")
    bins = []
    for channel, count in ((hsv.getchannel(0), 16), (hsv.getchannel(1), 8), (image.convert("L"), 16)):
        raw = channel.histogram()
        step = 256 // count
        values = [sum(raw[i * step:(i + 1) * step]) for i in range(count)]
        total = float(sum(values)) or 1.0
        bins.append([v / total for v in values])
    return bins


def intersection(a, b):
    return sum(sum(min(x, y) for x, y in zip(ca, cb)) for ca, cb in zip(a, b)) / len(a)


shot = load(sys.argv[1])
luma = shot.convert("L")
stat = ImageStat.Stat(luma)
mean, spread = stat.mean[0], stat.stddev[0]
colors = len(set(shot.resize((64, 36)).getdata()))
line = f"luma {mean:.1f} spread {spread:.1f} colours {colors}"
broken = []
if mean < 8:
    broken.append("black frame")
if spread < 6:
    broken.append("flat frame")
if colors < 40:
    broken.append("almost single-coloured")
if len(sys.argv) > 2:
    line += f" similarity {intersection(histogram(shot), histogram(load(sys.argv[2]))):.3f}"
print(line + ("  broken: " + ", ".join(broken) if broken else ""))
