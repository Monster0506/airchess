"""Procedurally generates the AirChess chess set as binary glTF (GLB) and USDZ files.

Output (relative to the repo root):
  app/src/main/assets/models/  GLB for Android
  iosApp/Models/               USDZ for iOS (RealityKit)
  {white,black}_{pawn,rook,knight,bishop,queen,king} and board, in each format

Coordinate spec (meters, +Y up, right-handed, same in both formats):
  * Piece origin is the center of its base, the base sits on y = 0.
  * White knights face -Z, black knights face +Z (baked in).
  * Board: top face at y = 0, a1 at x = -0.175, z = +0.175; files grow +X,
    ranks grow -Z.

Both formats are written from the same in-memory vertex/index arrays. The
output is deterministic: no randomness, no timestamps.
"""

import argparse
import json
import math
import struct
import sys
import tempfile
import warnings
import zipfile
from collections import Counter
from pathlib import Path

import numpy as np

MM = 0.001
SIDES = 24
SPHERE_SIDES = 12
SPHERE_RINGS = 6
CREASE_DEG = 50.0
KEY_SCALE = 1e7
EPS_AREA = 1e-18
HEIGHT_TOLERANCE = 0.05

SQUARE = 0.05
BOARD_SIZE = 8 * SQUARE
FRAME_WIDTH = 0.02
FRAME_DEPTH = 0.012

REPO_ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "models"
USDZ_DIR = REPO_ROOT / "iosApp" / "Models"
FORMATS = ("glb", "usdz")

# Zip header layout used to pin entry timestamps (1980-01-01 00:00).
DOS_DATE = (0 << 9) | (1 << 5) | 1
DOS_TIME = 0
ZIP_LOCAL_TIME = 10
ZIP_CENTRAL_TIME = 12
ZIP_CENTRAL_LENGTHS = 28
ZIP_CENTRAL_FIXED = 46

PIECE_NAMES = ("pawn", "rook", "knight", "bishop", "queen", "king")
PIECE_HEIGHTS = {
    "pawn": 0.022,
    "rook": 0.028,
    "knight": 0.032,
    "bishop": 0.036,
    "queen": 0.042,
    "king": 0.046,
}
PIECE_DIAMETERS = {
    "pawn": 0.024,
    "rook": 0.026,
    "knight": 0.026,
    "bishop": 0.026,
    "queen": 0.030,
    "king": 0.030,
}

# name -> (hex color, roughness)
PIECE_STYLES = {
    "white": ("#EDE3CF", 0.45),
    "black": ("#2B211C", 0.35),
}
BOARD_STYLES = {
    "light": ("#E8D5B0", 0.6),
    "dark": ("#8B5A3C", 0.6),
    "frame": ("#4A2E1F", 0.55),
}


# ---------------------------------------------------------------- geometry

def arc(cx, cy, rad, a0, a1, n):
    """Profile points (r, y) along a circle arc; angles in degrees."""
    pts = []
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        pts.append((round(cx + rad * math.cos(a), 9), round(cy + rad * math.sin(a), 9)))
    return pts


def tri_normals(tris):
    cross = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0])
    return cross


def solid(tris):
    """Drops degenerate triangles, checks closure and makes winding outward."""
    tris = np.asarray(tris, dtype=np.float64)
    area2 = np.linalg.norm(tri_normals(tris), axis=1)
    tris = tris[area2 > EPS_AREA]

    keys = np.round(tris * KEY_SCALE).astype(np.int64)
    edges = Counter()
    for t in keys:
        for i in range(3):
            edges[(tuple(t[i]), tuple(t[(i + 1) % 3]))] += 1
    for (a, b), count in edges.items():
        if count != 1 or edges.get((b, a), 0) != 1:
            raise ValueError("mesh is not a consistently oriented closed surface")

    volume = np.einsum("ij,ij->i", tris[:, 0], np.cross(tris[:, 1], tris[:, 2])).sum()
    if volume < 0:
        tris = tris[:, ::-1]
    return np.ascontiguousarray(tris)


