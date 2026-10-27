#!/usr/bin/env python3
"""Renders phone-resolution previews of the Pehchan AI UI.

The HTML below mirrors res/layout/activity_main.xml dp-for-dp (same colours,
radii, type sizes, icons) and is filled with REAL values: the bundled model is
run on a real photo, and the details/colours come from the same algorithm the
app uses. Screenshots are taken at 360x780dp @3x = 1080x2340px.
"""
import io
import json
import os
import subprocess
import urllib.request

import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

WORK = "/scratch/work"
IMG = os.environ.get("PHOTO", os.path.join(WORK, "testimg", "Golden_Retriever.jpg"))
OUT = os.path.join(WORK, "shots")
os.makedirs(OUT, exist_ok=True)

# ---------------------------------------------------------------- model run
labels = [l.strip() for l in open(os.path.join(WORK, "app/assets/labels.txt"), encoding="utf-8") if l.strip()]
interp = Interpreter(model_path=os.path.join(WORK, "app/assets/model.tflite"), num_threads=2)
interp.allocate_tensors()
inp = interp.get_input_details()[0]
out = interp.get_output_details()[0]

photo = Image.open(IMG).convert("RGB")
orig_w, orig_h = photo.size
resized = photo.copy()
max_side = max(resized.size)
if max_side > 1600:
    scale = 1600.0 / max_side
    resized = resized.resize((int(orig_w * scale), int(orig_h * scale)), Image.LANCZOS)

arr = np.array(photo.resize((224, 224), Image.BILINEAR), dtype=np.uint8)[None, ...]
interp.set_tensor(inp["index"], arr)
interp.invoke()
o = interp.get_tensor(out["index"])[0].astype(np.float32)
s, z = out["quantization"]
probs = (o - z) * s
order = probs.argsort()[::-1][:6]


def pretty(raw):
    t = raw.replace("_", " ").strip()
    if "," in t:
        a, b = [x.strip() for x in t.split(",", 1)]
        t = b if len(b) > len(a) else a
    return t


results = [(pretty(labels[i]), float(probs[i])) for i in order]

# ---- object detector (same preprocessing the app uses) ----------------------
coco = [l.strip() for l in open(os.path.join(WORK, "app/assets/coco_labels.txt"), encoding="utf-8") if l.strip()]
det = Interpreter(model_path=os.path.join(WORK, "app/assets/detect.tflite"), num_threads=2)
det.allocate_tensors()
din = det.get_input_details()[0]
douts = det.get_output_details()
b_i, c_i, s_i, n_i = [o["index"] for o in douts]

darr = np.array(photo.resize((300, 300), Image.BILINEAR), dtype=np.uint8)[None, ...]
det.set_tensor(din["index"], darr)
det.invoke()
dboxes = det.get_tensor(b_i)[0]
dcls = det.get_tensor(c_i)[0].astype(int)
dscr = det.get_tensor(s_i)[0]
dnum = int(det.get_tensor(n_i)[0])
raw = []
for i in range(dnum):
    if dscr[i] < 0.42:
        continue
    name = coco[dcls[i]] if 0 <= dcls[i] < len(coco) else "?"
    if name == "???":
        continue
    raw.append((name, float(dscr[i]), [float(x) for x in dboxes[i]]))
raw.sort(key=lambda d: -d[1])


def _iou(a, b):
    left, top = max(a[1], b[1]), max(a[0], b[0])
    right, bottom = min(a[3], b[3]), min(a[2], b[2])
    inter = max(0.0, right - left) * max(0.0, bottom - top)
    union = (a[3] - a[1]) * (a[2] - a[0]) + (b[3] - b[1]) * (b[2] - b[0]) - inter
    return 0.0 if union <= 0 else inter / union


detections = []
for cand in raw:
    if any(k[0] == cand[0] and _iou(k[2], cand[2]) > 0.10 for k in detections):
        continue
    detections.append(cand)
