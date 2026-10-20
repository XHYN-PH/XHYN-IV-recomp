"""Joins the renderer's frame timeline with SurfaceFlinger's frame times.

SurfaceFlinger (--latency) gives per frame: desired present, actual present
and frame-ready (the GPU finished it), on CLOCK_MONOTONIC. The renderer's
"frame-timeline:" lines give, per guest frame, the guest present and the
offsets of the worker, handoff, recorder start, queue submit and publish end.
Each SurfaceFlinger frame is matched to the guest frame whose submit is the
latest before its frame-ready time. Frames shown 50 ms after the previous one
are "late"; the stage averages of late and on-time frames show which stage
grows.

usage: frame_timeline.py <sf.txt> <timeline.txt>
"""
import bisect
import sys

sf = []
for i, line in enumerate(open(sys.argv[1])):
    parts = line.split()
    if i == 0 or len(parts) < 3:
        continue
    desired, actual, ready = map(int, parts[:3])
    if 0 < actual < 9e18 and 0 < ready < 9e18:
        sf.append((desired, actual, ready))

frames = {}
for line in open(sys.argv[2]):
    for item in line.split("frame-timeline:", 1)[1].split():
        number, values = item.split(":")
        guest, *offsets = values.split(",")
        frames[int(number)] = (int(guest), [int(o) for o in offsets])
order = sorted(frames)
if not sf or not order:
    sys.exit(f"no data: {len(sf)} SurfaceFlinger frames, {len(order)} timeline frames")

submits = []  # (submit ns, frame)
for number in order:
    guest, offsets = frames[number]
    if offsets[3] >= 0:
        submits.append((guest + offsets[3] * 1000, number))
submits.sort()
submit_times = [s[0] for s in submits]

rows = []
previous_actual = previous_ready = None
for desired, actual, ready in sf:
    k = bisect.bisect_right(submit_times, ready) - 1
    if k < 0 or previous_actual is None:
        previous_actual, previous_ready = actual, ready
        continue
    number = submits[k][1]
    guest, (worker, handoff, recorder, submit, publish) = frames[number]
    previous_guest = frames.get(number - 1, (None,))[0]
    rows.append({
        "late": (actual - previous_actual) / 1e6 > 42,
        "guest_interval": (guest - previous_guest) / 1e6 if previous_guest else float("nan"),
        "guest_to_worker": worker / 1e3,
        "handoff_wait": (handoff - worker) / 1e3,
        "to_recorder": (recorder - handoff) / 1e3,
        "recording": (submit - recorder) / 1e3,
        "gpu_after_submit": (ready - (guest + submit * 1000)) / 1e6,
        "gpu_interval": (ready - previous_ready) / 1e6,
        "publish_after_submit": (publish - submit) / 1e3,
    })
    previous_actual, previous_ready = actual, ready

keys = ["guest_interval", "guest_to_worker", "handoff_wait", "to_recorder", "recording",
        "gpu_after_submit", "gpu_interval", "publish_after_submit"]
print(f"{len(rows)} frames joined, {sum(r['late'] for r in rows)} late (shown 50 ms after the previous)")
print("ms              " + " ".join(f"{k[:13]:>13}" for k in keys))
for label, selected in (("on-time", [r for r in rows if not r["late"]]), ("late", [r for r in rows if r["late"]])):
    if not selected:
        continue
    means = []
    for k in keys:
        values = [r[k] for r in selected if r[k] == r[k]]
        means.append(sum(values) / len(values) if values else float("nan"))
    print(f"{label:8s} n={len(selected):3d} " + " ".join(f"{m:13.1f}" for m in means))
print("late frames:")
for r in [r for r in rows if r["late"]][:15]:
    print("  " + " ".join(f"{r[k]:13.1f}" for k in keys))