def lathe(profile, sides=SIDES):
    """Revolves a (r, y) profile around the Y axis."""
    tris = []
    ang = [2 * math.pi * j / sides for j in range(sides + 1)]

    def ring(r, y, j):
        return (r * math.cos(ang[j]), y, r * math.sin(ang[j]))

    for (r0, y0), (r1, y1) in zip(profile, profile[1:]):
        if (r0, y0) == (r1, y1):
            continue
        for j in range(sides):
            a0, a1 = ring(r0, y0, j), ring(r0, y0, j + 1)
            b0, b1 = ring(r1, y1, j), ring(r1, y1, j + 1)
            tris.append((a0, b0, b1))
            tris.append((a0, b1, a1))
    return solid(tris)


def sphere(cx, cy, cz, rad):
    prof = arc(0.0, 0.0, rad, -90, 90, SPHERE_RINGS)
    tris = lathe(prof, SPHERE_SIDES)
    return tris + np.array([cx, cy, cz])


def box(lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    c = {
        (i, j, k): (x1 if i else x0, y1 if j else y0, z1 if k else z0)
        for i in (0, 1)
        for j in (0, 1)
        for k in (0, 1)
    }
    quads = [
        ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)),
        ((0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)),
        ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
        ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
        ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
        ((0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)),
    ]
    tris = []
    for a, b, d, e in quads:
        tris.append((c[a], c[b], c[d]))
        tris.append((c[a], c[d], c[e]))
    return solid(tris)


def ear_clip(poly):
    """Triangulates a simple CCW polygon; returns index triples (CCW)."""
    idx = list(range(len(poly)))
    out = []

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    def inside(p, a, b, c):
        return cross(a, b, p) >= 0 and cross(b, c, p) >= 0 and cross(c, a, p) >= 0

    while len(idx) > 3:
        for k in range(len(idx)):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
            a, b, c = poly[i0], poly[i1], poly[i2]
            if cross(a, b, c) <= 1e-12:
                continue
            if any(inside(poly[m], a, b, c) for m in idx if m not in (i0, i1, i2)):
                continue
            out.append((i0, i1, i2))
            del idx[k]
            break
        else:
            raise ValueError("ear clipping failed; polygon is not simple CCW")
    out.append(tuple(idx))
    return out


def extrude(poly, place, loops):
    """Extrudes a CCW (u, v) polygon through `loops` = [(w, scale), ...].

    `place(u, v, w)` maps to world xyz. Scale shrinks a loop toward the
    polygon centroid, which gives the end caps a pillow-like bevel.
    """
    poly = [tuple(p) for p in poly]
    cu = sum(p[0] for p in poly) / len(poly)
    cv = sum(p[1] for p in poly) / len(poly)
    n = len(poly)
    rings = []
    for w, s in loops:
        rings.append([place(cu + (u - cu) * s, cv + (v - cv) * s, w) for u, v in poly])

    tris = []
    caps = ear_clip(poly)
    for a, b, c in caps:
        tris.append((rings[0][a], rings[0][b], rings[0][c]))
        tris.append((rings[-1][c], rings[-1][b], rings[-1][a]))
    for lo, hi in zip(rings, rings[1:]):
        for i in range(n):
            j = (i + 1) % n
            tris.append((lo[j], lo[i], hi[i]))
            tris.append((lo[j], hi[i], hi[j]))
    return solid(tris)


def sector(r0, r1, a0, a1, y0, y1, n=4):
    """Annular sector prism (rook merlon); angles in degrees."""
    poly = arc(0, 0, r1, a0, a1, n) + arc(0, 0, r0, a1, a0, n)
    return extrude(poly, lambda u, v, w: (u, w, v), [(y0, 1.0), (y1, 1.0)])


def rot_y_180(tris):
    return tris * np.array([-1.0, 1.0, -1.0])


def smooth(tris):
    """Per-corner normals: area-weighted average over faces within the crease angle."""
    fn = tri_normals(tris)
    area = np.linalg.norm(fn, axis=1)
    unit = fn / area[:, None]
    keys = np.round(tris * KEY_SCALE).astype(np.int64)

    groups = {}
    for f in range(len(tris)):
        for c in range(3):
            groups.setdefault(tuple(keys[f, c]), []).append(f)

    cos_crease = math.cos(math.radians(CREASE_DEG))
    normals = np.zeros_like(tris)
    for f in range(len(tris)):
        for c in range(3):
            faces = np.array(groups[tuple(keys[f, c])])
            near = faces[unit[faces] @ unit[f] > cos_crease]
            nrm = (unit[near] * area[near][:, None]).sum(axis=0)
            normals[f, c] = nrm / np.linalg.norm(nrm)
    return normals


