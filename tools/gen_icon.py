# -*- coding: utf-8 -*-
"""生成考试成绩管理系统的应用图标: app.ico(多尺寸) 与 app.png(256)。
主题: 蓝色圆角渐变底 + 白色学位帽(考试/学业) + 金黄流苏。
"""
from PIL import Image, ImageDraw

S = 256  # 主画布

def lerp(a, b, t):
    return [int(a[i] + (b[i] - a[i]) * t) for i in range(3)]

def rounded_gradient(size, radius, c_top, c_bottom):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        t = y / (size - 1)
        r, g, b = lerp(c_top, c_bottom, t)
        for x in range(size):
            # rounded corner mask
            dx = min(x, size - 1 - x)
            dy = min(y, size - 1 - y)
            if dx < radius and dy < radius:
                cx, cy = radius, radius
                if (dx - cx) ** 2 + (dy - cy) ** 2 > radius ** 2:
                    continue
            px[x, y] = (r, g, b, 255)
    # 内描边
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2, 2, size - 3, size - 3], radius=radius - 2,
                        outline=(255, 255, 255, 90), width=3)
    return img

def grad_cap(d, scale=1.0):
    """在 (0,0)-(S,S) 上绘制白色学位帽, scale 用于整体缩放。"""
    # 帽板(平行四边形)
    p1 = (60, 116); p2 = (182, 84); p3 = (212, 108); p4 = (90, 140)
    d.polygon([p1, p2, p3, p4], fill=(255, 255, 255, 255))
    # 帽顶衬(板下的梯形)
    d.polygon([(106, 128), (156, 118), (146, 158), (104, 152)], fill=(235, 238, 244, 255))
    # 帽沿深色(底部一条)
    d.polygon([(104, 152), (146, 158), (142, 166), (102, 162)], fill=(200, 205, 215, 255))
    # 中心扣(金黄)
    r = 9
    cx, cy = 140, 104
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 196, 0, 255))
    # 流苏(金黄线条)
    d.line([(147, 110), (188, 140)], fill=(255, 196, 0, 255), width=5)
    d.ellipse([182, 134, 198, 150], fill=(255, 196, 0, 255))

def render(size):
    img = rounded_gradient(size, radius=int(size * 0.22), c_top=(30, 136, 229), c_bottom=(13, 71, 161))
    d = ImageDraw.Draw(img)
    k = size / S
    # 整体往左略移、垂直居中
    off = (0, 6)
    def pts(l): return [(int((x) * k + off[0]), int(y * k + off[1])) for x, y in l]
    # 帽板
    d.polygon(pts([(74, 118), (188, 86), (214, 110), (100, 142)]), fill=(255, 255, 255, 255))
    # 板下梯形
    d.polygon(pts([(114, 128), (160, 120), (152, 156), (116, 152)]), fill=(236, 240, 245, 255))
    d.polygon(pts([(116, 152), (152, 156), (148, 163), (114, 160)]), fill=(205, 210, 220, 255))
    # 中心扣
    r = 9 * k
    cx, cy = 146 * k, 106 * k
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 196, 0, 255))
    # 流苏
    d.line([(152 * k, 112 * k), (196 * k, 142 * k)], fill=(255, 196, 0, 255), width=max(3, int(5 * k)))
    er = 8 * k
    d.ellipse([196 * k - er, 136 * k - er, 196 * k + er, 136 * k + er], fill=(255, 196, 0, 255))
    # 底部高光带(营造玻璃感)
    d.rounded_rectangle([10 * k, size - 26 * k, size - 10 * k, size - 20 * k],
                        radius=8 * k, fill=(255, 255, 255, 45))
    return img

if __name__ == "__main__":
    import os
    here = os.path.dirname(os.path.abspath(__file__))
    sizes = [16, 24, 32, 48, 64, 128, 256]
    imgs = [render(s) for s in sizes]
    png_path = os.path.join(here, "app.png")
    ico_path = os.path.join(here, "app.ico")
    imgs[-1].save(png_path)
    # 多分辨率 ico：以 256 原图缩放出各尺寸，确保标题栏/托盘/桌面均清晰
    imgs[-1].save(ico_path, sizes=[(s, s) for s in sizes])
    print("generated", ico_path, png_path)
