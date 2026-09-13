#!/usr/bin/env bash
set -euo pipefail
# Run Linux validation without an X server.
export XDG_RUNTIME_DIR
XDG_RUNTIME_DIR=$(mktemp -d)
export WAYLAND_DISPLAY=modtale-validation
weston --backend=headless-backend.so --renderer=pixman --socket="$WAYLAND_DISPLAY" --idle-time=0 \
  --width=1600 --height=1000 --no-config > "$XDG_RUNTIME_DIR/weston.log" 2>&1 &
weston_pid=$!
trap 'kill "$weston_pid" 2>/dev/null || true; cat "$XDG_RUNTIME_DIR/weston.log"; rm -rf "$XDG_RUNTIME_DIR"' EXIT
for attempt in {1..100}; do
  if [ -S "$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY" ]; then break; fi
  kill -0 "$weston_pid"
  sleep 0.1
done
test -S "$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY"
env -u DISPLAY GDK_BACKEND=wayland ./gradlew "$@"
