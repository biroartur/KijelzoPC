# Renders wallpaper/kijelzo-wallpaper.jpg (1920x1080). Needs Pillow and macOS fonts (Georgia, Avenir Next).
# Usage: python3 tools/make_wallpaper.py wallpaper/kijelzo-wallpaper.jpg
import math
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1]
W, H = 1920, 1080
S = 2  # supersample
w, h = W * S, H * S
AMBER = (255, 200, 0)

# Base: near-black with a slightly lighter blue-grey core towards the left-centre
base = Image.new("RGB", (w, h))
px = base.load()
cx, cy = w * 0.32, h * 0.52
maxd = math.hypot(w, h) * 0.75
for y in range(h):
    for x in range(0, w):
        d = min(1.0, math.hypot(x - cx, (y - cy) * 1.25) / maxd)
        t = 1 - d * d
        px[x, y] = (int(7 + 13 * t), int(8 + 15 * t), int(11 + 20 * t))

# Warm amber glow, lower right
glow = Image.new("L", (w, h), 0)
ImageDraw.Draw(glow).ellipse([w * 0.52, h * 0.38, w * 1.08, h * 1.15], fill=60)
glow = glow.filter(ImageFilter.GaussianBlur(260 * S / 2))
base.paste(Image.new("RGB", (w, h), (120, 72, 0)), (0, 0), glow)

layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
draw = ImageDraw.Draw(layer)

# Large faint serif hymn number, like the display shows it
serif = ImageFont.truetype("/System/Library/Fonts/Supplemental/Georgia.ttf", 560 * S)
num = "123"
box = draw.textbbox((0, 0), num, font=serif)
nx = w - (box[2] - box[0]) - 150 * S
ny = h * 0.20 - box[1]
draw.text((nx, ny), num, font=serif, fill=AMBER + (20,))

# Music staff: five thin, gently waving lines across the lower third
for i in range(5):
    pts = []
    y0 = h * 0.70 + i * 22 * S
    for x in range(0, w + 8, 8):
        y = y0 + math.sin(x / w * math.pi * 1.6 + 0.6) * 46 * S + math.sin(x / w * math.pi * 4.1) * 8 * S
        pts.append((x, y))
    draw.line(pts, fill=AMBER + (30,), width=2 * S)

# Wordmark
title_font = ImageFont.truetype("/System/Library/Fonts/Avenir Next.ttc", 132 * S, index=5)
tag_font = ImageFont.truetype("/System/Library/Fonts/Avenir Next.ttc", 26 * S, index=2)
tx, ty = 150 * S, h * 0.37
draw.text((tx, ty), "Kijelző", font=title_font, fill=(236, 233, 226, 235))
tb = draw.textbbox((tx, ty), "Kijelző", font=title_font)
ly = tb[3] + 34 * S
draw.line([(tx + 6 * S, ly), (tx + 96 * S, ly)], fill=AMBER + (220,), width=3 * S)
tag = "ÉNEKSZÁMOK"
x = tx + 6 * S
for ch in tag:
    draw.text((x, ly + 26 * S), ch, font=tag_font, fill=AMBER + (190,))
    x += draw.textlength(ch, font=tag_font) + 7 * S

img = Image.alpha_composite(base.convert("RGBA"), layer).convert("RGB")
img = img.resize((W, H), Image.LANCZOS)

# Light noise against gradient banding
random.seed(7)
p = img.load()
for y in range(H):
    for x in range(W):
        n = random.randint(-2, 2)
        r, g, b = p[x, y]
        p[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)))

img.save(OUT, "JPEG", quality=92, optimize=True, progressive=True)
print("saved", OUT, img.size)
