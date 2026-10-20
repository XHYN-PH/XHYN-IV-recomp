#!/bin/bash
# Power/heat sample of the running game over N seconds (default 10): CPU time
# the process used (in cores), the busiest threads, average clock of each CPU
# cluster, GPU clock/busy/temperature, and the frame rate.
#   power_sample.sh [seconds] [label]
set -uo pipefail
. "$(dirname "$0")/env.sh"
SECONDS_ARG="${1:-10}"
LABEL="${2:-}"
P=$(adbs shell pidof "$GAME_PROC" | tr -d '\r')
[ -n "$P" ] || { echo "game not running"; exit 1; }
adbs shell "
  cpu() { awk '{print \$14+\$15}' /proc/$P/stat; }
  threads() { for t in /proc/$P/task/*; do echo \"\$(awk '{print \$14+\$15}' \$t/stat) \$(tr ' ' _ < \$t/comm)\"; done; }
  a=\$(cpu); threads > /data/local/tmp/ps_a.txt
  f0=0; f4=0; f7=0; n=0; gb=0
  end=\$((\$(date +%s) + $SECONDS_ARG))
  while [ \$(date +%s) -lt \$end ]; do
    f0=\$((f0 + \$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq)))
    f4=\$((f4 + \$(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq)))
    f7=\$((f7 + \$(cat /sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq)))
    gb=\$((gb + \$(cut -d' ' -f1 /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null || echo 0)))
    n=\$((n + 1)); sleep 0.5
  done
  b=\$(cpu); threads > /data/local/tmp/ps_b.txt
  hz=\$(getconf CLK_TCK 2>/dev/null || echo 100)
  echo \"cpu-cores=\$(( (b - a) * 100 / hz / $SECONDS_ARG ))% little=\$((f0 / n / 1000))MHz big=\$((f4 / n / 1000))MHz prime=\$((f7 / n / 1000))MHz gpu-busy=\$((gb / n))% gpu-clk=\$((\$(cat /sys/class/kgsl/kgsl-3d0/gpuclk) / 1000000))MHz gpu-temp=\$((\$(cat /sys/class/kgsl/kgsl-3d0/temp) / 1000))C\"
  paste /data/local/tmp/ps_a.txt /data/local/tmp/ps_b.txt | awk -v hz=\$hz -v s=$SECONDS_ARG '{d=\$3-\$1; if (d>0) printf \"%d%% %s\n\", d*100/hz/s, \$2}' | sort -rn | head -5 | tr '\n' ' '; echo
" | sed "s/^/[$LABEL] /"
bash "$(dirname "$0")/fps.sh" 6 2>&1 | head -1 | sed "s/^/[$LABEL] /"