# ----------------------------------------------------------------- pieces

def base_profile(radius):
    r = radius
    return [
        (0.0, 0.0),
        (r, 0.0),
        (r, 1.6),
        (r - 1.0, 2.6),
        (r - 4.0, 3.4),
    ]


def pawn():
    prof = base_profile(12.0) + [
        (4.4, 6.0),
        (3.2, 10.0),
        (3.0, 12.0),
        (5.4, 12.4),
        (5.4, 13.4),
        (2.8, 13.8),
    ]
    prof += arc(0.0, 17.5, 4.5, -57, 90, 8)
    return lathe(prof)


def rook():
    prof = base_profile(13.0) + [
        (6.6, 8.0),
        (6.2, 19.0),
        (7.6, 20.0),
        (9.5, 21.5),
        (9.5, 24.0),
        (0.0, 24.0),
    ]
    parts = [lathe(prof)]
    for k in range(6):
        a0 = 60.0 * k + 8.0
        parts.append(sector(6.4, 9.5, a0, a0 + 44.0, 24.0, 28.0))
    return np.concatenate(parts)


def bishop():
    prof = base_profile(13.0) + [
        (3.4, 13.0),
        (3.6, 14.0),
        (6.2, 14.6),
        (6.2, 15.6),
        (3.8, 16.3),
    ]
    egg = [(5.6 * math.cos(math.radians(t)), 24.5 + 9.0 * math.sin(math.radians(t)))
           for t in range(-68, 63, 10)]
    prof += [(round(r, 9), round(y, 9)) for r, y in egg]
    prof += arc(0.0, 33.8, 2.2, -30, 90, 6)
    return lathe(prof)


def queen_body():
    return base_profile(15.0) + [
        (5.0, 9.0),
        (3.8, 20.0),
        (4.0, 24.0),
        (7.0, 25.0),
        (7.0, 26.5),
        (5.0, 27.5),
        (6.2, 29.5),
        (9.2, 36.0),
        (9.2, 37.5),
    ]


def queen():
    prof = queen_body() + [(0.0, 37.5)]
    parts = [lathe(prof)]
    ring_r = 7.8
    for k in range(8):
        a = 2 * math.pi * k / 8
        parts.append(sphere(ring_r * math.cos(a), 38.3, ring_r * math.sin(a), 1.5))
    parts.append(sphere(0.0, 39.6, 0.0, 2.4))
    return np.concatenate(parts)


def king():
    prof = base_profile(15.0) + [
        (5.0, 9.0),
        (3.9, 22.0),
        (4.1, 26.0),
        (7.2, 27.0),
        (7.2, 28.5),
        (5.4, 29.5),
        (8.8, 38.5),
        (8.0, 39.6),
        (5.0, 40.4),
        (0.0, 40.7),
    ]
    a, big, yb, y0, y1, yt = 1.6, 5.0, 38.5, 42.2, 45.4, 46.0
    cross = [
        (-a, yb), (a, yb), (a, y0), (big, y0), (big, y1), (a, y1),
        (a, yt), (-a, yt), (-a, y1), (-big, y1), (-big, y0), (-a, y0),
    ]
    cross = extrude(cross, lambda u, v, w: (w, v, u), [(-a, 0.8), (-a + 0.5, 1.0), (a - 0.5, 1.0), (a, 0.8)])
    return np.concatenate([lathe(prof), cross])


# Spine of the horse head in the Y-Z plane, neck bottom to muzzle (mm). Each
# station is (z, y, half depth in the plane, half width along X); sections are
# ellipses perpendicular to the spine, so the head is solid from every side.
KNIGHT_SPINE = [
    (0.0, 3.5, 8.0, 5.6),
    (-0.5, 9.0, 7.0, 5.2),
    (-0.5, 15.0, 5.8, 4.8),
    (0.0, 19.5, 5.0, 4.5),
    (1.5, 23.0, 4.4, 4.5),
    (4.2, 24.5, 4.2, 4.5),
    (7.6, 22.8, 3.8, 4.0),
    (9.6, 19.8, 3.2, 3.4),
    (10.6, 16.8, 2.4, 2.8),
    (11.0, 14.8, 1.2, 1.6),
]
KNIGHT_RING = 14
KNIGHT_EAR_Y = 32.0
KNIGHT_EARS = [(3.0, 2.4), (3.0, -2.4)]  # (z, x) of the ear bases
KNIGHT_MANE_SPAN = range(1, 6)
KNIGHT_MANE_OUT = 1.8
KNIGHT_MANE_IN = 1.5
KNIGHT_MANE_HALF = 0.8


