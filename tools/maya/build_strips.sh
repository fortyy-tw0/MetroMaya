#!/usr/bin/env bash
# Rebuild every flap strip from the pose PNGs. Run after editing any pose.
# Needs Python 3 with numpy, scipy and Pillow (WebP support).
set -euo pipefail
cd "$(dirname "$0")"
IMG=../../src/main/resources/static/img
python3 flap_hero.py
python3 flap_estimator.py "$IMG/maya-hey-you.png" "$IMG/maya-hey-fly.webp"
python3 flap_estimator.py src/maya-hey-you-wink.png "$IMG/maya-hey-fly-wink.webp"
python3 flap_cta.py
