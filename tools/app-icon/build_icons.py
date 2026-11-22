"""Builds the browser-tab and home-screen icons from the official MetroMaya logo.
Run: python3 tools/app-icon/build_icons.py"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
IMG = ROOT / "src/main/resources/static/img"
logo = Image.open(Path(__file__).parent / "src/metromaya-logo.png").convert("RGB")


def full(size):
    return logo.resize((size, size), Image.LANCZOS)


# browser tab
full(32).save(IMG / "favicon-32.png", optimize=True)
full(48).save(ROOT / "src/main/resources/static/favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
# home screen
full(180).save(IMG / "apple-touch-icon.png", optimize=True)
full(192).save(IMG / "app-icon-192.png", optimize=True)
full(512).save(IMG / "app-icon-512.png", optimize=True)
# the logo is full-bleed and its eyes sit inside the central 80% safe zone, so Android's round mask keeps them
full(512).save(IMG / "app-icon-maskable.png", optimize=True)