def spine_frames(spine):
    """Unit normals (pointing forward at the base) along the spine."""
    frames = []
    for i, (z, y, _, _) in enumerate(spine):
        z0, y0 = spine[max(i - 1, 0)][:2]
        z1, y1 = spine[min(i + 1, len(spine) - 1)][:2]
        tz, ty = z1 - z0, y1 - y0
        norm = math.hypot(tz, ty)
        frames.append((ty / norm, -tz / norm))
    return frames


def loft(spine, ring=KNIGHT_RING):
    rings = []
    for (z, y, a, w), (nz, ny) in zip(spine, spine_frames(spine)):
        rings.append([
            (w * math.sin(t), y + a * math.cos(t) * ny, z + a * math.cos(t) * nz)
            for t in (2 * math.pi * j / ring for j in range(ring))
        ])

    def center(r):
        return tuple(sum(p[i] for p in r) / len(r) for i in range(3))

    tris = []
    for lo, hi in zip(rings, rings[1:]):
        for j in range(ring):
            k = (j + 1) % ring
            tris.append((lo[j], lo[k], hi[k]))
            tris.append((lo[j], hi[k], hi[j]))
    for r, flip in ((rings[0], True), (rings[-1], False)):
        c = center(r)
        for j in range(ring):
            a, b = r[j], r[(j + 1) % ring]
            tris.append((c, b, a) if flip else (c, a, b))
    return solid(tris)


def mane(spine):
    """Thin fin along the back of the neck, rooted inside the body."""
    frames = spine_frames(spine)
    out, inn = [], []
    for i in KNIGHT_MANE_SPAN:
        z, y, a, _ = spine[i]
        nz, ny = frames[i]
        out.append((z - (a + KNIGHT_MANE_OUT) * nz, y - (a + KNIGHT_MANE_OUT) * ny))
        inn.append((z - (a - KNIGHT_MANE_IN) * nz, y - (a - KNIGHT_MANE_IN) * ny))
    poly = out + inn[::-1]
    area = sum(p[0] * q[1] - q[0] * p[1] for p, q in zip(poly, poly[1:] + poly[:1]))
    if area < 0:
        poly.reverse()
    h = KNIGHT_MANE_HALF
    loops = [(-h, 0.7), (-h + 0.4, 1.0), (h - 0.4, 1.0), (h, 0.7)]
    return extrude(poly, lambda u, v, w: (w, v, u), loops)


def knight(white):
    pedestal = lathe([
        (0.0, 0.0), (13.0, 0.0), (13.0, 2.0), (12.0, 3.5), (10.5, 4.5), (0.0, 4.5),
    ])
    ear = lathe([(0.0, 0.0), (1.6, 0.0), (1.1, 2.4), (0.0, 5.5)], 8)
    ears = [ear + np.array([x, KNIGHT_EAR_Y - 5.5, z]) for z, x in KNIGHT_EARS]
    tris = np.concatenate([pedestal, loft(KNIGHT_SPINE), mane(KNIGHT_SPINE), *ears])
    return rot_y_180(tris) if white else tris


def build_piece(name, white):
    makers = {
        "pawn": pawn,
        "rook": rook,
        "bishop": bishop,
        "queen": queen,
        "king": king,
    }
    if name == "knight":
        return knight(white) * MM
    return makers[name]() * MM


# ------------------------------------------------------------------ board

