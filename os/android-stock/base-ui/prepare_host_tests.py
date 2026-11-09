#!/usr/bin/env python3
"""Convert the assembled DEX for host-only Robolectric tests; never packages JVM code."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from build_base_ui import DEFAULTS, english_args
from preflight import validate_base


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ('base','dex','dex2jar','sdk','out'):
        parser.add_argument('--'+key,type=Path,required=True)
    args=parser.parse_args()
    validate_base(args.base)
    out=args.out.resolve(); out.mkdir(parents=True,exist_ok=True)
    raw, framed = out/'stock-raw.jar', out/'stock-framed.jar'
    subprocess.run(['bash',str(args.dex2jar/'d2j-dex2jar.sh'),'--force','--output',str(raw),str(args.dex)],check=True)
    tools=out/'tools';tools.mkdir(exist_ok=True)
    subprocess.run(['javac','--release','17','-cp',str(args.dex2jar/'lib/*'),'-d',str(tools),str(ROOT/'base-ui/host-tests/tools/Reframe.java')],check=True)
    classpath=os.pathsep.join(map(str,(tools,raw,args.dex2jar/'lib/*',args.sdk/'platforms/android-35/android.jar')))
    subprocess.run(['java','-cp',classpath,'Reframe',str(raw),str(framed)],check=True)
    assets=out/'assets';assets.mkdir(exist_ok=True)
    with zipfile.ZipFile(args.base) as archive:
        for name in sorted(DEFAULTS):
            (assets/Path(name).name).write_bytes(english_args(archive.read(name)))
    print(f'Host-only JAR: {framed}\nHost-only assets: {assets}')


if __name__=='__main__': main()
