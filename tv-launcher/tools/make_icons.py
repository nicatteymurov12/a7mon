#!/usr/bin/env python3
"""Generates the launcher icon and the 320x180 TV banner (res/drawable-xhdpi)."""
import os, sys
from PIL import Image, ImageDraw, ImageFont

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res", "drawable-xhdpi")
os.makedirs(out, exist_ok=True)

def gradient(w, h, a, b):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / max(1, w - 1)) * 0.6 + (y / max(1, h - 1)) * 0.4
            px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return img

def tv_glyph(d, cx, cy, s, color):
    w, h = s * 1.6, s
    d.rounded_rectangle([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], radius=s * 0.14, outline=color, width=max(2, int(s * 0.11)))
    d.polygon([(cx - s * 0.18, cy - s * 0.24), (cx - s * 0.18, cy + s * 0.24), (cx + s * 0.26, cy)], fill=color)
    d.line([cx - s * 0.35, cy + h / 2 + s * 0.22, cx + s * 0.35, cy + h / 2 + s * 0.22], fill=color, width=max(2, int(s * 0.11)))

def font(size):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
              "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"]:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

a, b = (29, 78, 216), (14, 165, 233)

icon = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
g = gradient(96, 96, a, b)
mask = Image.new("L", (96, 96), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, 95, 95], radius=22, fill=255)
icon.paste(g, (0, 0), mask)
tv_glyph(ImageDraw.Draw(icon), 48, 44, 34, (255, 255, 255))
icon.save(os.path.join(out, "ic_launcher.png"))

banner = gradient(320, 180, a, b)
d = ImageDraw.Draw(banner)
tv_glyph(d, 70, 84, 46, (255, 255, 255))
d.text((128, 62), "Rahat TV", font=font(40), fill=(255, 255, 255))
d.text((130, 112), "TV menyusu", font=font(20), fill=(220, 235, 255))
banner.save(os.path.join(out, "banner.png"))
print("icons ->", os.path.normpath(out))
