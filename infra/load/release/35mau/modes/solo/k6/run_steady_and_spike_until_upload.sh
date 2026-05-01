#!/usr/bin/env bash
set -euo pipefail
unset LC_ALL

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/log"
SCRIPT="$SCRIPT_DIR/steady_and_spike_until_upload.k6.js"
TIMESTAMP="$(TZ=Asia/Seoul date '+%Y%m%d-%H%M%S')"
CONSOLE_LOG="$LOG_DIR/k6-steady-and-spike-console-$TIMESTAMP.log"
VU_LOG_PREFIX="$LOG_DIR/k6-steady-and-spike-vu"

mkdir -p "$LOG_DIR"

echo "[RUN] script=$SCRIPT"
echo "[RUN] console_log=$CONSOLE_LOG"
echo "[RUN] vu_log_prefix=$VU_LOG_PREFIX"
echo "[RUN] VU log format: k6 console line, split by KST minute"

split_vu_logs() {
  awk -v log_dir="$LOG_DIR" '
  /\[VU\]/ {
    minute = ""
    if (match($0, /time="[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}/)) {
      minute = substr($0, RSTART + 6, 16)
    } else if (match($0, /\[VU\] [0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}/)) {
      minute = substr($0, RSTART + 5, 16)
    }

    if (minute != "") {
      gsub(/[-:T ]/, "", minute)
      vu_log = log_dir "/k6-steady-and-spike-vu-" minute ".log"
      print $0 >> vu_log
      close(vu_log)
    }
  }
  ' "$CONSOLE_LOG"
}

set +e
k6 run --console-output "$CONSOLE_LOG" "$SCRIPT"
K6_STATUS="$?"
set -e
split_vu_logs
echo "[DONE] console_log=$CONSOLE_LOG"
echo "[DONE] vu_logs=$VU_LOG_PREFIX-YYYYMMDDHHMM.log"
exit "$K6_STATUS"
