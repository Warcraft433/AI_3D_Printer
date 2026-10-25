"""
Scene Renderers for PrintGuard AI Cinematic Showcase Video
Contains dedicated rendering routines for Scenes 1 through 7.
"""

import math
import os
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from motion_graphics import (
    CLR_BG_BASE, CLR_CYAN, CLR_CYAN_DIM, CLR_VIOLET, CLR_VIOLET_DIM,
    CLR_TEXT_MAIN, CLR_TEXT_MUTED, CLR_CARD_BG, CLR_CARD_BORDER,
    CLR_ALERT, CLR_ALERT_BG, CLR_SUCCESS,
    get_font, ease_out_cubic, ease_in_out_sine, smoothstep,
    create_tech_background, draw_glass_card, render_ken_burns_image,
    draw_defect_hud, draw_data_wire
)
from wireframe_3d import Wireframe3D

# Global wireframe engine instance
_WIREFRAME = Wireframe3D()

# Preloaded assets cache
_ASSET_CACHE = {}

def get_asset_image(name):
    if name not in _ASSET_CACHE:
        path = os.path.join("video-generator", "assets", name)
        if os.path.exists(path):
            _ASSET_CACHE[name] = Image.open(path).convert("RGBA")
        else:
            _ASSET_CACHE[name] = None
    return _ASSET_CACHE[name]

