"""
Source-of-truth helper for the radiationzones gas mask textures.

What it does:
  * Generates the worn-armor atlas at
    `assets/radiationzones/textures/models/armor/gas_mask_layer_1.png` —
    a 64x64 RGBA PNG laid out in Minecraft's CubeListBuilder
    box-unwrap convention (top/bottom/right/front/left/back) for each
    of the 9 cubes that make up the QSMP gas mask. The colors are the
    QSMP palette sampled from the original Sketchfab GLB texture
    (`attached_assets/qsmp_gas_mask_3d_model_1777807351643.glb` by
    skibidi). We CANNOT ship the GLB's PNG verbatim because Minecraft
    requires a fixed cube-unwrap layout that the GLB's per-face UVs do
    not respect — doing so causes every cube face to sample a random
    region of the atlas (visible in-game as one bright square eye and
    dark blobs everywhere else).
  * Generates two 16x16 inventory icons (`gas_mask.png`,
    `gas_mask_filtered.png`) using the same QSMP palette so the
    inventory and worn model read as the same item, plus a
    `gas_mask_filtered_canister.png` overlay tinted at runtime.

Source GLB: "QSMP Gas Mask 3D model" by skibidi on Sketchfab
  https://sketchfab.com/3d-models/qsmp-gas-mask-3d-model-d33075f4f86a40dea4cc908b2d0ddbfa

Atlas layout (must stay in lockstep with GasMaskModel.java texOffs!):
  head_shell        @ (0,  0)   40x20   (10x10x10)
  lens_right        @ (0, 20)   12x6    (4x4x2)
  lens_left         @ (12,20)   12x6    (4x4x2)
  filter_right      @ (24,20)   10x6    (2x3x3)
  filter_left       @ (34,20)   10x6    (2x3x3)
  strap_right_inner @ (44,20)   10x4    (3x2x2)
  strap_left_inner  @ (54,20)   10x4    (3x2x2)
  snout_base        @ (0, 28)   12x6    (3x3x3)
  snout_canister    @ (12,28)   12x8    (2x4x4)
  face_plate        @ (24,28)   22x8    (8x5x3)

Run from repo root:
    python3 .local/scripts/gen_gas_mask_textures.py
"""
from __future__ import annotations

import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
ARMOR_OUT = os.path.join(
    ROOT, "radiationzones-mod", "src", "main", "resources",
    "assets", "radiationzones", "textures", "models", "armor", "gas_mask_layer_1.png")
ITEM_EMPTY = os.path.join(
    ROOT, "radiationzones-mod", "src", "main", "resources",
    "assets", "radiationzones", "textures", "item", "gas_mask.png")
ITEM_FILTERED = os.path.join(
    ROOT, "radiationzones-mod", "src", "main", "resources",
    "assets", "radiationzones", "textures", "item", "gas_mask_filtered.png")
ITEM_FILTERED_CANISTER = os.path.join(
    ROOT, "radiationzones-mod", "src", "main", "resources",
    "assets", "radiationzones", "textures", "item", "gas_mask_filtered_canister.png")


# ---------------------------------------------------------------------------
# QSMP palette — sampled from the original GLB texture (color-counted with
# Pillow). The QSMP gas mask is matte black rubber with cool grey-green
# glass lenses and dark metal hardware.
# ---------------------------------------------------------------------------
RUBBER_BASE = (14, 13, 15, 255)
RUBBER_DARK = (8, 7, 9, 255)
RUBBER_LIGHT = (40, 35, 38, 255)
RUBBER_HI = (58, 52, 52, 255)
LENS_RIM = (10, 9, 11, 255)
LENS_GLASS = (172, 189, 185, 255)
LENS_GLASS_HI = (227, 233, 230, 255)
LENS_GLASS_DK = (78, 90, 92, 255)
METAL_DARK = (30, 26, 30, 255)
METAL_MID = (90, 88, 92, 255)
METAL_HI = (200, 211, 206, 255)
CAN_BODY = (40, 35, 38, 255)
CAN_RIB = (10, 9, 11, 255)
CAN_CAP = (90, 88, 92, 255)
CAN_HOLE = (5, 4, 5, 255)


