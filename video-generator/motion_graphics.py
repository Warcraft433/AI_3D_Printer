"""
Motion Graphics & UI Compositing Engine for PrintGuard AI Video Generator
Provides high-fidelity drawing primitives, fonts, glassmorphism cards,
HUD overlays, Ken Burns animations, and tech visual effects.
"""

import math
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# -------------------------------------------------------------
# Color Palette (Dark Charcoal, Electric Cyan, Deep Violet)
# -------------------------------------------------------------
CLR_BG_BASE = (11, 15, 25)            # #0B0F19 Charcoal
CLR_BG_DEEP = (6, 8, 14)              # Deep black border vignette
CLR_CYAN = (0, 240, 255)              # #00F0FF Electric Cyan
CLR_CYAN_DIM = (0, 140, 160)
CLR_VIOLET = (139, 92, 246)           # #8B5CF6 Accent Violet
CLR_VIOLET_DIM = (80, 50, 150)
CLR_TEXT_MAIN = (248, 250, 252)       # Off-white primary text
CLR_TEXT_MUTED = (148, 163, 184)      # Slate muted text
CLR_CARD_BG = (18, 24, 38, 220)       # Dark glassmorphic background
CLR_CARD_BORDER = (45, 60, 90)        # Subtle border
CLR_ALERT = (239, 68, 68)             # Red defect alert
CLR_ALERT_BG = (40, 15, 20, 210)
CLR_SUCCESS = (34, 197, 94)           # Green verified
CLR_GRID = (20, 28, 44)               # Grid lines

FONT_PATHS = {
    "regular": "C:/Windows/Fonts/segoeui.ttf",
    "bold": "C:/Windows/Fonts/segoeuib.ttf",
    "mono": "C:/Windows/Fonts/consola.ttf",
    "mono_bold": "C:/Windows/Fonts/consolab.ttf",
    "industrial": "C:/Windows/Fonts/bahnschrift.ttf"
}

_FONT_CACHE = {}

def get_font(font_type="regular", size=24):
    key = (font_type, size)
    if key not in _FONT_CACHE:
        path = FONT_PATHS.get(font_type, FONT_PATHS["regular"])
        if os.path.exists(path):
            _FONT_CACHE[key] = ImageFont.truetype(path, size)
        else:
            _FONT_CACHE[key] = ImageFont.load_default()
    return _FONT_CACHE[key]

# -------------------------------------------------------------
# Easing Curves
# -------------------------------------------------------------
def ease_out_cubic(x):
    return 1.0 - math.pow(1.0 - max(0.0, min(1.0, x)), 3)

def ease_in_out_sine(x):
    x = max(0.0, min(1.0, x))
    return -(math.cos(math.pi * x) - 1.0) / 2.0

def smoothstep(edge0, edge1, x):
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)