# ----------------------------------------------------------------------
# Scene 1: Cinematic Opening (0.0s – 6.0s)
# ----------------------------------------------------------------------
def render_scene_1(t, dur=6.0):
    p = t / dur
    img = create_tech_background(1920, 1080, time_sec=t)
    draw = ImageDraw.Draw(img)
    
    # 3D Wireframe rotating in upper-middle area
    yaw = t * 0.45
    pitch = 0.35 + 0.15 * math.sin(t * 0.3)
    roll = t * 0.15
    laser_p = (t * 0.5) % 1.0
    
    # Scale eases in from 0 to 290
    wire_scale = 290.0 * min(1.0, ease_out_cubic(t / 1.5))
    _WIREFRAME.render(draw, center_x=960, center_y=380, scale=wire_scale,
                      yaw=yaw, pitch=pitch, roll=roll, laser_progress=laser_p)
    
    # Title Reveal (Starts at t=1.0s)
    if t >= 1.0:
        p_title = ease_out_cubic((t - 1.0) / 1.5)
        alpha_title = int(255 * p_title)
        
        # Glow layer behind title
        f_title = get_font("bold", 72)
        title_text = "PRINTGUARD AI"
        tw = draw.textlength(title_text, font=f_title)
        tx = (1920 - tw) // 2
        ty = 650
        
        # Soft cyan glow offset
        glow_alpha = int(90 * p_title)
        for ox, oy in [(-2, 0), (2, 0), (0, -2), (0, 2)]:
            draw.text((tx + ox, ty + oy), title_text, font=f_title, fill=CLR_CYAN + (glow_alpha,))
        draw.text((tx, ty), title_text, font=f_title, fill=CLR_TEXT_MAIN + (alpha_title,))
        
    # Subtitle Reveal (Starts at t=2.2s)
    if t >= 2.2:
        p_sub = ease_out_cubic((t - 2.2) / 1.2)
        alpha_sub = int(240 * p_sub)
        f_sub = get_font("regular", 28)
        sub_text = "Smarter 3D Printing. Intelligent Error Detection."
        sw = draw.textlength(sub_text, font=f_sub)
        draw.text(((1920 - sw) // 2, 745), sub_text, font=f_sub, fill=CLR_CYAN + (alpha_sub,))
        
    # System Specs Pill (Starts at t=3.2s)
    if t >= 3.2:
        p_pill = ease_out_cubic((t - 3.2) / 1.0)
        alpha_pill = int(255 * p_pill)
        pill_rect = [620, 815, 1300, 855]
        draw.rounded_rectangle(pill_rect, radius=8, fill=(18, 24, 38, alpha_pill),
                               outline=(0, 240, 255, int(150 * p_pill)), width=1)
        f_pill = get_font("mono", 15)
        pill_text = "AUTONOMOUS PRINT MONITORING  |  YOLOv8 VISION  |  SPRING BOOT 3.3"
        pw = draw.textlength(pill_text, font=f_pill)
        draw.text(((1920 - pw) // 2, 824), pill_text, font=f_pill, fill=CLR_TEXT_MUTED + (alpha_pill,))
        
    # Corner HUD Elements
    f_hud = get_font("mono", 14)
    draw.text((60, 50), "SYS: PRINTGUARD_AI // STABLE", font=f_hud, fill=CLR_CYAN_DIM)
    draw.text((60, 72), "AI WATCHDOG: ACTIVE", font=f_hud, fill=CLR_CYAN_DIM)
    draw.text((1720, 50), "FPS: 30.00", font=f_hud, fill=CLR_CYAN_DIM)
    draw.text((1720, 72), "CAM: 1080P_ONLINE", font=f_hud, fill=CLR_CYAN_DIM)
    
    return img

# ----------------------------------------------------------------------
# Scene 2: The Problem (6.0s – 14.0s)
# ----------------------------------------------------------------------
def render_scene_2(t, dur=8.0):
    p = t / dur
    img = create_tech_background(1920, 1080, time_sec=6.0 + t)
    draw = ImageDraw.Draw(img)
    
    # Header
    f_cat = get_font("mono_bold", 16)
    draw.text((100, 60), "SECTION 01 // CRITICAL FAILURE MODES", font=f_cat, fill=CLR_ALERT)
    f_head = get_font("bold", 44)
    draw.text((100, 90), "Printing Errors Waste Time, Material, and Effort", font=f_head, fill=CLR_TEXT_MAIN)
    f_sub = get_font("regular", 20)
    draw.text((100, 150), "Unattended 3D print failures run for hours, ruining entire filament spools and damaging printer hotends.", font=f_sub, fill=CLR_TEXT_MUTED)
    
    # Left Box: Real Defect Image with Dynamic HUD Bounding Box
    left_x0, left_y0, left_w, left_h = 100, 210, 840, 580
    draw_glass_card(draw, [left_x0, left_y0, left_x0 + left_w, left_y0 + left_h],
                    title="OPTICAL DEFECT TELEMETRY // REAL DETECTIONS",
                    border_color=(120, 40, 50), bg_color=(20, 15, 25, 230))
    
    # Render actual defect image
    defect_img = render_ken_burns_image(
        "video-generator/assets/defect_1.jpg", 800, 440,
        progress=p, zoom_start=1.0, zoom_end=1.04, pan_x=0.01
    )
    img.paste(defect_img, (left_x0 + 20, left_y0 + 55))
    
    # Overlay AI bounding box tracking over the spaghetti defect
    hud_x0 = left_x0 + 20 + 130
    hud_y0 = left_y0 + 55 + 80
    hud_x1 = left_x0 + 20 + 680
    hud_y1 = left_y0 + 55 + 380
    
    # Scanning laser line within bounding box
    laser_y = hud_y0 + int((hud_y1 - hud_y0) * ((t * 0.8) % 1.0))
    
    draw_defect_hud(draw, [hud_x0, hud_y0, hud_x1, hud_y1],
                    label="CRITICAL ANOMALY: FILAMENT SPAGHETTI",
                    confidence=0.926, alert_state=True, scan_line_y=laser_y)
                    
    # Bottom HUD status bar inside defect frame
    f_hud = get_font("mono", 14)
    draw.text((left_x0 + 30, left_y0 + 515), "MODEL: YOLOv8-CUSTOM  |  INFERENCE TIME: 38ms  |  STATUS: PRINT_INTERRUPTED", font=f_hud, fill=CLR_ALERT)
    draw.text((left_x0 + 30, left_y0 + 540), "SAFETY ACTION: EMERGENCY PAUSE G-CODE DISPATCHED", font=f_hud, fill=CLR_TEXT_MAIN)
    
    # Right Box: 3 Quantitative Impact Cards
    card_w = 840
    card_h = 175
    right_x0 = 980
    cards_data = [
        ("CATASTROPHIC SPOOL SCRAP", "100% Material Loss", "Spaghetti failures consume up to 300g of raw filament without human supervision.", CLR_ALERT),
        ("HOURS OF LOST DOWNTIME", "8+ Hours Wasted", "Overnight and batch production jobs fail silently, causing missed delivery deadlines.", CLR_CYAN),
        ("EXTRUDER & NOZZLE CLOGS", "Thermal Runaway Risk", "Unchecked plastic blobs encase the heater block, risking thermistor destruction.", CLR_VIOLET)
    ]
    
    for i, (title, highlight, desc, col) in enumerate(cards_data):
        y_pos = 210 + i * (card_h + 25)
        delay = i * 0.4
        card_p = ease_out_cubic(max(0.0, min(1.0, (t - delay) / 0.8)))
        offset_x = int(40 * (1.0 - card_p))
        
        c_rect = [right_x0 + offset_x, y_pos, right_x0 + offset_x + card_w, y_pos + card_h]
        draw_glass_card(draw, c_rect, border_color=(col[0]//2, col[1]//2, col[2]//2))
        
        # Color accent stripe
        draw.rectangle([right_x0 + offset_x + 2, y_pos + 15, right_x0 + offset_x + 8, y_pos + card_h - 15], fill=col)
        
        f_ctitle = get_font("mono_bold", 15)
        draw.text((right_x0 + offset_x + 30, y_pos + 20), title, font=f_ctitle, fill=col)
        
        f_high = get_font("bold", 28)
        draw.text((right_x0 + offset_x + 30, y_pos + 48), highlight, font=f_high, fill=CLR_TEXT_MAIN)
        
        f_cdesc = get_font("regular", 18)
        draw.text((right_x0 + offset_x + 30, y_pos + 92), desc, font=f_cdesc, fill=CLR_TEXT_MUTED)

    # Bottom summary ticker
    draw.line([(100, 830), (1820, 830)], fill=CLR_CARD_BORDER, width=1)
    f_bot = get_font("mono", 16)
    draw.text((100, 850), "[!] PROBLEM SUMMARY: Without continuous computer vision monitoring, additive manufacturing remains vulnerable to costly print failures.", font=f_bot, fill=CLR_TEXT_MUTED)
    
    return img

# ----------------------------------------------------------------------
# Scene 3: Introducing The Solution (14.0s – 22.0s)
# ----------------------------------------------------------------------
def render_scene_3(t, dur=8.0):
    p = t / dur
    img = create_tech_background(1920, 1080, time_sec=14.0 + t)
    draw = ImageDraw.Draw(img)
    
    # Header
    f_cat = get_font("mono_bold", 16)
    draw.text((100, 50), "SECTION 02 // INTELLIGENT PLATFORM ARCHITECTURE", font=f_cat, fill=CLR_CYAN)
    f_head = get_font("bold", 42)
    draw.text((100, 80), "Bringing AI-Powered Monitoring into the Printing Workflow", font=f_head, fill=CLR_TEXT_MAIN)
    f_sub = get_font("regular", 19)
    draw.text((100, 136), "PrintGuard AI integrates edge optical computer vision directly with industrial Spring Boot telemetry.", font=f_sub, fill=CLR_TEXT_MUTED)
    
    # Center Device Frame showing authentic UI Dashboard
    dash_img = render_ken_burns_image(
        "video-generator/assets/ui_dashboard.png", 1120, 650,
        progress=p, zoom_start=1.0, zoom_end=1.04, pan_y=0.02
    )
    
    # Frame container
    frame_x, frame_y = 100, 180
    frame_w, frame_h = 1140, 670
    draw.rectangle([frame_x - 5, frame_y - 5, frame_x + frame_w + 5, frame_y + frame_h + 5],
                   fill=(14, 19, 30), outline=CLR_CYAN_DIM, width=2)
    img.paste(dash_img, (frame_x + 10, frame_y + 10))
    
    # Corner HUD ticks on frame
    tick = 16
    draw.line([(frame_x - 8, frame_y), (frame_x - 8, frame_y + tick)], fill=CLR_CYAN, width=3)
    draw.line([(frame_x - 8, frame_y), (frame_x + tick, frame_y)], fill=CLR_CYAN, width=3)
    draw.line([(frame_x + frame_w + 8, frame_y), (frame_x + frame_w + 8, frame_y + tick)], fill=CLR_CYAN, width=3)
    draw.line([(frame_x + frame_w + 8, frame_y), (frame_x + frame_w - tick, frame_y)], fill=CLR_CYAN, width=3)
    
    # Right Side Feature Cards
    r_x0 = 1270
    r_w = 550
    features = [
        ("AUTONOMOUS COMPUTER VISION", "YOLOv8 Real-Time Inference",
         ["Continuous optical frame analysis detects spaghetti", "and delamination defects with 92%+ confidence."], CLR_CYAN),
        ("ENTERPRISE SPRING BACKEND", "Java 21 & WebSocket Stream",
         ["Stateless JWT security, JPA persistence, and", "real-time hardware state synchronization."], CLR_VIOLET),
        ("DIRECT HARDWARE BRIDGE", "Web Serial G-code Streaming",
         ["Direct browser-to-printer serial communication", "with zero cloud latency or middleware overhead."], CLR_SUCCESS)
    ]
    
    for i, (f_title, f_sub_h, f_lines, f_col) in enumerate(features):
        y_pos = 180 + i * 220
        c_p = ease_out_cubic(max(0.0, min(1.0, (t - i * 0.4) / 0.8)))
        off_x = int(35 * (1.0 - c_p))
        
        card_rect = [r_x0 + off_x, y_pos, r_x0 + off_x + r_w, y_pos + 195]
        draw_glass_card(draw, card_rect, title="", border_color=(f_col[0]//2, f_col[1]//2, f_col[2]//2))
        
        # Color tag dot
        draw.ellipse([r_x0 + off_x + 25, y_pos + 25, r_x0 + off_x + 35, y_pos + 35], fill=f_col)
        f_ftitle = get_font("mono_bold", 14)
        draw.text((r_x0 + off_x + 45, y_pos + 22), f_title, font=f_ftitle, fill=f_col)
        
        f_fsub = get_font("bold", 22)
        draw.text((r_x0 + off_x + 25, y_pos + 50), f_sub_h, font=f_fsub, fill=CLR_TEXT_MAIN)
        
        f_fdesc = get_font("regular", 16)
        for l_i, line in enumerate(f_lines):
            draw.text((r_x0 + off_x + 25, y_pos + 86 + l_i * 22), line, font=f_fdesc, fill=CLR_TEXT_MUTED)
        
        # Live status pill inside card
        draw.rounded_rectangle([r_x0 + off_x + 25, y_pos + 155, r_x0 + off_x + 190, y_pos + 180],
                               radius=6, fill=(10, 15, 25), outline=(f_col[0]//2, f_col[1]//2, f_col[2]//2), width=1)
        f_pill = get_font("mono", 12)
        draw.text((r_x0 + off_x + 35, y_pos + 160), "STATUS: VERIFIED", font=f_pill, fill=CLR_SUCCESS)
        
    # Bottom ticker
    f_bot = get_font("mono", 15)
    draw.text((100, 890), "OPERATIONAL PROFILE: FastAPI (:8001) <---> Spring Boot (:8080) <---> React Three.js Frontend (:3000)", font=f_bot, fill=CLR_CYAN_DIM)

    return img

# ----------------------------------------------------------------------
# Scene 4: AI Detection Pipeline (22.0s – 34.0s)
# ----------------------------------------------------------------------
def render_scene_4(t, dur=12.0):
    p = t / dur
    img = create_tech_background(1920, 1080, time_sec=22.0 + t)
    draw = ImageDraw.Draw(img)
    
    # Header
    f_cat = get_font("mono_bold", 16)
    draw.text((100, 45), "SECTION 03 // COMPUTER VISION INFERENCE PIPELINE", font=f_cat, fill=CLR_CYAN)
    f_head = get_font("bold", 42)
    draw.text((100, 75), "YOLOv8 Edge Detection & Auto-Pause Interlock", font=f_head, fill=CLR_TEXT_MAIN)
    f_sub = get_font("regular", 19)
    draw.text((100, 130), "From raw optical feed to deep convolutional tensor inference and immediate mechanical safety trigger.", font=f_sub, fill=CLR_TEXT_MUTED)
    
    # Upper Section: 5-Stage Animated Data Flow Architecture
    stages = [
        ("01. CAPTURE", "Camera Stream", "1080p Optical Input"),
        ("02. PREPROCESS", "OpenCV Engine", "Gaussian & Resize"),
        ("03. INFERENCE", "YOLOv8 Neural Net", "best.pt Model (0.35)"),
        ("04. CLASSIFY", "Defect Telemetry", "Spaghetti (92.6%)"),
        ("05. INTERLOCK", "Auto-Pause Safety", "M25 Emergency Pause")
    ]
    
    stage_w = 300
    stage_h = 110
    total_w = len(stages) * stage_w + (len(stages) - 1) * 55
    start_x = (1920 - total_w) // 2
    stage_y = 175
    
    centers = []
    for idx, (step_num, title, subtitle) in enumerate(stages):
        x = start_x + idx * (stage_w + 55)
        centers.append((x + stage_w // 2, stage_y + stage_h // 2))
        
        # Highlight active stage as time progresses
        active_idx = int((t / dur) * 5.0) % 5
        is_active = (idx == active_idx)
        
        border_col = CLR_CYAN if is_active else CLR_CARD_BORDER
        bg_col = (20, 35, 55, 230) if is_active else CLR_CARD_BG
        
        draw_glass_card(draw, [x, stage_y, x + stage_w, stage_y + stage_h],
                        title="", border_color=border_col, bg_color=bg_col, corner_radius=10)
                        
        if is_active:
            draw.line([(x, stage_y), (x + stage_w, stage_y)], fill=CLR_CYAN, width=3)
            
        f_num = get_font("mono_bold", 13)
        draw.text((x + 16, stage_y + 14), step_num, font=f_num, fill=CLR_CYAN if is_active else CLR_TEXT_MUTED)
        f_stitle = get_font("bold", 20)
        draw.text((x + 16, stage_y + 38), title, font=f_stitle, fill=CLR_TEXT_MAIN)
        f_ssub = get_font("regular", 15)
        draw.text((x + 16, stage_y + 70), subtitle, font=f_ssub, fill=CLR_TEXT_MUTED)

    # Animated Connection Wires between Stages
    for idx in range(len(stages) - 1):
        pt1 = (start_x + idx * (stage_w + 55) + stage_w, stage_y + stage_h // 2)
        pt2 = (start_x + (idx + 1) * (stage_w + 55), stage_y + stage_h // 2)
        wire_p = (t * 2.0 + idx * 0.25) % 1.0
        draw_data_wire(draw, pt1, pt2, progress=wire_p, pulse_color=CLR_CYAN)

    # Lower Section: Real UI Screen (Left) + Live Telemetry JSON Inspector (Right)
    low_y0 = 315
    low_h = 550
    
    # Left: Authentic AI Monitoring Page Screenshot
    mon_w = 950
    draw_glass_card(draw, [100, low_y0, 100 + mon_w, low_y0 + low_h],
                    title="LIVE HARDWARE CAMERA FEED // AI MONITORING INTERFACE")
    ui_mon = render_ken_burns_image(
        "video-generator/assets/ui_ai_monitoring.png", mon_w - 30, low_h - 60,
        progress=p, zoom_start=1.0, zoom_end=1.03, pan_y=0.01
    )
    img.paste(ui_mon, (115, low_y0 + 45))
    
    # Right: Real-Time JSON Telemetry & Interlock Monitor
    tele_x0 = 1080
    tele_w = 740
    draw_glass_card(draw, [tele_x0, low_y0, tele_x0 + tele_w, low_y0 + low_h],
                    title="TELEMETRY STREAM & SAFETY DISPATCH",
                    border_color=CLR_CYAN_DIM)
                    
    # Simulated Live Telemetry Code Display
    json_lines = [
        "// Telemetry Frame Analysis (WebSocket /ws/stream)",
        "{",
        '  "timestamp": "2026-10-24T18:45:12.842Z",',
        '  "frame_id": 4189,',
        '  "optical_input": {',
        '    "resolution": "1920x1080",',
        '    "fps": 30.0',
        '  },',
        '  "yolo_inference": {',
        '    "model": "best.pt (YOLOv8-Custom)",',
        '    "detected_class": "filament_spaghetti",',
        '    "confidence_score": 0.926,',
        '    "threshold": 0.350',
        '  },',
        '  "safety_interlock": {',
        '    "status": "CRITICAL_DEFECT_CONFIRMED",',
        '    "action": "AUTO_PAUSE_DISPATCHED",',
        '    "gcode_command": "M25 ; Pause SD Print",',
        '    "buzzer_alert": true',
        '  }',
        "}"
    ]
    f_code = get_font("mono", 15)
    f_code_bold = get_font("mono_bold", 15)
    
    for l_idx, line in enumerate(json_lines):
        line_y = low_y0 + 55 + l_idx * 23
        if "CRITICAL_DEFECT_CONFIRMED" in line or "AUTO_PAUSE_DISPATCHED" in line:
            draw.text((tele_x0 + 25, line_y), line, font=f_code_bold, fill=CLR_ALERT)
        elif "confidence_score" in line or "detected_class" in line:
            draw.text((tele_x0 + 25, line_y), line, font=f_code_bold, fill=CLR_CYAN)
        elif line.startswith("//"):
            draw.text((tele_x0 + 25, line_y), line, font=f_code, fill=CLR_TEXT_MUTED)
        else:
            draw.text((tele_x0 + 25, line_y), line, font=f_code, fill=CLR_TEXT_MAIN)
            
    # Live Status Badges on the bottom right
    badge_y = low_y0 + low_h - 45
    draw.rounded_rectangle([tele_x0 + 25, badge_y - 20, tele_x0 + 350, badge_y + 15],
                           radius=6, fill=(15, 30, 25), outline=CLR_SUCCESS, width=1)
    f_stat = get_font("mono_bold", 13)
    draw.text((tele_x0 + 40, badge_y - 12), "[OK] INTERLOCK ARMED & RESPONSIVE", font=f_stat, fill=CLR_SUCCESS)
    
    draw.rounded_rectangle([tele_x0 + 370, badge_y - 20, tele_x0 + 680, badge_y + 15],
                           radius=6, fill=(35, 15, 20), outline=CLR_ALERT, width=1)
    draw.text((tele_x0 + 385, badge_y - 12), "[!] DEFECT CONFIDENCE: 92.6%", font=f_stat, fill=CLR_ALERT)
    
    return img

# ----------------------------------------------------------------------
# Scene 5: 3D Printing & CAD Workflow (34.0s – 44.0s)
# ----------------------------------------------------------------------
def render_scene_5(t, dur=10.0):
    p = t / dur
    img = create_tech_background(1920, 1080, time_sec=34.0 + t)
    draw = ImageDraw.Draw(img)
    
    # Header
    f_cat = get_font("mono_bold", 16)
    draw.text((100, 45), "SECTION 04 // DIGITAL FABRICATION WORKFLOW", font=f_cat, fill=CLR_VIOLET)
    f_head = get_font("bold", 42)
    draw.text((100, 75), "From Generative Model to Headless Slicing & G-Code", font=f_head, fill=CLR_TEXT_MAIN)
    f_sub = get_font("regular", 19)
    draw.text((100, 130), "Automated pipeline converting 3D concepts into production toolpaths and hardware execution.", font=f_sub, fill=CLR_TEXT_MUTED)
    
    # 4 Vertical Stage Cards
    workflow_steps = [
        ("01. MODEL SYNTHESIS", "AI Diffusion & CAD", "Shap-E neural mesh generator & parametric chess generator.", "ui_generator.png", CLR_CYAN),
        ("02. MESH REPAIR", "Manifold Validation", "Watertight mesh healing, face normal fixes, and STL export.", None, CLR_VIOLET),
        ("03. HEADLESS SLICING", "PrusaSlicer CLI", "Integrated console slicer generating optimized G-code toolpaths.", None, CLR_CYAN),
        ("04. DIRECT HARDWARE", "Web Serial Link", "Browser Web Serial API streaming commands directly to printer.", "ui_printer_control.png", CLR_SUCCESS)
    ]
    
    card_w = 405
    card_h = 660
    card_y = 180
    gap = 25
    
    for i, (step_num, title, desc, asset_name, col) in enumerate(workflow_steps):
        x = 100 + i * (card_w + gap)
        # Staggered entrance
        c_p = ease_out_cubic(max(0.0, min(1.0, (t - i * 0.3) / 0.7)))
        off_y = int(40 * (1.0 - c_p))
        
        draw_glass_card(draw, [x, card_y + off_y, x + card_w, card_y + off_y + card_h],
                        title="", border_color=(col[0]//2, col[1]//2, col[2]//2))
                        
        # Step header
        f_num = get_font("mono_bold", 14)
        draw.text((x + 20, card_y + off_y + 20), step_num, font=f_num, fill=col)
        f_title = get_font("bold", 24)
        draw.text((x + 20, card_y + off_y + 45), title, font=f_title, fill=CLR_TEXT_MAIN)
        f_desc = get_font("regular", 16)
        draw.text((x + 20, card_y + off_y + 80), desc, font=f_desc, fill=CLR_TEXT_MUTED)
        
        # Center Visual Element
        vis_y0 = card_y + off_y + 140
        vis_h = 360
        vis_w = card_w - 40
        
        if asset_name:
            ui_thumb = render_ken_burns_image(
                f"video-generator/assets/{asset_name}", vis_w, vis_h,
                progress=p, zoom_start=1.0, zoom_end=1.04
            )
            img.paste(ui_thumb, (x + 20, vis_y0))
            draw.rectangle([x + 20, vis_y0, x + 20 + vis_w, vis_y0 + vis_h], outline=col, width=1)
        elif i == 1:
            # Manifold Repair Visual (3D Wireframe rotating with validation checks)
            draw.rounded_rectangle([x + 20, vis_y0, x + 20 + vis_w, vis_y0 + vis_h],
                                   radius=8, fill=(12, 16, 26), outline=col, width=1)
            # Render mini wireframe inside card
            _WIREFRAME.render(draw, center_x=x + 20 + vis_w // 2, center_y=vis_y0 + 130,
                              scale=130.0, yaw=t * 0.6, pitch=0.4, roll=0.1, cyan_color=CLR_VIOLET)
            f_check = get_font("mono_bold", 14)
            draw.text((x + 35, vis_y0 + 260), "[OK] WATERTIGHT: TRUE", font=f_check, fill=CLR_SUCCESS)
            draw.text((x + 35, vis_y0 + 285), "[OK] NON-MANIFOLD EDGES: 0", font=f_check, fill=CLR_SUCCESS)
            draw.text((x + 35, vis_y0 + 310), "[OK] FACES NORMALIZED", font=f_check, fill=CLR_SUCCESS)
        elif i == 2:
            # PrusaSlicer G-code Toolpath Simulation Visual
            draw.rounded_rectangle([x + 20, vis_y0, x + 20 + vis_w, vis_y0 + vis_h],
                                   radius=8, fill=(12, 16, 26), outline=col, width=1)
            # Animated G-code Toolpath Lines
            f_mono = get_font("mono", 13)
            draw.text((x + 35, vis_y0 + 20), "ENGINE: PrusaSlicer 2.9.6 CLI", font=f_mono, fill=CLR_CYAN)
            draw.text((x + 35, vis_y0 + 42), "PROFILE: Prusa MK3S+ (0.2mm)", font=f_mono, fill=CLR_TEXT_MUTED)
            
            # Toolpath lines simulation
            center_gx = x + 20 + vis_w // 2
            center_gy = vis_y0 + 160
            for r in range(20, 110, 15):
                angle_offset = t * 1.5 + r
                draw.arc([center_gx - r, center_gy - r, center_gx + r, center_gy + r],
                         start=int(angle_offset * 40) % 360,
                         end=(int(angle_offset * 40) + 240) % 360,
                         fill=CLR_CYAN, width=2)
                         
            # Animated Layer Counter
            cur_layer = int(120 + (t / dur) * 180)
            f_layer = get_font("mono_bold", 16)
            draw.text((x + 35, vis_y0 + 280), f"LAYER: {cur_layer} / 320", font=f_layer, fill=CLR_TEXT_MAIN)
            draw.text((x + 35, vis_y0 + 310), "EST. PRINT TIME: 1h 48m", font=f_mono, fill=CLR_TEXT_MUTED)
            
        # Card Bottom Tag
        tag_y = card_y + off_y + card_h - 45
        draw.rounded_rectangle([x + 20, tag_y - 12, x + card_w - 20, tag_y + 18],
                               radius=6, fill=(15, 20, 32), outline=(col[0]//2, col[1]//2, col[2]//2), width=1)
        f_tag = get_font("mono_bold", 12)
        draw.text((x + 32, tag_y - 4), f"STAGE 0{i+1} // VERIFIED FUNCTIONAL", font=f_tag, fill=col)

    # Connecting Flow Arrow along bottom
    draw.line([(100, 880), (1820, 880)], fill=CLR_CARD_BORDER, width=2)
    f_flow = get_font("mono_bold", 14)
    draw.text((100, 895), "SEAMLESS DIGITAL THREAD: 3D Generation --> Slicing --> Telemetry --> Physical Execution", font=f_flow, fill=CLR_CYAN)
    
    return img

# ----------------------------------------------------------------------
# Scene 6: Architecture & Tech Stack (44.0s – 55.0s)
# ----------------------------------------------------------------------
def render_scene_6(t, dur=11.0):
    p = t / dur
    img = create_tech_background(1920, 1080, time_sec=44.0 + t)
    draw = ImageDraw.Draw(img)
    
    # Header
    f_cat = get_font("mono_bold", 16)
    draw.text((100, 45), "SECTION 05 // VERIFIED PRODUCTION ARCHITECTURE", font=f_cat, fill=CLR_CYAN)
    f_head = get_font("bold", 42)
    draw.text((100, 75), "Engineered with Enterprise-Grade Robustness", font=f_head, fill=CLR_TEXT_MAIN)
    f_sub = get_font("regular", 19)
    draw.text((100, 130), "Decoupled microservices architecture ensuring high throughput, real-time safety, and persistence.", font=f_sub, fill=CLR_TEXT_MUTED)
    
    # 4 Architecture Cards (2x2 Grid)
    card_w = 830
    card_h = 320
    x_left = 100
    x_right = 990
    y_top = 180
    y_bot = 535
    
    arch_modules = [
        # Module 1: Java / Spring Boot (Top-Left)
        {
            "rect": [x_left, y_top, x_left + card_w, y_top + card_h],
            "title": "CORE BACKEND & ORCHESTRATION",
            "tech": "Java 21 & Spring Boot 3.3.0",
            "points": [
                "Spring Security with stateless JWT authentication & role-based RBAC",
                "Resilience4j Circuit Breaker guarding AI microservice communication",
                "Spring Data JPA with Flyway V1/V2 database schema migrations",
                "Full WebSocket telemetry dispatcher streaming live printer state"
            ],
            "badge": "98/98 INTEGRATION TESTS PASSED (100%)",
            "col": CLR_CYAN
        },
        # Module 2: Python / AI Service (Top-Right)
        {
            "rect": [x_right, y_top, x_right + card_w, y_top + card_h],
            "title": "COMPUTER VISION & GENERATIVE AI",
            "tech": "Python 3.11, FastAPI & YOLOv8",
            "points": [
                "YOLOv8 custom-trained neural net (best.pt) for spaghetti defect classification",
                "OpenCV optical frame preprocessor with adaptive lighting normalization",
                "Shap-E neural diffusion pipeline for text/image-to-3D model synthesis",
                "Parametric procedural CAD geometry engine for rapid component generation"
            ],
            "badge": "19/19 AI UNIT TESTS PASSED (100%)",
            "col": CLR_VIOLET
        },
        # Module 3: Frontend / UI (Bottom-Left)
        {
            "rect": [x_left, y_bot, x_left + card_w, y_bot + card_h],
            "title": "HIGH-PERFORMANCE CLIENT INTERFACE",
            "tech": "React 18, Vite & Three.js",
            "points": [
                "Interactive 3D model viewport with real-time orbit controls & STL rendering",
                "Direct hardware connection via browser Web Serial API (115200 baud)",
                "Dark glassmorphism design system tailored for industrial control rooms",
                "Production bundle validated with zero build warnings (Vite 5)"
            ],
            "badge": "AUTHENTIC UI SCREENS VERIFIED",
            "col": CLR_CYAN
        },
        # Module 4: Persistence & Tooling (Bottom-Right)
        {
            "rect": [x_right, y_bot, x_right + card_w, y_bot + card_h],
            "title": "PERSISTENCE & TOOLPATH PIPELINE",
            "tech": "PostgreSQL, Supabase & PrusaSlicer",
            "points": [
                "PostgreSQL / Supabase cloud persistence with Row Level Security (RLS)",
                "Complete audit history tracking all detected defects and resolution actions",
                "Headless PrusaSlicer 2.9 CLI integrated for autonomous G-code generation",
                "Local H2 database profile configured for rapid offline zero-dependency testing"
            ],
            "badge": "HEADLESS CLI INTEGRATED",
            "col": CLR_SUCCESS
        }
    ]
    
    for i, m in enumerate(arch_modules):
        # Staggered entrance
        c_p = ease_out_cubic(max(0.0, min(1.0, (t - i * 0.25) / 0.7)))
        c_rect = m["rect"]
        draw_glass_card(draw, c_rect, title="", border_color=(m["col"][0]//2, m["col"][1]//2, m["col"][2]//2))
        
        # Color accent stripe
        draw.rectangle([c_rect[0] + 2, c_rect[1] + 12, c_rect[0] + 7, c_rect[3] - 12], fill=m["col"])
        
        # Header titles
        f_mtitle = get_font("mono_bold", 13)
        draw.text((c_rect[0] + 25, c_rect[1] + 18), m["title"], font=f_mtitle, fill=m["col"])
        
        f_mtech = get_font("bold", 24)
        draw.text((c_rect[0] + 25, c_rect[1] + 42), m["tech"], font=f_mtech, fill=CLR_TEXT_MAIN)
        
        # Bullet points
        f_mpoint = get_font("regular", 16)
        for p_idx, pt in enumerate(m["points"]):
            pt_y = c_rect[1] + 85 + p_idx * 38
            draw.ellipse([c_rect[0] + 25, pt_y + 6, c_rect[0] + 31, pt_y + 12], fill=m["col"])
            draw.text((c_rect[0] + 42, pt_y), pt, font=f_mpoint, fill=CLR_TEXT_MUTED)
            
        # Verified Badge
        f_mbadge = get_font("mono_bold", 12)
        badge_rect = [c_rect[0] + 25, c_rect[3] - 38, c_rect[0] + 380, c_rect[3] - 14]
        draw.rounded_rectangle(badge_rect, radius=5, fill=(12, 22, 28),
                               outline=(m["col"][0]//2, m["col"][1]//2, m["col"][2]//2), width=1)
        draw.text((c_rect[0] + 38, c_rect[3] - 32), f"[VERIFIED] {m['badge']}", font=f_mbadge, fill=m["col"])

    # Bottom summary
    draw.line([(100, 890), (1820, 890)], fill=CLR_CARD_BORDER, width=1)
    f_bot = get_font("mono", 15)
    draw.text((100, 905), "SYSTEM TEST SUMMARY: 98 Java Tests PASS | 19 Python Tests PASS | Frontend Build PASS | Headless Slicer PASS", font=f_bot, fill=CLR_SUCCESS)
    
    return img

# ----------------------------------------------------------------------
# Scene 7: Outro & Call to Action (55.0s – 66.0s)
# ----------------------------------------------------------------------
def render_scene_7(t, dur=11.0):
    p = t / dur
    img = create_tech_background(1920, 1080, time_sec=55.0 + t)
    draw = ImageDraw.Draw(img)
    
    # Slowly rotating 3D wireframe centered
    yaw = t * 0.35
    pitch = 0.4 + 0.1 * math.sin(t * 0.25)
    roll = t * 0.1
    _WIREFRAME.render(draw, center_x=960, center_y=420, scale=310.0,
                      yaw=yaw, pitch=pitch, roll=roll, laser_progress=None)
    
    # 3 High-Impact Pillars
    f_pillar = get_font("mono_bold", 22)
    p_texts = [
        "ZERO WASTED SPOOLS",
        "AUTONOMOUS ERROR INTERLOCK",
        "INTELLIGENT 3D PRINTING"
    ]
    p_xs = [320, 960, 1600]
    for i in range(3):
        tw = draw.textlength(p_texts[i], font=f_pillar)
        draw.text((p_xs[i] - tw // 2, 140), p_texts[i], font=f_pillar, fill=CLR_CYAN)
        # Small glowing dot below
        draw.ellipse([p_xs[i] - 4, 180, p_xs[i] + 4, 188], fill=CLR_VIOLET)
        
    # Main Title Reveal
    f_title = get_font("bold", 78)
    title_text = "PRINTGUARD AI"
    tw = draw.textlength(title_text, font=f_title)
    tx = (1920 - tw) // 2
    ty = 630
    
    # Glow effect
    for ox, oy in [(-3, 0), (3, 0), (0, -3), (0, 3)]:
        draw.text((tx + ox, ty + oy), title_text, font=f_title, fill=CLR_CYAN + (90,))
    draw.text((tx, ty), title_text, font=f_title, fill=CLR_TEXT_MAIN)
    
    # Subtitle
    f_sub = get_font("regular", 28)
    sub_text = "Smarter 3D Printing. Intelligent Error Detection."
    sw = draw.textlength(sub_text, font=f_sub)
    draw.text(((1920 - sw) // 2, 730), sub_text, font=f_sub, fill=CLR_CYAN)
    
    # Developer / Project Pill
    pill_rect = [580, 800, 1340, 848]
    draw.rounded_rectangle(pill_rect, radius=8, fill=(18, 24, 38, 230), outline=CLR_CYAN_DIM, width=1)
    f_pill = get_font("mono_bold", 16)
    pill_text = "GITHUB: Warcraft433/AI_3D_Printer  |  SPRING BOOT 3.3  |  YOLOv8"
    pw = draw.textlength(pill_text, font=f_pill)
    draw.text(((1920 - pw) // 2, 814), pill_text, font=f_pill, fill=CLR_TEXT_MAIN)
    
    # Tech Stack Footer Badges
    f_badge = get_font("mono", 14)
    badges_str = "Java 21   •   Spring Boot 3.3   •   Python 3.11   •   YOLOv8   •   React 18   •   Three.js   •   PrusaSlicer"
    bw = draw.textlength(badges_str, font=f_badge)
    draw.text(((1920 - bw) // 2, 885), badges_str, font=f_badge, fill=CLR_CYAN_DIM)
        
    # Master Fade Out over last 2 seconds (t >= 9.0s out of 11.0s)
    if t >= 9.0:
        fade_alpha = int(255 * ((t - 9.0) / 2.0))
        black_overlay = Image.new("RGBA", (1920, 1080), (6, 8, 14, fade_alpha))
        img.alpha_composite(black_overlay)

    return img