# ---------------------------------------------------------------------------
# Box-unwrap helper. For a cube of size w*h*d at texOffs(u,v), Minecraft's
# CubeListBuilder unwraps the six faces as:
#
#   top:    (u+d,       v),       (w, d)
#   bottom: (u+d+w,     v),       (w, d)
#   right:  (u,         v+d),     (d, h)   -- the -X face
#   front:  (u+d,       v+d),     (w, h)   -- the -Z face (faces the camera)
#   left:   (u+d+w,     v+d),     (d, h)   -- the +X face
#   back:   (u+d+w+d,   v+d),     (w, h)   -- the +Z face
# Total atlas footprint per cube: width = 2*(w+d), height = d + h.
#
# Each face is filled either with a solid RGBA tuple or with a callable
# (face_x, face_y) -> RGBA so we can paint highlights/details per pixel.
# ---------------------------------------------------------------------------

def _fill(img, x, y, fw, fh, c):
    if callable(c):
        for px in range(fw):
            for py in range(fh):
                img.putpixel((x + px, y + py), c(px, py))
    else:
        for px in range(fw):
            for py in range(fh):
                img.putpixel((x + px, y + py), c)


def box(img, u, v, w, h, d, top, bottom, right, front, left, back):
    """Paint the six faces of a cube into the atlas at texOffs(u,v)."""
    _fill(img, u + d,           v,     w, d, top)
    _fill(img, u + d + w,       v,     w, d, bottom)
    _fill(img, u,               v + d, d, h, right)
    _fill(img, u + d,           v + d, w, h, front)
    _fill(img, u + d + w,       v + d, d, h, left)
    _fill(img, u + d + w + d,   v + d, w, h, back)


def _shaded(base, hi, dk):
    """Return a face-painter that adds a 1px highlight on the top edge and
    a 1px shadow on the bottom edge — gives flat rubber a hint of depth."""
    def paint(fx, fy):
        # Will be called per-pixel; we don't know the face size here, so
        # rely on the caller's local size via closure. Use a sentinel that
        # the outer code can wrap — but since fy==0 -> top edge across the
        # whole face is all we need, this is fine without knowing fh.
        if fy == 0:
            return hi
        return base
    return paint


def _rubber_with_seam(seam_y):
    """Rubber face with a horizontal dark seam at row `seam_y`."""
    def paint(fx, fy):
        if fy == seam_y:
            return RUBBER_DARK
        if fy == 0:
            return RUBBER_HI
        return RUBBER_BASE
    return paint


def _lens_front(fw, fh):
    """Glass eye: 1px black rim, glass interior with a corner highlight
    and an opposing shadow corner."""
    def paint(fx, fy):
        if fx == 0 or fx == fw - 1 or fy == 0 or fy == fh - 1:
            return LENS_RIM
        # Highlight in the upper-left of the interior.
        if (fx, fy) == (1, 1):
            return LENS_GLASS_HI
        if (fx, fy) == (2, 1) or (fx, fy) == (1, 2):
            return LENS_GLASS_HI
        # Shadow in the lower-right of the interior.
        if (fx, fy) == (fw - 2, fh - 2):
            return LENS_GLASS_DK
        return LENS_GLASS
    return paint


def _can_top():
    """Top of the canister cap (2x4 region) — lighter metal."""
    return CAN_CAP


def _can_bottom(fw, fh):
    """Bottom of the canister — has the intake hole punched through."""
    def paint(fx, fy):
        # Center 2x2 pixels = the dark hole.
        cx0, cx1 = fw // 2 - 1, fw // 2
        cy0, cy1 = fh // 2 - 1, fh // 2
        if fx in (cx0, cx1) and fy in (cy0, cy1):
            return CAN_HOLE
        return CAN_CAP
    return paint


def _can_side(fw, fh):
    """Side of the canister body — vertical ribs on the body, lighter
    cap stripe at the top edge (closest to the snout)."""
    def paint(fx, fy):
        if fy == 0:
            return CAN_CAP
        # Rib lines every other pixel for that ribbed-canister look.
        if fx % 2 == 0:
            return CAN_RIB
        return CAN_BODY
    return paint


def _filter_cap_outer(fw, fh):
    """Outer face of the side filter cap — concentric metal cap look."""
    def paint(fx, fy):
        if fx == 0 or fx == fw - 1 or fy == 0 or fy == fh - 1:
            return METAL_DARK
        return METAL_MID
    return paint


# ---------------------------------------------------------------------------
# Build the armor atlas.
# ---------------------------------------------------------------------------