# -------------------------------------------------------------
# Background & Ambient Atmosphere
# -------------------------------------------------------------
def create_tech_background(width=1920, height=1080, time_sec=0.0):
    """Creates a dark charcoal background with vignette, subtle animated grid, and particles."""
    # Create gradient background
    img = Image.new("RGBA", (width, height), CLR_BG_BASE + (255,))
    draw = ImageDraw.Draw(img)
    
    # Grid spacing
    grid_size = 80
    grid_offset_y = int((time_sec * 8.0) % grid_size)
    
    # Draw subtle background grid
    for x in range(0, width, grid_size):
        draw.line([(x, 0), (x, height)], fill=CLR_GRID + (120,), width=1)
    for y in range(grid_offset_y, height, grid_size):
        draw.line([(0, y), (width, y)], fill=CLR_GRID + (120,), width=1)
        
    # Grid intersection crosses
    cross_size = 3
    for x in range(0, width, grid_size * 2):
        for y in range(grid_offset_y, height, grid_size * 2):
            draw.line([(x - cross_size, y), (x + cross_size, y)], fill=CLR_CYAN_DIM + (90,), width=1)
            draw.line([(x, y - cross_size), (x, y + cross_size)], fill=CLR_CYAN_DIM + (90,), width=1)
            
    # Ambient floating dust / glow particles (deterministic seed by floor index)
    np.random.seed(42)
    num_particles = 35
    px = np.random.uniform(50, width - 50, num_particles)
    py = (np.random.uniform(50, height - 50, num_particles) - time_sec * 15.0) % height
    psize = np.random.uniform(1.5, 3.5, num_particles)
    palpha = np.random.uniform(40, 110, num_particles)
    
    for i in range(num_particles):
        x_p, y_p = px[i], py[i]
        r_p = psize[i]
        alpha = int(palpha[i] * (0.6 + 0.4 * math.sin(time_sec * 2.0 + i)))
        draw.ellipse([x_p - r_p, y_p - r_p, x_p + r_p, y_p + r_p], fill=CLR_CYAN + (alpha,))
        
    # Vignette shadow at edges
    # Top and bottom bars
    vignette = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    v_draw = ImageDraw.Draw(vignette)
    for i in range(120):
        a = int(180 * (1.0 - i / 120.0))
        v_draw.line([(0, i), (width, i)], fill=(6, 8, 14, a))
        v_draw.line([(0, height - 1 - i), (width, height - 1 - i)], fill=(6, 8, 14, a))
    img.alpha_composite(vignette)
    
    return img

# -------------------------------------------------------------
# Glassmorphic Card
# -------------------------------------------------------------
def draw_glass_card(draw, bbox, title="", border_color=CLR_CARD_BORDER, bg_color=CLR_CARD_BG, corner_radius=12):
    """Draws a modern translucent card with rounded corners and border."""
    x0, y0, x1, y1 = bbox
    # Rounded rectangle background
    draw.rounded_rectangle(bbox, radius=corner_radius, fill=bg_color, outline=border_color, width=2)
    
    # Corner accent ticks (tech aesthetic)
    tick = 10
    draw.line([(x0, y0 + tick), (x0, y0), (x0 + tick, y0)], fill=CLR_CYAN, width=2)
    draw.line([(x1 - tick, y0), (x1, y0), (x1, y0 + tick)], fill=CLR_CYAN, width=2)
    draw.line([(x0, y1 - tick), (x0, y1), (x0 + tick, y1)], fill=CLR_CYAN, width=2)
    draw.line([(x1 - tick, y1), (x1, y1), (x1, y1 - tick)], fill=CLR_CYAN, width=2)
    
    if title:
        f_title = get_font("mono_bold", 16)
        draw.text((x0 + 18, y0 + 14), title.upper(), font=f_title, fill=CLR_CYAN)

# -------------------------------------------------------------
# Ken Burns Image Viewer (Pan & Zoom)
# -------------------------------------------------------------
def render_ken_burns_image(img_path, width, height, progress, zoom_start=1.0, zoom_end=1.06, pan_x=0.0, pan_y=0.0):
    """Loads and crops an image with smooth zoom and pan."""
    if not os.path.exists(img_path):
        # Fallback dark placeholder
        return Image.new("RGBA", (width, height), (20, 25, 40, 255))
        
    src = Image.open(img_path).convert("RGBA")
    sw, sh = src.size
    
    cur_zoom = zoom_start + (zoom_end - zoom_start) * ease_in_out_sine(progress)
    
    # Target aspect ratio
    target_aspect = width / height
    src_aspect = sw / sh
    
    if src_aspect > target_aspect:
        # Source is wider
        crop_h = sh / cur_zoom
        crop_w = crop_h * target_aspect
    else:
        # Source is taller
        crop_w = sw / cur_zoom
        crop_h = crop_w / target_aspect
        
    # Center crop with pan offset
    cx = (sw / 2.0) + (pan_x * (sw - crop_w) * 0.4)
    cy = (sh / 2.0) + (pan_y * (sh - crop_h) * 0.4)
    
    x0 = max(0, cx - crop_w / 2.0)
    y0 = max(0, cy - crop_h / 2.0)
    x1 = min(sw, x0 + crop_w)
    y1 = min(sh, y0 + crop_h)
    
    cropped = src.crop((int(x0), int(y0), int(x1), int(y1)))
    resized = cropped.resize((width, height), Image.Resampling.BILINEAR)
    return resized

