#!/usr/bin/env bash
# Redraw the gold cap piping on every pose from the untouched sources in src/before-piping/,
# writing the poses into static/img (and the wink source into src/), then rebuild the strips.
# hat_piping.py is NOT idempotent: always run it on src/before-piping/, never on its own output.
set -euo pipefail
cd "$(dirname "$0")"
IMG=../../src/main/resources/static/img
for p in hello ta-da whoa hey-you hehehe lets-go; do
  python3 hat_piping.py "src/before-piping/maya-$p.png" "$IMG/maya-$p.png"
done
python3 hat_piping.py src/before-piping/maya-hey-you-wink.png src/maya-hey-you-wink.png
./build_strips.sh