top_label, top_score = results[0]
others = results[1:5]

# ------------------------------------------------------- image facts (as app)
def image_facts(img):
    small = img.resize((44, max(1, round(44 * img.size[1] / img.size[0]))), Image.BILINEAR)
    px = np.array(small, dtype=np.int32).reshape(-1, 3)
    lum = (0.2126 * px[:, 0] + 0.7152 * px[:, 1] + 0.0722 * px[:, 2]) / 255.0
    avg = float(lum.mean())
    brightness = "Bright" if avg > 0.62 else ("Balanced" if avg > 0.34 else "Dark")
    keys = (px[:, 0] >> 5) << 10 | (px[:, 1] >> 5) << 5 | (px[:, 2] >> 5)
    buckets = {}
    for k, c in zip(keys, px):
        b = buckets.setdefault(int(k), [0, 0, 0, 0])
        b[0] += c[0]
        b[1] += c[1]
        b[2] += c[2]
        b[3] += 1
    ordered = sorted(buckets.values(), key=lambda b: -b[3])
    colors = []
    for b in ordered:
        n = b[3]
        if n < 3:
            continue
        r, g, bl = b[0] // n, b[1] // n, b[2] // n
        if any((((r - c[0]) ** 2 + (g - c[1]) ** 2 + (bl - c[2]) ** 2) ** 0.5) < 48 for c in colors):
            continue
        colors.append((r, g, bl))
        if len(colors) == 5:
            break
    return brightness, colors


brightness, colors = image_facts(resized)
file_bytes = os.path.getsize(IMG)


def human(n):
    if n < 1024:
        return "%d B" % n
    kb = n / 1024.0
    if kb < 1024:
        return "%.0f KB" % kb
    return "%.1f MB" % (kb / 1024.0)


details = [
    ("Photo size", "%d \u00d7 %d px" % (orig_w, orig_h)),
    ("File size", human(file_bytes)),
    ("Lighting", brightness),
    ("Type", "JPEG photo"),
]
pct = max(1, round(top_score * 100))
rank_note = "High confidence" if pct >= 70 else ("Fairly confident" if pct >= 40 else "Best guess \u2014 try a closer photo")


# the app now sizes the frame to the photo's aspect ratio (clamped 180..470dp)
_ratio = photo.size[1] / float(photo.size[0])
FRAME_H_DP = max(180.0, min(470.0, round(294.0 * _ratio)))
FRAME_W, FRAME_H = 294.0, FRAME_H_DP - 2.0   # inner content box of the preview frame (dp)


def boxes_html():
    if not detections:
        return ""
    w, h = photo.size
    scale = min(FRAME_W / w, FRAME_H / h)
    dw, dh = w * scale, h * scale
    ox, oy = (FRAME_W - dw) / 2.0, (FRAME_H - dh) / 2.0
    out = []
    for name, score, box in detections:
        top, left, bottom, right = box
        x = ox + left * dw
        y = oy + top * dh
        bw = (right - left) * dw
        bh = (bottom - top) * dh
        out.append(
            '<div style="position:absolute;left:%.1fpx;top:%.1fpx;width:%.1fpx;height:%.1fpx;'
            'border:2.2px solid #A78BFA;border-radius:10px;background:rgba(124,92,255,.16)"></div>'
            '<div style="position:absolute;left:%.1fpx;top:%.1fpx;background:rgba(18,26,44,.9);'
            'border-radius:7px;padding:3px 6px;font:500 10.5px Roboto;color:#fff;white-space:nowrap">%s &nbsp;%d%%</div>'
            % (x, y, bw, bh, max(2.0, x), (y - 20 if y >= 22 else y + 2), cap(name), round(score * 100)))
    return "".join(out)