def board_prims():
    tiles = {"light": [], "dark": []}
    half = BOARD_SIZE / 2
    for rank in range(8):
        for file in range(8):
            cx = -half + SQUARE * (file + 0.5)
            cz = half - SQUARE * (rank + 0.5)
            x0, x1 = cx - SQUARE / 2, cx + SQUARE / 2
            z0, z1 = cz - SQUARE / 2, cz + SQUARE / 2
            kind = "dark" if (file + rank) % 2 == 0 else "light"
            tiles[kind].append(((x0, 0, z1), (x1, 0, z1), (x1, 0, z0)))
            tiles[kind].append(((x0, 0, z1), (x1, 0, z0), (x0, 0, z0)))

    outer = half + FRAME_WIDTH
    lo, hi = -FRAME_DEPTH, 0.0
    frame = [
        box((-outer, lo, -outer), (outer, hi, -half)),
        box((-outer, lo, half), (outer, hi, outer)),
        box((-outer, lo, -half), (-half, hi, half)),
        box((half, lo, -half), (outer, hi, half)),
    ]
    underside = [
        ((-half, lo, -half), (half, lo, -half), (half, lo, half)),
        ((-half, lo, -half), (half, lo, half), (-half, lo, half)),
    ]
    frame.append(np.array(underside, dtype=np.float64))
    return [
        ("light", np.array(tiles["light"], dtype=np.float64)),
        ("dark", np.array(tiles["dark"], dtype=np.float64)),
        ("frame", np.concatenate(frame)),
    ]


# -------------------------------------------------------------------- glb

def linear(hex_color):
    def lin(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    raw = hex_color.lstrip("#")
    return [round(lin(int(raw[i:i + 2], 16) / 255.0), 6) for i in (0, 2, 4)] + [1.0]


def mesh_arrays(tris):
    tris = tris.astype(np.float32).astype(np.float64)
    normals = smooth(tris)
    rows = np.concatenate([tris.reshape(-1, 3), np.round(normals.reshape(-1, 3), 5)], axis=1)
    rows = rows.astype(np.float32)
    uniq, inverse = np.unique(rows, axis=0, return_inverse=True)
    return uniq[:, :3].copy(), uniq[:, 3:].copy(), inverse.reshape(-1).astype(np.uint32)


def build_meshes(prims):
    """prims: [(material name, triangles)] -> [(material name, positions, normals, indices)]."""
    return [(mat, *mesh_arrays(tris)) for mat, tris in prims]


def write_glb(path, name, meshes, styles):
    """Writes one glTF mesh with one primitive per entry of `meshes`."""
    blob = bytearray()
    views, accessors, primitives, materials = [], [], [], []

    def add_view(data, target):
        while len(blob) % 4:
            blob.append(0)
        views.append({"buffer": 0, "byteOffset": len(blob), "byteLength": len(data), "target": target})
        blob.extend(data)
        return len(views) - 1

    for mat_index, (mat, pos, nrm, idx) in enumerate(meshes):
        color, rough = styles[mat]
        materials.append({
            "name": mat,
            "pbrMetallicRoughness": {
                "baseColorFactor": linear(color),
                "metallicFactor": 0.0,
                "roughnessFactor": rough,
            },
        })
        attrs = {}
        for key, arr in (("POSITION", pos), ("NORMAL", nrm)):
            accessors.append({
                "bufferView": add_view(arr.tobytes(), 34962),
                "componentType": 5126,
                "count": len(arr),
                "type": "VEC3",
                **({"min": [float(v) for v in arr.min(axis=0)],
                    "max": [float(v) for v in arr.max(axis=0)]} if key == "POSITION" else {}),
            })
            attrs[key] = len(accessors) - 1
        accessors.append({
            "bufferView": add_view(idx.tobytes(), 34963),
            "componentType": 5125,
            "count": len(idx),
            "type": "SCALAR",
        })
        primitives.append({"attributes": attrs, "indices": len(accessors) - 1, "material": mat_index})

    doc = {
        "asset": {"version": "2.0", "generator": "airchess tools/models/generate_models.py"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"name": name, "mesh": 0}],
        "meshes": [{"name": name, "primitives": primitives}],
        "materials": materials,
        "accessors": accessors,
        "bufferViews": views,
        "buffers": [{"byteLength": len(blob)}],
    }
    js = json.dumps(doc, sort_keys=True, separators=(",", ":")).encode()
    js += b" " * (-len(js) % 4)
    blob.extend(b"\0" * (-len(blob) % 4))
    total = 12 + 8 + len(js) + 8 + len(blob)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<II", len(js), 0x4E4F534A) + js)
        f.write(struct.pack("<II", len(blob), 0x004E4942) + bytes(blob))