# -------------------------------------------------------------
# HUD Defect Bounding Box
# -------------------------------------------------------------
def draw_defect_hud(draw, box, label="DEFECT DETECTED: SPAGHETTI", confidence=0.926, alert_state=True, scan_line_y=None):
    """Draws a high-tech AI detection bounding box with corners, label badge, and telemetry without obscuring underlying image."""
    x0, y0, x1, y1 = box
    color = CLR_ALERT if alert_state else CLR_SUCCESS
    
    # Bounding box thin border
    draw.rectangle(box, outline=color, width=2)
    
    # High-tech bracket corners (thick 4px grips)
    corner_len = 28
    w = 4
    # Top-Left
    draw.line([(x0, y0), (x0 + corner_len, y0)], fill=color, width=w)
    draw.line([(x0, y0), (x0, y0 + corner_len)], fill=color, width=w)
    # Top-Right
    draw.line([(x1, y0), (x1 - corner_len, y0)], fill=color, width=w)
    draw.line([(x1, y0), (x1, y0 + corner_len)], fill=color, width=w)
    # Bottom-Left
    draw.line([(x0, y1), (x0 + corner_len, y1)], fill=color, width=w)
    draw.line([(x0, y1), (x0, y1 - corner_len)], fill=color, width=w)
    # Bottom-Right
    draw.line([(x1, y1), (x1 - corner_len, y1)], fill=color, width=w)
    draw.line([(x1, y1), (x1, y1 - corner_len)], fill=color, width=w)
    
    # Center target reticle
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    r_ret = 12
    draw.line([(cx - r_ret, cy), (cx + r_ret, cy)], fill=(255, 255, 255), width=2)
    draw.line([(cx, cy - r_ret), (cx, cy + r_ret)], fill=(255, 255, 255), width=2)
    draw.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=color)
    
    # Scanning laser line within bounding box if provided
    if scan_line_y is not None and y0 <= scan_line_y <= y1:
        draw.line([(x0 + 4, scan_line_y), (x1 - 4, scan_line_y)], fill=(255, 100, 100), width=2)
    
    # Top Header Badge
    f_badge = get_font("mono_bold", 15)
    text = f" {label} [{confidence*100:.1f}%] "
    text_w = int(draw.textlength(text, font=f_badge))
    badge_h = 28
    badge_rect = [x0, max(0, y0 - badge_h), x0 + text_w + 10, y0]
    draw.rectangle(badge_rect, fill=color)
    draw.text((x0 + 6, max(0, y0 - badge_h) + 5), text, font=f_badge, fill=(255, 255, 255))

# -------------------------------------------------------------
# Animated Connecting Cable / Wire
# -------------------------------------------------------------
def draw_data_wire(draw, pt1, pt2, progress=0.0, pulse_color=CLR_CYAN, base_color=(40, 55, 80)):
    """Draws a stylized data connection line with a traveling pulse packet."""
    x1, y1 = pt1
    x2, y2 = pt2
    
    # Draw base connector wire
    draw.line([(x1, y1), (x2, y2)], fill=base_color, width=2)
    
    # Calculate pulse packet position
    px = x1 + (x2 - x1) * (progress % 1.0)
    py = y1 + (y2 - y1) * (progress % 1.0)
    
    # Pulse packet with trail
    draw.line([(px - (x2 - x1) * 0.08, py - (y2 - y1) * 0.08), (px, py)], fill=pulse_color, width=4)
    draw.ellipse([px - 4, py - 4, px + 4, py + 4], fill=(255, 255, 255))