def objects_card():
    if not detections:
        return ""
    rows = []
    seen_rows = set()
    shown = []
    for name, score, box in detections:
        if name.lower() in seen_rows:
            continue
        seen_rows.add(name.lower())
        shown.append((name, score))
    for i, (name, score) in enumerate(shown):
        rows.append(
            '<div style="margin-top:%dpx">'
            '<div style="display:flex;align-items:center">'
            '<div style="flex:1;font:500 14px Roboto;color:#F2F5FF">%s</div>'
            '<div style="font:400 12.5px Roboto;color:#96A2BE">%d%%</div></div>'
            '<div style="margin-top:6px;height:7px;border-radius:100px;background:#1D2740;display:flex;overflow:hidden">'
            '<div style="width:%.1f%%;background:linear-gradient(90deg,#8B5CFF,#4F8CFF)"></div></div></div>'
            % (0 if i == 0 else 12, cap(name), max(1, round(score * 100)), max(1.2, score * 100)))
    return ('<div class="card" style="padding:16px">'
            '<div class="label">OBJECTS FOUND</div>'
            '<div style="margin-top:4px;font:400 11.5px Roboto;color:#6B7793">'
            'Tap any object to look closer at it</div>'
            '<div style="margin-top:10px">' + "".join(rows) + '</div></div>')


def cap(text):
    return text[:1].upper() + text[1:] if text else text


def article(text):
    return ("an " if text[0].lower() in "aeiou" else "a ") + text


# ------------------------------------------------------------------- icons
ICONS = {
    "sparkle": '<path d="M11,2.2 l1.85,5.35 L18.2,9.4 l-5.35,1.85 L11,16.6 L9.15,11.25 L3.8,9.4 l5.35,-1.85 z" fill="#fff"/><path d="M18.2,14.6 l0.95,2.45 L21.6,18 l-2.45,0.95 L18.2,21.4 l-0.95,-2.45 L14.8,18 l2.45,-0.95 z" fill="#fff" opacity=".75"/>',
    "gallery": '<path d="M5,4 h14 a2,2 0 0 1 2,2 v12 a2,2 0 0 1 -2,2 h-14 a2,2 0 0 1 -2,-2 v-12 a2,2 0 0 1 2,-2 z" stroke="#fff" stroke-width="1.7" fill="none"/><path d="M3.4,16.6 l4.3,-4.3 a1.1,1.1 0 0 1 1.5,0 l2.3,2.3 l2.1,-2.1 a1.1,1.1 0 0 1 1.5,0 l3.4,3.4" stroke="#fff" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" fill="none"/><circle cx="8.4" cy="9.3" r="1.6" fill="#fff"/>',
    "camera": '<path d="M3.8,9.6 a1.8,1.8 0 0 1 1.8,-1.8 h1.9 l1.5,-2.2 h6 l1.5,2.2 h1.9 a1.8,1.8 0 0 1 1.8,1.8 v8 a1.8,1.8 0 0 1 -1.8,1.8 h-12.8 a1.8,1.8 0 0 1 -1.8,-1.8 z" stroke="#fff" stroke-width="1.7" stroke-linejoin="round" fill="none"/><circle cx="12" cy="13.5" r="3.3" stroke="#fff" stroke-width="1.7" fill="none"/>',
    "image": '<path d="M4.5,4.5 h15 a1.5,1.5 0 0 1 1.5,1.5 v12 a1.5,1.5 0 0 1 -1.5,1.5 h-15 a1.5,1.5 0 0 1 -1.5,-1.5 v-12 a1.5,1.5 0 0 1 1.5,-1.5 z" stroke="#fff" stroke-width="1.5" fill="none"/><path d="M3.2,16.8 l4.6,-4.6 a1.1,1.1 0 0 1 1.5,0 l2.2,2.2 l2,-2 a1.1,1.1 0 0 1 1.5,0 l3.6,3.6" stroke="#fff" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/><circle cx="8.2" cy="9.2" r="1.4" fill="#fff"/>',
    "search": '<circle cx="11" cy="11" r="6.6" stroke="#A78BFA" stroke-width="1.8" fill="none"/><path d="M15.9,15.9 L20.4,20.4" stroke="#A78BFA" stroke-width="1.8" stroke-linecap="round"/>',
    "book": '<path d="M12,6.6 C10.2,5.2 7.4,4.7 4.6,5.1 v12.2 c2.8,-0.4 5.6,0.1 7.4,1.5 c1.8,-1.4 4.6,-1.9 7.4,-1.5 V5.1 C16.6,4.7 13.8,5.2 12,6.6 z" stroke="#96A2BE" stroke-width="1.6" stroke-linejoin="round" fill="none"/><path d="M12,6.6 L12,18.8" stroke="#96A2BE" stroke-width="1.6" stroke-linecap="round"/>',
    "share": '<circle cx="17.4" cy="5.6" r="2.3" stroke="#96A2BE" stroke-width="1.6" fill="none"/><circle cx="6.6" cy="12" r="2.3" stroke="#96A2BE" stroke-width="1.6" fill="none"/><circle cx="17.4" cy="18.4" r="2.3" stroke="#96A2BE" stroke-width="1.6" fill="none"/><path d="M8.7,10.9 L15.3,6.9 M8.7,13.1 L15.3,17.1" stroke="#96A2BE" stroke-width="1.6" stroke-linecap="round"/>',
}