# ------------------------------------------------------------------- usdz

def pin_zip_time(path):
    """Overwrites the DOS mtime of every entry so repackaging yields identical bytes."""
    data = bytearray(Path(path).read_bytes())
    with zipfile.ZipFile(path) as zf:
        offsets = [info.header_offset for info in zf.infolist()]
        entry = zf.start_dir
    for off in offsets:
        struct.pack_into("<HH", data, off + ZIP_LOCAL_TIME, DOS_TIME, DOS_DATE)
    for _ in offsets:
        struct.pack_into("<HH", data, entry + ZIP_CENTRAL_TIME, DOS_TIME, DOS_DATE)
        name_len, extra_len, comment_len = struct.unpack_from("<HHH", data, entry + ZIP_CENTRAL_LENGTHS)
        entry += ZIP_CENTRAL_FIXED + name_len + extra_len + comment_len
    Path(path).write_bytes(data)


def write_usdz(path, name, meshes, styles):
    """Writes a Y-up, meters ARKit USDZ with one UsdPreviewSurface material per mesh."""
    from pxr import Gf, Sdf, Usd, UsdGeom, UsdShade, UsdUtils, Vt

    with tempfile.TemporaryDirectory() as tmp:
        layer = Path(tmp) / f"{name}.usdc"
        stage = Usd.Stage.CreateNew(str(layer))
        UsdGeom.SetStageUpAxis(stage, UsdGeom.Tokens.y)
        UsdGeom.SetStageMetersPerUnit(stage, 1.0)

        root = UsdGeom.Xform.Define(stage, f"/{name}")
        stage.SetDefaultPrim(root.GetPrim())
        Usd.ModelAPI(root).SetKind("component")
        UsdGeom.Scope.Define(stage, f"/{name}/Materials")

        for mat, pos, nrm, idx in meshes:
            color, rough = styles[mat]
            mat_path = f"/{name}/Materials/{mat}"
            material = UsdShade.Material.Define(stage, mat_path)
            shader = UsdShade.Shader.Define(stage, f"{mat_path}/Surface")
            shader.CreateIdAttr("UsdPreviewSurface")
            shader.CreateInput("diffuseColor", Sdf.ValueTypeNames.Color3f).Set(Gf.Vec3f(*linear(color)[:3]))
            shader.CreateInput("metallic", Sdf.ValueTypeNames.Float).Set(0.0)
            shader.CreateInput("roughness", Sdf.ValueTypeNames.Float).Set(float(rough))
            material.CreateSurfaceOutput().ConnectToSource(shader.ConnectableAPI(), "surface")

            mesh = UsdGeom.Mesh.Define(stage, f"/{name}/{mat}")
            mesh.CreateSubdivisionSchemeAttr(UsdGeom.Tokens.none)
            mesh.CreateOrientationAttr(UsdGeom.Tokens.rightHanded)
            mesh.CreateDoubleSidedAttr(False)
            mesh.CreatePointsAttr(Vt.Vec3fArray.FromNumpy(pos))
            mesh.CreateFaceVertexCountsAttr(Vt.IntArray(len(idx) // 3, [3] * (len(idx) // 3)))
            mesh.CreateFaceVertexIndicesAttr(Vt.IntArray.FromNumpy(idx.astype(np.int32)))
            mesh.CreateExtentAttr(Vt.Vec3fArray([Gf.Vec3f(*map(float, pos.min(axis=0))),
                                                 Gf.Vec3f(*map(float, pos.max(axis=0)))]))
            normals = UsdGeom.PrimvarsAPI(mesh).CreatePrimvar(
                "normals", Sdf.ValueTypeNames.Normal3fArray, UsdGeom.Tokens.vertex)
            normals.Set(Vt.Vec3fArray.FromNumpy(nrm))
            UsdShade.MaterialBindingAPI.Apply(mesh.GetPrim()).Bind(material)

        stage.GetRootLayer().Save()
        if not UsdUtils.CreateNewARKitUsdzPackage(Sdf.AssetPath(str(layer)), str(path)):
            raise RuntimeError(f"{name}: USDZ packaging failed")
    pin_zip_time(path)


def read_usdz(path):
    """Returns (positions, indices, normals) over all meshes, like read_glb."""
    from pxr import Usd, UsdGeom

    stage = Usd.Stage.Open(str(path))
    pos, nrm, tris = [], [], []
    base = 0
    for prim in stage.Traverse():
        if not prim.IsA(UsdGeom.Mesh):
            continue
        mesh = UsdGeom.Mesh(prim)
        counts = np.asarray(mesh.GetFaceVertexCountsAttr().Get())
        assert (counts == 3).all(), f"{path.name}: non-triangle face"
        p = np.asarray(mesh.GetPointsAttr().Get(), dtype=np.float32)
        n = np.asarray(UsdGeom.PrimvarsAPI(mesh).GetPrimvar("normals").Get(), dtype=np.float32)
        ix = np.asarray(mesh.GetFaceVertexIndicesAttr().Get(), dtype=np.int64)
        pos.append(p)
        nrm.append(n)
        tris.append(ix.reshape(-1, 3) + base)
        base += len(p)
    return np.concatenate(pos), np.concatenate(tris), np.concatenate(nrm)


def check_usdz(path):
    from pxr.UsdUtils.complianceChecker import ComplianceChecker

    with warnings.catch_warnings():
        # Removed upstream in usd-core 26.8; requirements.txt pins the last release that has it.
        warnings.simplefilter("ignore", DeprecationWarning)
        checker = ComplianceChecker(arkit=True)
        checker.CheckCompliance(str(path))
    errors = checker.GetErrors() + checker.GetFailedChecks()
    assert not errors, f"{path.name}: USDZ compliance errors: {errors}"


def read_glb(path):
    """Minimal GLB reader: returns (positions, indices, normals) over all primitives."""
    data = Path(path).read_bytes()
    magic, version, length = struct.unpack_from("<III", data, 0)
    assert magic == 0x46546C67 and version == 2 and length == len(data)
    jlen, _ = struct.unpack_from("<II", data, 12)
    doc = json.loads(data[20:20 + jlen])
    blob = data[28 + jlen + 0:]
    blob = blob[:struct.unpack_from("<I", data, 20 + jlen)[0]]
    pos, nrm, tris = [], [], []
    base = 0
    for prim in doc["meshes"][0]["primitives"]:
        def get(i, dtype, cols):
            acc = doc["accessors"][i]
            view = doc["bufferViews"][acc["bufferView"]]
            arr = np.frombuffer(blob, dtype=dtype, count=acc["count"] * cols, offset=view["byteOffset"])
            return arr.reshape(-1, cols) if cols > 1 else arr

        p = get(prim["attributes"]["POSITION"], np.float32, 3)
        n = get(prim["attributes"]["NORMAL"], np.float32, 3)
        ix = get(prim["indices"], np.uint32, 1).astype(np.int64) + 0
        pos.append(p)
        nrm.append(n)
        tris.append(ix.reshape(-1, 3) + base)
        base += len(p)
    return np.concatenate(pos), np.concatenate(tris), np.concatenate(nrm)


def merged(meshes):
    """Concatenates meshes into (positions, triangle indices, normals) like the readers."""
    pos, nrm, tris = [], [], []
    base = 0
    for _, p, n, idx in meshes:
        pos.append(p)
        nrm.append(n)
        tris.append(idx.astype(np.int64).reshape(-1, 3) + base)
        base += len(p)
    return np.concatenate(pos), np.concatenate(tris), np.concatenate(nrm)


def assert_same(label, expected, actual):
    """Vertex/triangle counts must match the in-memory mesh; data must round-trip exactly."""
    for what, a, b in zip(("vertices", "triangles", "normals"), expected, actual):
        assert a.shape == b.shape, f"{label}: {what} {a.shape} != {b.shape}"
        assert np.array_equal(a, b), f"{label}: {what} differ"


# ------------------------------------------------------------------- main

def check_piece(name, pos, tris, nrm):
    lo, hi = pos.min(axis=0), pos.max(axis=0)
    height, diameter = PIECE_HEIGHTS[name], PIECE_DIAMETERS[name]
    assert abs(lo[1]) < 1e-6, f"{name}: base not on y=0"
    assert abs(hi[1] - height) <= height * HEIGHT_TOLERANCE, f"{name}: height {hi[1]}"
    assert max(abs(lo[0]), abs(hi[0]), abs(lo[2]), abs(hi[2])) <= diameter / 2 + 1e-6, f"{name}: too wide"
    assert len(tris) < 3000, f"{name}: {len(tris)} triangles"
    face = np.cross(pos[tris[:, 1]] - pos[tris[:, 0]], pos[tris[:, 2]] - pos[tris[:, 0]])
    assert (np.einsum("ij,ij->i", face, nrm[tris[:, 0]]) >= -1e-12).all(), f"{name}: flipped faces"


def generate(formats, glb_dir, usdz_dir):
    assets = [
        (f"{color}_{name}", [(color, build_piece(name, color == "white"))], PIECE_STYLES, name)
        for color in PIECE_STYLES
        for name in PIECE_NAMES
    ]
    assets.append(("board", board_prims(), BOARD_STYLES, None))

    for out_dir, fmt in ((glb_dir, "glb"), (usdz_dir, "usdz")):
        if fmt in formats:
            out_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    for label, prims, styles, piece in assets:
        meshes = build_meshes(prims)
        expected = merged(meshes)
        if piece:
            check_piece(piece, expected[0], expected[1], expected[2])

        if "glb" in formats:
            path = glb_dir / f"{label}.glb"
            write_glb(path, label, meshes, styles)
            assert_same(path.name, expected, read_glb(path))
            rows.append((path.name, expected[0], expected[1], path.stat().st_size))

        if "usdz" in formats:
            path = usdz_dir / f"{label}.usdz"
            write_usdz(path, label, meshes, styles)
            check_usdz(path)
            assert_same(path.name, expected, read_usdz(path))
            rows.append((path.name, expected[0], expected[1], path.stat().st_size))
    return rows


def report(rows):
    print(f"{'file':20} {'min xyz (m)':32} {'max xyz (m)':32} {'tris':>5} {'bytes':>7}")
    for name, pos, tris, size in rows:
        lo, hi = pos.min(axis=0), pos.max(axis=0)
        print(
            f"{name:20} {np.array2string(lo, precision=4, floatmode='fixed'):32} "
            f"{np.array2string(hi, precision=4, floatmode='fixed'):32} {len(tris):5d} {size:7d}"
        )


def preview(out_dir, png):
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection

    fig = plt.figure(figsize=(18, 6))
    for k, color in enumerate(PIECE_STYLES):
        for i, name in enumerate(PIECE_NAMES):
            ax = fig.add_subplot(2, 6, k * 6 + i + 1, projection="3d")
            pos, tris, nrm = read_glb(out_dir / f"{color}_{name}.glb")
            polys = pos[tris]
            shade = np.clip(0.35 + 0.65 * (nrm[tris].mean(axis=1) @ np.array([0.3, 0.8, 0.5])), 0, 1)
            base = np.array([0.93, 0.89, 0.81]) if color == "white" else np.array([0.35, 0.28, 0.24])
            # Plot with Z as depth so +Y is up and the knight faces the viewer's right.
            quad = polys[:, :, [0, 2, 1]]
            coll = Poly3DCollection(quad, facecolors=[base * s for s in shade], edgecolor="none")
            ax.add_collection3d(coll)
            ax.set_xlim(-0.02, 0.02)
            ax.set_ylim(-0.02, 0.02)
            ax.set_zlim(0, 0.04)
            ax.view_init(elev=15, azim=-60)
            ax.set_title(f"{color} {name}")
            ax.set_axis_off()
    png.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(png, dpi=90)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--formats", choices=("glb", "usdz", "both"), default="both",
                        help="which formats to write (default: both)")
    parser.add_argument("--out", type=Path, default=OUT_DIR, help="GLB output directory")
    parser.add_argument("--usdz-out", type=Path, default=USDZ_DIR, help="USDZ output directory")
    parser.add_argument("--preview", type=Path, help="also write a matplotlib preview PNG (needs glb)")
    args = parser.parse_args()

    formats = FORMATS if args.formats == "both" else (args.formats,)
    if args.preview and "glb" not in formats:
        parser.error("--preview reads the GLB output; use --formats glb or both")

    rows = generate(formats, args.out, args.usdz_out)
    report(rows)
    if args.preview:
        preview(args.out, args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main())
