#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/log"
TERMINAL_LOG_DIR="$LOG_DIR/terminal"
VU_LOG_DIR="$LOG_DIR/vus"
SCRIPT="$SCRIPT_DIR/steady_4083_rampup_until_upload.k6.js"
TIMESTAMP="$(TZ=Asia/Seoul date '+%Y%m%d-%H%M%S')"

export TARGET_VUS="${TARGET_VUS:-300}"
export RAMP_UP_DURATION="${RAMP_UP_DURATION:-1m}"
export HOLD_DURATION="${HOLD_DURATION:-0s}"
export RAMP_DOWN_DURATION="${RAMP_DOWN_DURATION:-0s}"
export LOGGER_DURATION="${LOGGER_DURATION:-1m}"
export VU_LOG_INTERVAL_SECONDS="${VU_LOG_INTERVAL_SECONDS:-15}"

TERMINAL_LOG="$TERMINAL_LOG_DIR/k6-300-rampup-special-$TIMESTAMP.log"
VU_LOG="$VU_LOG_DIR/k6-300-rampup-vu-$TIMESTAMP.log"

mkdir -p "$TERMINAL_LOG_DIR" "$VU_LOG_DIR"

echo "[RUN] script=$SCRIPT"
echo "[RUN] special_log=$TERMINAL_LOG"
if [[ "$VU_LOG_INTERVAL_SECONDS" != "0" ]]; then
  echo "[RUN] vu_log=$VU_LOG"
fi
echo "[RUN] vu_log_interval=${VU_LOG_INTERVAL_SECONDS}s"

set +e
export K6_SUMMARY_LOG="$TERMINAL_LOG"
k6 run \
  -e BASE_URL="${BASE_URL:-}" \
  -e API_PREFIX="${API_PREFIX:-}" \
  -e AUDIO_FILE="${AUDIO_FILE:-}" \
  -e AUDIO_CONTENT_TYPE="${AUDIO_CONTENT_TYPE:-}" \
  -e TARGET_VUS="$TARGET_VUS" \
  -e RAMP_UP_DURATION="$RAMP_UP_DURATION" \
  -e HOLD_DURATION="$HOLD_DURATION" \
  -e RAMP_DOWN_DURATION="$RAMP_DOWN_DURATION" \
  -e LOGGER_DURATION="$LOGGER_DURATION" \
  -e VU_LOG_INTERVAL_SECONDS="$VU_LOG_INTERVAL_SECONDS" \
  -e SLEEP_SECONDS="${SLEEP_SECONDS:-}" \
  -e K6_SUMMARY_LOG="$K6_SUMMARY_LOG" \
  --console-output "$VU_LOG" \
  "$SCRIPT"
K6_STATUS="$?"
set -e

echo "[DONE] special_log=$TERMINAL_LOG"
if [[ "$VU_LOG_INTERVAL_SECONDS" != "0" ]]; then
  echo "[DONE] vu_log=$VU_LOG"
fi
exit "$K6_STATUS"
