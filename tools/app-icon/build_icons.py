"""Builds the home-screen app icons from Maya's hello pose. Run: python3 tools/app-icon/build_icons.py"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
IMG = ROOT / "src/main/resources/static/img"
CREAM = (251, 247, 240, 255)

maya = Image.open(IMG / "maya-hello.png").convert("RGBA")
maya = maya.crop(maya.getbbox())


def icon(size, share):
    """Maya centred on cream; `share` is how much of the icon's width she takes (maskable icons need a safe zone)."""
    canvas = Image.new("RGBA", (size, size), CREAM)
    w = round(size * share)
    h = round(maya.height * w / maya.width)
    if h > size * share:
        h = round(size * share)
        w = round(maya.width * h / maya.height)
    m = maya.resize((w, h), Image.LANCZOS)
    canvas.alpha_composite(m, ((size - w) // 2, (size - h) // 2 + round(size * 0.02)))
    return canvas.convert("RGB")


icon(192, 0.78).save(IMG / "app-icon-192.png", optimize=True)
icon(512, 0.78).save(IMG / "app-icon-512.png", optimize=True)
icon(512, 0.6).save(IMG / "app-icon-maskable.png", optimize=True)
icon(180, 0.78).save(IMG / "apple-touch-icon.png", optimize=True)
