"""Average GPU milliseconds per frame by pass from a native-performance-latest.log.

usage: gpu_profile_summary.py profile.log [other.log]   (two logs: side by side)
"""
import collections
import re
import sys


def load(path):
    total = collections.defaultdict(float)
    counts = collections.defaultdict(float)
    frames = set()
    for line in open(path, encoding="utf-8", errors="replace"):
        if "domain=gpu" not in line:
            continue
        fields = dict(re.findall(r"(\S+)=(\S+)", line))
        frames.add(fields["frame"])
        total[fields["name"]] += int(fields["ticks"]) * float(fields["timestamp-period-ns"]) / 1e6
        counts[fields["name"]] += int(fields["count"])
    n = max(1, len(frames))
    return {k: v / n for k, v in total.items()}, {k: v / n for k, v in counts.items()}, len(frames)


runs = [load(p) for p in sys.argv[1:]]
names = sorted({k for ms, _, _ in runs for k in ms}, key=lambda k: -runs[0][0].get(k, 0))
print("frames " + "  ".join(str(f) for _, _, f in runs))
for name in names:
    values = [ms.get(name, 0.0) for ms, _, _ in runs]
    if max(values) < 0.05:
        continue
    line = f"{name:<32}" + "".join(f" {v:7.2f}" for v in values)
    if len(values) == 2:
        line += f"  {values[1] - values[0]:+6.2f}"
    line += f"   x{runs[0][1].get(name, 0):.0f}"
    print(line)
