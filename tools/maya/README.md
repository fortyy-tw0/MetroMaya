# Maya image tools

These scripts build Maya's web assets in `src/main/resources/static/img/`. They need Python 3 with numpy, scipy and Pillow (with WebP support).

| Script | What it does |
|---|---|
| `build_strips.sh` | Rebuilds all four 16-frame flap strips from the pose PNGs. Run it after any pose edit. |
| `apply_hat_piping.sh` | Redraws the gold cap piping on every pose from `src/before-piping/`, then runs `build_strips.sh`. |
| `flap_hero.py` | `maya-ta-da.png` → `maya-fly-frames.webp` (the hero). |
| `flap_estimator.py SRC DST` | The pointing pose → `maya-hey-fly.webp`, and the wink source → `maya-hey-fly-wink.webp`. |
| `flap_cta.py` | `maya-hehehe.png` → `maya-hehe-fly.webp` (the Book a slot banner). |
| `hat_piping.py SRC DST` | Redraws the piping flush against the brown outlines. **Not idempotent:** only run it on `src/before-piping/`. |
| `thicken_rim.py SRC DST` | Thickens the cap's top outline. This was already applied to ta-da, hehehe and hey-you. |

Source files:
- `src/maya-hey-you-wink.png`: the pointing pose with her eye closed. The wink strip is built from it.
- `src/before-piping/`: every pose as it was before the piping fix.

Strips are built by bending the whole drawing: the body stays still and each wing rotates about its shoulder. They are never cut-out layers. The wink goes through exactly the same pipeline as the open-eye strip, so the two line up pixel for pixel.