def icon(name, size, color_filter=None):
    style = "filter:%s;" % color_filter if color_filter else ""
    return ('<svg viewBox="0 0 24 24" width="%s" height="%s" style="flex:0 0 auto;%s">%s</svg>'
            % (size, size, style, ICONS[name]))


# ------------------------------------------------------------------- markup
def bars():
    rows = []
    for i, (label, score) in enumerate(others):
        alpha = max(0.45, 1.0 - i * 0.18)
        rows.append("""
      <div style="margin-top:%dpx">
        <div style="display:flex;align-items:center">
          <div style="flex:1;font:500 14px Roboto;color:#F2F5FF;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">%s</div>
          <div style="font:400 12.5px Roboto;color:#96A2BE">%d%%</div>
        </div>
        <div style="margin-top:6px;height:7px;border-radius:100px;background:#1D2740;display:flex;overflow:hidden">
          <div style="width:%.1f%%;background:linear-gradient(90deg,#8B5CFF,#4F8CFF);opacity:%.2f"></div>
        </div>
      </div>""" % (0 if i == 0 else 12, cap(label), max(1, round(score * 100)),
                   max(1.2, score * 100), alpha))
    return "".join(rows)


def detail_rows():
    return "".join(
        '<div style="margin-top:7px;display:flex;align-items:center">'
        '<div style="flex:1;font:400 13px Roboto;color:#96A2BE">%s</div>'
        '<div style="font:500 13px Roboto;color:#F2F5FF">%s</div></div>' % (k, v)
        for k, v in details)


def swatches():
    out = []
    for i, c in enumerate(colors):
        hexv = "#%02X%02X%02X" % c
        margin = "" if i == len(colors) - 1 else "margin-right:8px;"
        out.append('<div style="flex:1;%s">'
                   '<div style="height:34px;border-radius:9px;background:%s;border:1px solid rgba(255,255,255,.2)"></div>'
                   '<div style="margin-top:5px;text-align:center;font:400 9.5px Roboto;color:#6B7793">%s</div></div>'
                   % (margin, hexv, hexv))
    return "".join(out)


import base64
img_b64 = base64.b64encode(open(IMG, "rb").read()).decode()
font_b64 = base64.b64encode(open(os.path.join(WORK, "fonts/roboto-400.woff2"), "rb").read()).decode()