def build_armor_atlas() -> Image.Image:
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # ------- head_shell (10x10x10) at (0,0) — outer rubber dome ----------
    # Top of the head: a slightly lighter band along the front edge
    # (closest to the face) so the QSMP brow ridge reads in 3D.
    def head_top(fx, fy):
        if fy >= 8:
            return RUBBER_HI
        return RUBBER_BASE
    head_front = _rubber_with_seam(7)  # seam where face plate meets dome
    box(img,
        u=0, v=0, w=10, h=10, d=10,
        top=head_top,
        bottom=RUBBER_DARK,
        right=RUBBER_BASE,
        front=head_front,
        left=RUBBER_BASE,
        back=RUBBER_BASE)

    # ------- lens_right (4x4x2) at (0,20) -------------------------------
    box(img,
        u=0, v=20, w=4, h=4, d=2,
        top=LENS_RIM, bottom=LENS_RIM,
        right=LENS_RIM, left=LENS_RIM, back=LENS_RIM,
        front=_lens_front(4, 4))

    # ------- lens_left (4x4x2) at (12,20) -------------------------------
    box(img,
        u=12, v=20, w=4, h=4, d=2,
        top=LENS_RIM, bottom=LENS_RIM,
        right=LENS_RIM, left=LENS_RIM, back=LENS_RIM,
        front=_lens_front(4, 4))

    # ------- filter_right (2x3x3) at (24,20) ----------------------------
    # Side filter cap: the visible "outer" face is the +X face (left).
    box(img,
        u=24, v=20, w=2, h=3, d=3,
        top=METAL_DARK, bottom=METAL_DARK,
        right=METAL_DARK,
        front=METAL_DARK,
        left=_filter_cap_outer(3, 3),
        back=METAL_DARK)

    # ------- filter_left (2x3x3) at (34,20) -----------------------------
    # Symmetric: the visible outer face is the -X face (right).
    box(img,
        u=34, v=20, w=2, h=3, d=3,
        top=METAL_DARK, bottom=METAL_DARK,
        right=_filter_cap_outer(3, 3),
        front=METAL_DARK,
        left=METAL_DARK,
        back=METAL_DARK)

    # ------- strap_right_inner (3x2x2) at (44,20) -----------------------
    box(img,
        u=44, v=20, w=3, h=2, d=2,
        top=RUBBER_BASE, bottom=RUBBER_DARK,
        right=RUBBER_BASE, front=RUBBER_BASE,
        left=RUBBER_BASE, back=RUBBER_BASE)

    # ------- strap_left_inner (3x2x2) at (54,20) ------------------------
    box(img,
        u=54, v=20, w=3, h=2, d=2,
        top=RUBBER_BASE, bottom=RUBBER_DARK,
        right=RUBBER_BASE, front=RUBBER_BASE,
        left=RUBBER_BASE, back=RUBBER_BASE)

    # ------- snout_base (3x3x3) at (0,28) -------------------------------
    # The threaded mount that the canister screws onto. A bit lighter
    # than rubber to suggest worn metal; no canister hole on the front
    # face (the canister cube covers it).
    def snout_front(fx, fy):
        if fx == 0 or fx == 2 or fy == 0 or fy == 2:
            return METAL_DARK
        return METAL_MID
    box(img,
        u=0, v=28, w=3, h=3, d=3,
        top=METAL_DARK, bottom=METAL_DARK,
        right=METAL_DARK, left=METAL_DARK, back=METAL_DARK,
        front=snout_front)

    # ------- snout_canister (2x4x4) at (12,28) --------------------------
    # The forward filter canister. Its FRONT face has the intake hole.
    def can_front(fx, fy):
        # 2x4 face. Center 2x2 = intake hole.
        if 0 <= fx <= 1 and 1 <= fy <= 2:
            return CAN_HOLE
        return CAN_CAP
    box(img,
        u=12, v=28, w=2, h=4, d=4,
        top=_can_top(),
        bottom=_can_bottom(2, 4),
        right=_can_side(4, 4),
        left=_can_side(4, 4),
        back=CAN_BODY,
        front=can_front)

    # ------- face_plate (8x5x3) at (24,28) ------------------------------
    # The flat rubber piece between the lenses and the snout. Front face
    # has the cheek ridges (slightly lighter horizontal band).
    def face_front(fx, fy):
        if fy == 0:
            return RUBBER_HI
        if fy == 4:
            return RUBBER_DARK
        if fy == 2 and 1 <= fx <= 6:
            return RUBBER_LIGHT
        return RUBBER_BASE
    box(img,
        u=24, v=28, w=8, h=5, d=3,
        top=RUBBER_BASE, bottom=RUBBER_DARK,
        right=RUBBER_BASE, left=RUBBER_BASE, back=RUBBER_BASE,
        front=face_front)

    return img


