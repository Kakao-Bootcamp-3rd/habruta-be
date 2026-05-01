#!/usr/bin/env bash
set -euo pipefail
unset LC_ALL

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/log"
TERMINAL_LOG_DIR="$LOG_DIR/terminal"
VU_LOG_DIR="$LOG_DIR/vus"
SCRIPT="$SCRIPT_DIR/steady_4083_rampup_until_upload.k6.js"
TIMESTAMP="$(TZ=Asia/Seoul date '+%Y%m%d-%H%M%S')"
TERMINAL_LOG="$TERMINAL_LOG_DIR/k6-4083-rampup-special-$TIMESTAMP.log"
VU_LOG="$VU_LOG_DIR/k6-4083-rampup-vu-$TIMESTAMP.log"

mkdir -p "$TERMINAL_LOG_DIR" "$VU_LOG_DIR"
export VU_LOG_INTERVAL_SECONDS="${VU_LOG_INTERVAL_SECONDS:-15}"

echo "[RUN] script=$SCRIPT"
echo "[RUN] special_log=$TERMINAL_LOG"
if [[ "$VU_LOG_INTERVAL_SECONDS" != "0" ]]; then
  echo "[RUN] vu_log=$VU_LOG"
fi
echo "[RUN] vu_log_interval=${VU_LOG_INTERVAL_SECONDS}s"

set +e
# Terminal shows default k6 output (in-place progress bar)
# Summary/errors are saved via handleSummary in k6 script
export K6_SUMMARY_LOG="$TERMINAL_LOG"
k6 run \
  --console-output "$VU_LOG" \
  "$SCRIPT"
K6_STATUS="$?"
set -e

echo "[DONE] special_log=$TERMINAL_LOG"
if [[ "$VU_LOG_INTERVAL_SECONDS" != "0" ]]; then
  echo "[DONE] vu_log=$VU_LOG"
fi
exit "$K6_STATUS"