HTML = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>
@font-face { font-family:'Roboto'; src:url(data:font/woff2;base64,%(font)s) format('woff2'); font-weight:100 900; font-style:normal; }
* { margin:0; padding:0; box-sizing:border-box; -webkit-font-smoothing:antialiased; }
html,body { width:360px; height:780px; overflow:hidden; }
body { font-family:Roboto,sans-serif; background:#080B14; color:#F2F5FF; }
.screen { position:relative; width:360px; height:780px; overflow:hidden;
  background:radial-gradient(420px 420px at 90%% -2%%, rgba(108,92,231,.30), rgba(108,92,231,0) 70%%),
             linear-gradient(180deg,#0A0F1C 0%%,#080B14 55%%,#05070E 100%%); }
.statusbar { height:26px; background:#080B14; display:flex; align-items:center; justify-content:space-between;
  padding:0 16px; font:500 11.5px Roboto; color:#F2F5FF; letter-spacing:.2px; }
.statusbar .right { display:flex; align-items:center; gap:5px; }
.scroll { height:754px; overflow:hidden; }
.content { padding:14px 18px 30px 18px; }
.card { background:#141B2E; border:1px solid #26314A; border-radius:20px; }
.label { font:500 10.5px Roboto; letter-spacing:.16em; color:#6B7793; }
.btnrow { display:flex; gap:10px; }
.btn-soft { height:50px; border-radius:16px; background:#1B2338; border:1px solid #2B3552;
  display:flex; align-items:center; justify-content:center; gap:8px; font:500 14px Roboto; color:#F2F5FF; }
.btn-identify { height:56px; border-radius:18px; background:linear-gradient(90deg,#8B5CFF,#4F8CFF);
  display:flex; align-items:center; justify-content:center; gap:9px; font:500 16px Roboto; color:#fff; }
.btn-ghost { height:52px; border-radius:16px; background:#121A2C; border:1px solid #33406A;
  display:flex; align-items:center; justify-content:center; gap:9px; font:500 14px Roboto; color:#F2F5FF; }
.btn-ghost.sm { height:50px; font-size:13.5px; }
.navpill { position:absolute; bottom:7px; left:50%%; transform:translateX(-50%%);
  width:108px; height:4px; border-radius:100px; background:rgba(255,255,255,.55); }
</style></head>
<body>
<div class="screen">
  <div class="statusbar">
    <div>9:41</div>
    <div class="right">
      <svg width="15" height="11" viewBox="0 0 15 11"><rect x="0" y="7" width="3" height="4" rx="1" fill="#fff"/><rect x="4" y="5" width="3" height="6" rx="1" fill="#fff"/><rect x="8" y="2.5" width="3" height="8.5" rx="1" fill="#fff"/><rect x="12" y="0" width="3" height="11" rx="1" fill="#fff" opacity=".4"/></svg>
      <svg width="15" height="11" viewBox="0 0 15 11"><path d="M7.5,1.2 C4.6,1.2 2,2.4 0.3,4.3 L1.6,5.7 C3,4.1 5.1,3.1 7.5,3.1 C9.9,3.1 12,4.1 13.4,5.7 L14.7,4.3 C13,2.4 10.4,1.2 7.5,1.2 z" fill="#fff"/><path d="M7.5,5.2 C5.6,5.2 3.9,6 2.6,7.2 L3.9,8.6 C4.8,7.7 6.1,7.1 7.5,7.1 C8.9,7.1 10.2,7.7 11.1,8.6 L12.4,7.2 C11.1,6 9.4,5.2 7.5,5.2 z" fill="#fff"/><circle cx="7.5" cy="10" r="1.3" fill="#fff"/></svg>
      <svg width="22" height="11" viewBox="0 0 22 11"><rect x="0.5" y="0.5" width="18" height="10" rx="2.6" stroke="#fff" stroke-opacity=".45" fill="none"/><rect x="2" y="2" width="13" height="7" rx="1.5" fill="#fff"/><path d="M20,3.6 v3.8 c1,-0.3 1.4,-1 1.4,-1.9 C21.4,4.6 21,3.9 20,3.6 z" fill="#fff" opacity=".5"/></svg>
    </div>
  </div>
  <div class="scroll">
   <div class="content" id="content">
    <div style="display:flex;align-items:center">
      <div style="width:40px;height:40px;border-radius:12px;background:linear-gradient(315deg,#8B5CFF,#4F8CFF);display:flex;align-items:center;justify-content:center">%(sparkle40)s</div>
      <div style="flex:1;margin-left:11px">
        <div style="font:900 19px Roboto;letter-spacing:-.2px">Pehchan AI</div>
        <div style="font:400 11px Roboto;color:#96A2BE;margin-top:1px">Image identifier &middot; on-device AI</div>
      </div>
      <div style="background:#1F1A3D;border:1px solid #3E3670;border-radius:100px;padding:5px 10px;font:500 9.5px Roboto;letter-spacing:.1em;color:#A78BFA">OFFLINE</div>
    </div>

    <div style="margin-top:22px;font:900 27px Roboto;line-height:1.16;letter-spacing:-.3px">What&rsquo;s in your photo?</div>
    <div style="margin-top:7px;font:400 13.5px Roboto;line-height:1.4;color:#96A2BE">Animals, food, phones, gadgets, furniture &mdash; pick any photo and Pehchan AI finds every object in it.</div>

    <div class="card" style="margin-top:18px;padding:14px">
      <div id="imageframe" style="height:%(frame_h)spx;border-radius:18px;background:#0D1424;border:1px solid #232E46;overflow:hidden;position:relative">
        %(photo)s
        %(boxes)s
        %(placeholder)s
      </div>
      <div class="btnrow" style="margin-top:12px">
        <div class="btn-soft" style="flex:1">%(gal18)sGallery</div>
        <div class="btn-soft" style="flex:1">%(cam18)sCamera</div>
      </div>
      <div class="btn-identify" style="margin-top:10px;%(identify_style)s">%(spark18)sIdentify Image</div>
    </div>

    <div id="results" style="margin-top:14px;%(results_style)s">
      %(objects_card)s
      <div style="border-radius:22px;border:1px solid #3A3568;padding:18px;margin-top:%(hero_margin)s;background:linear-gradient(315deg,#2A2352,#1A2140 55%%,#151C31)">
        <div class="label" style="color:#A78BFA">TOP MATCH</div>
        <div style="margin-top:6px;font:900 25px Roboto;letter-spacing:-.2px">%(top_label)s</div>
        <div style="margin-top:5px;font:400 13px Roboto;color:#96A2BE;line-height:1.35">This looks like %(top_article)s.</div>
        <div style="margin-top:13px;display:flex;align-items:center">
          <div style="background:#12291F;border:1px solid #2A6B4A;border-radius:100px;padding:5px 11px;font:500 12px Roboto;color:#4ADE80">%(pct)d%% match</div>
          <div style="margin-left:9px;font:400 11.5px Roboto;color:#6B7793">%(rank_note)s</div>
        </div>
      </div>

      <div class="card" style="margin-top:12px;padding:16px">
        <div class="label">OTHER POSSIBILITIES</div>
        <div style="margin-top:12px">%(bars)s</div>
      </div>

      <div class="card" id="detailcard" style="margin-top:12px;padding:16px">
        <div class="label">IMAGE DETAILS</div>
        <div style="margin-top:10px">%(details)s</div>
        <div class="label" style="margin-top:16px">DOMINANT COLOURS</div>
        <div style="margin-top:9px;display:flex">%(swatches)s</div>
      </div>

      <div class="btn-ghost" style="margin-top:12px">%(search)sSearch on Google Lens</div>
      <div class="btnrow" style="margin-top:10px">
        <div class="btn-ghost sm" style="flex:1">%(book)sWikipedia</div>
        <div class="btn-ghost sm" style="flex:1">%(share)sShare</div>
      </div>
    </div>

    <div style="margin-top:18px;text-align:center;font:400 11px Roboto;line-height:1.35;color:#6B7793">
      Two AI models run 100%% on your phone &mdash; an object detector and a fine-detail classifier.
    </div>
   </div>
  </div>
  <div class="navpill"></div>
</div>
<script>
  var q = new URLSearchParams(location.search).get('s') || 'home';
  var scroll = document.querySelector('.scroll');
  var content = document.getElementById('content');
  if (q === 'result') {
    var r = document.getElementById('results');
    content.style.marginTop = -(r.offsetTop - 30) + 'px';
  } else if (q === 'detected') {
    var f = document.getElementById('imageframe');
    content.style.marginTop = -(f.offsetTop - 34) + 'px';
  } else if (q === 'details') {
    var max = content.offsetHeight - 754;
    var d = document.getElementById('detailcard');
    content.style.marginTop = -Math.min(Math.max(0, d.offsetTop - 120), Math.max(0, max)) + 'px';
  }
</script>
</body></html>"""


def build(screen):
    photo_html = ""
    placeholder = ('<div style="position:absolute;inset:0;display:flex;flex-direction:column;'
                   'align-items:center;justify-content:center;gap:12px">'
                   '<div style="opacity:.55">' + icon("image", "46px") + '</div>'
                   '<div style="font:500 14.5px Roboto">No photo selected</div>'
                   '<div style="font:400 12px Roboto;color:#6B7793">Choose from gallery or take a new photo</div></div>')
    identify_style = "opacity:.45;"
    results_style = "display:none;"
    if screen in ("picked", "result", "details", "detected"):
        photo_html = ('<img src="data:image/jpeg;base64,%s" style="width:100%%;height:100%%;object-fit:contain">' % img_b64)
        placeholder = ""
        identify_style = ""
    if screen in ("result", "details", "detected"):
        results_style = ""
    return HTML % {
        "font": font_b64,
        "sparkle40": icon("sparkle", "22px"),
        "spark18": icon("sparkle", "19px"),
        "gal18": icon("gallery", "18px"),
        "cam18": icon("camera", "18px"),
        "photo": photo_html,
        "frame_h": int(FRAME_H_DP),
        "boxes": boxes_html() if screen in ("result", "details", "detected") else "",
        "objects_card": objects_card() if screen in ("result", "details", "detected") else "",
        "hero_margin": ("12px" if detections else "0") if screen in ("result", "details", "detected") else "0",
        "placeholder": placeholder,
        "identify_style": identify_style,
        "results_style": results_style,
        "top_label": cap(top_label),
        "top_article": article(top_label),
        "pct": pct,
        "rank_note": rank_note,
        "bars": bars(),
        "details": detail_rows(),
        "swatches": swatches(),
        "search": icon("search", "19px"),
        "book": icon("book", "17px"),
        "share": icon("share", "17px"),
    }


CHROME = "chromium"
_default = [("home", "01-home.png"), ("picked", "02-photo-picked.png"),
            ("detected", "03-objects.png"), ("result", "04-result.png"),
            ("details", "05-details.png")]
_env = os.environ.get("SHOTS")
shots = _default if not _env else [(a, b) for a, b in (x.split(":") for x in _env.split(","))]

for screen, name in shots:
    html_path = os.path.join(OUT, screen + ".html")
    open(html_path, "w", encoding="utf-8").write(build(screen))
    png = os.path.join(OUT, name)
    cmd = [CHROME, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
           "--force-device-scale-factor=3", "--window-size=360,780",
           "--virtual-time-budget=3000", "--default-background-color=00000000",
           "--screenshot=" + png, "file://" + html_path + "?s=" + screen]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
    print(name, "->", "ok" if os.path.exists(png) else "FAILED", r.stderr.strip()[-200:] if r.returncode else "")

for _, name in shots:
    p = os.path.join(OUT, name)
    if os.path.exists(p):
        print(name, Image.open(p).size)

print("top:", top_label, pct, "%", "| others:", [(l, round(s * 100, 1)) for l, s in others])
print("details:", details)
print("colors:", ["#%02X%02X%02X" % c for c in colors])
