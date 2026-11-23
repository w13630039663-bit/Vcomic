# -*- coding: utf-8 -*-
"""VComic 应用图标生成器：512 基准矢量逐档重绘，禁位图缩放。

规格来源 docs/designs/app-icon-redesign-2026-11-20/design-tokens.md。
ICO 手写容器：≤64px 档用 BMP 条目（老 win32 API 可取），128/256 用 PNG 条目。
"""
import math
import struct
import sys
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw

BLACK = (10, 10, 10, 255)     # icon.figure  #0A0A0A
WHITE = (255, 255, 255, 255)  # icon.board   #FFFFFF
EDGE = (236, 239, 244, 255)   # icon.edge    #ECEFF4

BASE = 512.0
BOARD_RX = 116.0
EDGE_W = 3.0
V_PTS = [(168.0, 148.0), (256.0, 318.0), (344.0, 148.0)]
V_W = 52.0
SPINE_X0, SPINE_Y0, SPINE_X1, SPINE_Y1 = 247.0, 342.0, 265.0, 380.0
SPINE_RX = 9.0

# 书页贝塞尔命令（左页；右页镜像）
PAGE_L = [
    ("M", (256.0, 336.0), None),
    ("C", (240.0, 312.0), (202.0, 300.0), (162.0, 300.0)),
    ("C", (122.0, 300.0), (98.0, 312.0), (96.0, 330.0)),
    ("C", (94.0, 344.0), (104.0, 352.0), (120.0, 350.0)),
    ("C", (160.0, 344.0), (212.0, 352.0), (256.0, 378.0)),
    ("Z", None, None),
]
PAGE_R = [
    ("M", (256.0, 336.0), None),
    ("C", (272.0, 312.0), (310.0, 300.0), (350.0, 300.0)),
    ("C", (390.0, 300.0), (414.0, 312.0), (416.0, 330.0)),
    ("C", (418.0, 344.0), (408.0, 352.0), (392.0, 350.0)),
    ("C", (352.0, 344.0), (300.0, 352.0), (256.0, 378.0)),
    ("Z", None, None),
]

# 16px 生死线补偿：书脊白条是唯一可能消失的结构，小档正向加宽（规格表）
SPINE_BOOST = {16: 2.4, 24: 2.0, 32: 1.6, 48: 1.3, 64: 1.3}


def bezier_pts(p0, p1, p2, p3, n=28):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1.0 - t
        x = mt**3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def path_to_poly(cmds, k):
    poly = []
    cur = None
    for cmd in cmds:
        c = cmd[0]
        if c == "M":
            cur = cmd[1]
            poly.append((cur[0] * k, cur[1] * k))
        elif c == "C":
            p1, p2, p3 = cmd[1], cmd[2], cmd[3]
            for x, y in bezier_pts(cur, p1, p2, p3)[1:]:
                poly.append((x * k, y * k))
            cur = p3
    return poly


def draw_icon(s):
    k = s / BASE
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = max(1, round(BOARD_RX * k))

    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=WHITE)
    if s >= 64:
        ew = max(1, round(EDGE_W * k))
        half = ew // 2
        d.rounded_rectangle(
            [half, half, s - 1 - half, s - 1 - half],
            radius=max(1, r - half), outline=EDGE, width=ew,
        )

    # V：圆头圆拐折线
    w = max(1, round(V_W * k))
    pts = [(x * k, y * k) for x, y in V_PTS]
    d.line(pts, fill=BLACK, width=w, joint="curve")
    cap = w / 2.0
    for ex, ey in (pts[0], pts[-1]):
        d.ellipse([ex - cap, ey - cap, ex + cap, ey + cap], fill=BLACK)

    # 书页
    d.polygon(path_to_poly(PAGE_L, k), fill=BLACK)
    d.polygon(path_to_poly(PAGE_R, k), fill=BLACK)

    # 书脊白条：中心对称加宽补偿
    boost = SPINE_BOOST.get(s, 1.0)
    sw = max(round(12.0 * k * boost), 1)
    cx = (SPINE_X0 + SPINE_X1) / 2.0
    x0 = round(cx * k - sw / 2.0)
    x1 = round(cx * k + sw / 2.0)
    y0 = round(SPINE_Y0 * k)
    y1 = round(SPINE_Y1 * k)
    sr = max(1, round(min(sw / 2.0, SPINE_RX * k)))
    if x1 - x0 < 1:
        x1 = x0 + 1
    d.rounded_rectangle([x0, y0, x1, y1], radius=sr, fill=WHITE)
    return img


def bmp_entry(img):
    """ICO 的 BMP 条目：BITMAPINFOHEADER + BGRA 自下而上 + 全零 AND mask。"""
    w, h = img.size
    rgba = img.convert("RGBA").tobytes("raw", "BGRA")
    stride = w * 4
    rows = [rgba[i * stride:(i + 1) * stride] for i in range(h)]
    pixel = b"".join(reversed(rows))
    mask_stride = ((w + 31) // 32) * 4
    mask = b"\x00" * (mask_stride * h)
    header = struct.pack("<IiiHHIIiiII", 40, w, h * 2, 1, 32, 0, w * h * 4, 0, 0, 0, 0)
    return header + pixel + mask


def png_entry(img):
    buf = BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def build_ico(images):
    """entries 升序排布；PNG 条目仅 128/256，其余 BMP 保兼容。"""
    entries = []
    for img in sorted(images, key=lambda m: m.size[0]):
        s = img.size[0]
        blob = png_entry(img) if s >= 128 else bmp_entry(img)
        entries.append((s, blob))
    count = len(entries)
    head = struct.pack("<HHH", 0, 1, count)
    offset = 6 + 16 * count
    dir_rows = b""
    body = b""
    for s, blob in entries:
        dim = 0 if s >= 256 else s
        dir_rows += struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(blob), offset)
        body += blob
        offset += len(blob)
    return head + dir_rows + body


def main():
    out_resources = Path(sys.argv[1])
    out_packaging = Path(sys.argv[2])
    out_render = Path(sys.argv[3])
    out_render.mkdir(parents=True, exist_ok=True)

    sizes = [16, 24, 32, 48, 64, 128, 256]
    imgs = {s: draw_icon(s) for s in sizes}

    ico_path = out_packaging / "vcomic-icon.ico"
    ico_path.write_bytes(build_ico(list(imgs.values())))

    imgs[512] = draw_icon(512)
    imgs[512].save(out_resources / "vcomic_icon.png")
    imgs[512].save(out_packaging / "vcomic-icon-transparent.png")

    for s in sizes + [512]:
        imgs[s].save(out_render / f"vcomic-{s}.png")
    print("OK", ico_path, out_resources / "vcomic_icon.png")


if __name__ == "__main__":
    main()
