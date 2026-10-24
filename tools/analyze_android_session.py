#!/usr/bin/env python3
"""Summarize an exported log ZIP without confusing publisher FPS with scanout.

Example: python3 tools/analyze_android_session.py session.zip --output report.json
Optional --compare other.zip checks recorded settings before comparing captures.
"""
from pathlib import Path
import argparse,csv,io,json,math,re,statistics,zipfile

def number(value):
    try:
        n=float(value)
        return n if math.isfinite(n) else None
    except (TypeError,ValueError):return None

def percentile(values,p):
    values=sorted(values)
    return values[max(0,math.ceil(len(values)*p)-1)] if values else None

def summarize(path):
    with zipfile.ZipFile(path) as archive:
        if sum(i.file_size for i in archive.infolist())>256*1024*1024:raise ValueError('Expanded report exceeds 256 MiB')
        def read(suffix):
            names=[n for n in archive.namelist() if n.endswith(suffix)]
            if len(names)>1:raise ValueError(f'Choose a single-session ZIP: multiple {suffix} entries')
            return archive.read(names[0]).decode('utf-8',errors='replace') if names else ''
        def rows(suffix):return list(csv.DictReader(io.StringIO(read(suffix))))
        runtime=read('.runtime.log');meta_text=read('native-performance-meta.json')
        meta=json.loads(meta_text) if meta_text else {}
        settings=dict(line.split('=',1) for line in read('.benchmark.settings.txt').splitlines() if '=' in line)
        measured=[r for r in rows('.benchmark.csv') if r.get('phase')=='measurement']
        # Window percentiles cannot be averaged into a whole-session percentile.
        maxes=[number(r.get('interval_max_ms')) for r in measured]
        p95s=[number(r.get('interval_p95_ms')) for r in measured]
        fps=[number(r.get('published_fps')) for r in measured]
        values=lambda xs:[x for x in xs if x is not None]
        slices=rows('native-performance-gpu-slices.csv')
        valid=[number(r.get('duration_ms')) for r in slices[1:] if r.get('status')=='approximate']
        valid=values(valid)
        frames=rows('native-performance-frames.csv')
        cpu=rows('native-performance-cpu.csv')
        texture=re.findall(r'content_reuploads=(\d+) mean_reupload_age_frames=([\d.]+)',runtime)
        evictions=re.findall(r'eviction_age=(\d+) pressure=(\d+) recovery=(\d+)',runtime)
        return {
            'source':Path(path).name,'device':meta.get('device','unknown'),'settings':settings,
            'benchmark':{'status':read('.benchmark.status.txt').strip(),'measurement_windows':len(measured),
                'window_publisher_fps_min':min(values(fps),default=None),'window_publisher_fps_max':max(values(fps),default=None),
                'worst_window_p95_ms':max(values(p95s),default=None),'maximum_interval_ms':max(values(maxes),default=None)},
            'gpu':{'approximate_envelope_samples':len(valid),'median_ms':statistics.median(valid) if valid else None,
                'p95_ms':percentile(valid,.95),'timing':meta.get('gpu_timing','unavailable')},
            'cpu':{'detailed_rows':len(cpu),'game_function_sampling_available':False},
            'presentation':{'physical_scanout_measured':meta.get('physical_scanout_measured',False),
                'suboptimal_observations':sum(r.get('paint_result')=='1000001003' for r in frames)},
            'textures':{'last_age_pressure_recovery_evictions':list(map(int,evictions[-1])) if evictions else [],
                'detected_content_reuploads':int(texture[-1][0]) if texture else None},
            'limits':['Publisher FPS is not display FPS.','GPU envelope timing is not isolated shader cost.',
                'Window percentiles cannot be combined into a session percentile.','No CPU hotspot or FPS gain is inferred from these summaries.']}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('session',type=Path)
    parser.add_argument('--compare',type=Path);parser.add_argument('--output',type=Path)
    args=parser.parse_args();report=summarize(args.session)
    if args.compare:
        other=summarize(args.compare)
        differences={k:[report['settings'].get(k),other['settings'].get(k)] for k in report['settings'].keys()|other['settings'].keys() if report['settings'].get(k)!=other['settings'].get(k)}
        report={'first':report,'second':other,'settings_differences':differences,
            'comparison_limit':'Comparable settings alone do not verify identical routes, save state, caches or temperature.'}
    text=json.dumps(report,indent=2,ensure_ascii=False)+'\n'
    if args.output:args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(text)
    else:print(text,end='')

if __name__=='__main__':main()