def gen_armor() -> None:
    os.makedirs(os.path.dirname(ARMOR_OUT), exist_ok=True)
    build_armor_atlas().save(ARMOR_OUT)


# ---------------------------------------------------------------------------
# Inventory icons (16x16) — kept as-is, they read fine. The canister
# overlay is white-only so a runtime ItemColor can tint per filter tier.
# ---------------------------------------------------------------------------

def _icon_base(with_filter: bool) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(3, 13):
        img.putpixel((x, 1), RUBBER_LIGHT)
        img.putpixel((x, 2), RUBBER_DARK)
    face_rows = [
        (3, 13, 3), (2, 14, 4), (2, 14, 5), (2, 14, 6), (2, 14, 7),
        (2, 14, 8), (3, 13, 9), (4, 12, 10), (5, 11, 11), (5, 11, 12),
        (6, 10, 13),
    ]
    for x0, x1, y in face_rows:
        for x in range(x0, x1):
            img.putpixel((x, y), RUBBER_BASE)
    outline_rows = [
        (3, 12, 3), (2, 13, 4), (2, 13, 8), (3, 12, 9),
        (4, 11, 10), (5, 10, 11), (5, 10, 12), (6, 9, 13),
    ]
    for x0, x1, y in outline_rows:
        img.putpixel((x0, y), RUBBER_DARK)
        img.putpixel((x1, y), RUBBER_DARK)

    def lens(cx: int):
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                img.putpixel((cx + dx, 5 + dy), LENS_RIM)
        img.putpixel((cx, 5), LENS_GLASS)
        img.putpixel((cx - 1, 5), LENS_GLASS)
        img.putpixel((cx + 1, 5), LENS_GLASS)
        img.putpixel((cx, 4), LENS_GLASS)
        img.putpixel((cx, 6), LENS_GLASS)
        img.putpixel((cx - 1, 4), LENS_GLASS_HI)
        img.putpixel((cx + 1, 6), LENS_GLASS_DK)

    lens(5)
    lens(10)

    img.putpixel((1, 5), METAL_DARK); img.putpixel((1, 6), CAN_CAP); img.putpixel((1, 7), METAL_DARK)
    img.putpixel((14, 5), METAL_DARK); img.putpixel((14, 6), CAN_CAP); img.putpixel((14, 7), METAL_DARK)

    for x in range(6, 10): img.putpixel((x, 11), METAL_DARK)
    for x in range(6, 10): img.putpixel((x, 12), RUBBER_HI)
    img.putpixel((6, 12), METAL_DARK); img.putpixel((9, 12), METAL_DARK)
    img.putpixel((7, 12), CAN_HOLE);   img.putpixel((8, 12), CAN_HOLE)
    img.putpixel((7, 11), METAL_HI)

    if with_filter:
        for x in range(5, 11): img.putpixel((x, 13), CAN_CAP)
        for y in range(14, 16):
            for x in range(5, 11):
                img.putpixel((x, y), CAN_BODY)
        for x in range(5, 11): img.putpixel((x, 14), CAN_RIB)
        img.putpixel((7, 15), CAN_HOLE); img.putpixel((8, 15), CAN_HOLE)
        img.putpixel((4, 14), CAN_RIB); img.putpixel((11, 14), CAN_RIB)
        img.putpixel((4, 15), CAN_RIB); img.putpixel((11, 15), CAN_RIB)
    return img


def _canister_overlay() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    WHITE = (255, 255, 255, 255)
    for x in range(5, 11):
        img.putpixel((x, 13), WHITE)
        img.putpixel((x, 15), WHITE)
    img.putpixel((7, 15), (0, 0, 0, 0))
    img.putpixel((8, 15), (0, 0, 0, 0))
    img.putpixel((6, 13), WHITE)
    return img


def gen_icons() -> None:
    os.makedirs(os.path.dirname(ITEM_EMPTY), exist_ok=True)
    _icon_base(False).save(ITEM_EMPTY)
    _icon_base(True).save(ITEM_FILTERED)
    _canister_overlay().save(ITEM_FILTERED_CANISTER)


def main() -> None:
    gen_armor()
    gen_icons()
    print("wrote:", ARMOR_OUT)
    print("wrote:", ITEM_EMPTY)
    print("wrote:", ITEM_FILTERED)
    print("wrote:", ITEM_FILTERED_CANISTER)


if __name__ == "__main__":
    main()
