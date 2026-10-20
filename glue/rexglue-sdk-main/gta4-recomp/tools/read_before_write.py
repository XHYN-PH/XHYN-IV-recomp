"""Lists recompiled functions that read a non-volatile register, cr field, ctr
or xer before writing it (a linear scan of the generated code).

With cr/ctr/xer/non_volatile_as_local those registers are C++ locals, so a
function that reads one it never set - a piece the analyzer split off its
owner, or an SEH funclet - would see zero. Mark such functions
share_registers in gta4_config.toml. Run after every code generation.

Adapted from lee_antes.py ("read before") of nfsmw-nx by StevensND
(https://github.com/StevensND/nfsmw-nx).

usage: read_before_write.py <generated dir>
"""
import re,glob,sys
gen=sys.argv[1]
fn_re=re.compile(r'^DEFINE_REX_FUNC\((sub_[0-9A-F]+)\)')
tok=re.compile(r'(?<![.\w])(r(?:1[4-9]|2[0-9]|3[01])|f(?:1[4-9]|2[0-9]|3[01])|v(?:1[4-9]|2[0-9]|3[01]|6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])|cr[0-7]|ctr|xer)\b')
asg=re.compile(r'^([A-Za-z0-9_]+)(?:\.[A-Za-z0-9_\[\]]+)*\s*=[^=]')
res={}
for f in sorted(glob.glob(gen+'/gta4_recomp.*.cpp')):
    cur=None
    for ln in open(f,encoding='utf-8',errors='ignore'):
        m=fn_re.match(ln)
        if m: cur=m.group(1);written=set();bad={};continue
        if cur is None: continue
        if ln.startswith('}'):
            if bad: res[cur]=bad
            cur=None;continue
        s=ln.strip()
        if not s or s.startswith('//') or s.startswith('PPC') or s.startswith('uint') or s.startswith('REX_FUNC_PROLOGUE'): continue
        # The register sync around a call to a share_registers function copies
        # locals into ctx whether or not they were set: not a real read.
        if s.startswith('const auto s_') or (s.startswith('ctx.') and '= s_' in s): continue
        if s.startswith('REX_STORE') and re.search(r',\s*(r|f)\d+\.(u64|f64)\);$',s) and ('ctx.r1.' in s): continue  # prologue save
        s2=re.sub(r'compare<[^>]*>\([^;]*,\s*xer\)','',s)
        s2=re.sub(r'cr\d\.so = xer\.so;','',s2)
        mf=re.match(r'^(cr[0-7])\.setFromMask\(',s)
        if mf:
            written.add(mf.group(1)); continue  # vector compare result: a write
        mc=re.match(r'^(cr[0-7])\.compare',s)
        if mc:
            for r in tok.findall(re.sub(r',\s*xer\)',')',s[len(mc.group(1)):])):
                if r not in written and r not in bad: bad[r]=s[:100]
            written.add(mc.group(1)); continue
        ms=re.match(r'^simde_mm_store\w*\(\(?(?:simde__m128i\*\))?(v\d+)\.\w+,(.*)$',s2)
        if ms:
            rhs=ms.group(2)
            for r in tok.findall(rhs):
                if r not in written and r not in bad: bad[r]=s[:100]
            written.add(ms.group(1)); continue
        m2=asg.match(s2); lhs=None; rhs=s2
        if m2: lhs=m2.group(1); rhs=s2[m2.end()-1:]
        for r in tok.findall(rhs):
            if r not in written and r not in bad: bad[r]=s[:100]
        if lhs and tok.fullmatch(lhs): written.add(lhs)
        # calls through a function pointer to simd that write by reference: ignore
for n in sorted(res): print(n, ' '.join(sorted(res[n])), '|', list(res[n].values())[0])
print('TOTAL',len(res),file=sys.stderr)
