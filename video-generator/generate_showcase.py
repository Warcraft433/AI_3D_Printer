"""
Master Showcase Video Generator for PrintGuard AI
Renders a 66-second cinematic 1080p60/30 showcase video with:
- 7 narrative scenes
- 3D wireframe perspective projection
- Authentic UI captures and YOLO defect bounding boxes
- Synchronized multi-layered electronic soundtrack
- Encoded with FFmpeg libx264 / AAC
"""

import os
import sys
import time
import subprocess
import numpy as np
from PIL import Image

import imageio_ffmpeg
from audio_synth import generate_soundtrack
from scenes import (
    render_scene_1, render_scene_2, render_scene_3,
    render_scene_4, render_scene_5, render_scene_6, render_scene_7
)

FPS = 30
WIDTH = 1920
HEIGHT = 1080

# Scene configuration: (name, renderer_fn, duration_seconds)
SCENE_TIMELINE = [
    ("Scene 1 - Cinematic Opening", render_scene_1, 6.0),
    ("Scene 2 - The Problem (Failures & Waste)", render_scene_2, 8.0),
    ("Scene 3 - Introducing PrintGuard AI", render_scene_3, 8.0),
    ("Scene 4 - AI Detection Pipeline", render_scene_4, 12.0),
    ("Scene 5 - 3D Printing & CAD Workflow", render_scene_5, 10.0),
    ("Scene 6 - Enterprise Architecture & Tech Stack", render_scene_6, 11.0),
    ("Scene 7 - Outro & Project Showcase", render_scene_7, 11.0)
]

def render_showcase_video(output_mp4="video-generator/output/printguard_ai_showcase.mp4"):
    total_duration = sum(dur for _, _, dur in SCENE_TIMELINE)
    total_frames = int(total_duration * FPS)
    
    os.makedirs(os.path.dirname(output_mp4), exist_ok=True)
    temp_dir = os.path.join("video-generator", "temp")
    os.makedirs(temp_dir, exist_ok=True)
    
    print("=" * 70)
    print("PRINTGUARD AI // CINEMATIC SHOWCASE VIDEO GENERATOR")
    print(f"Target: {output_mp4}")
    print(f"Resolution: {WIDTH}x{HEIGHT} @ {FPS}fps | Total duration: {total_duration:.1f}s ({total_frames} frames)")
    print("=" * 70)
    
    # Step 1: Generate soundtrack
    soundtrack_wav = os.path.join(temp_dir, "soundtrack.wav")
    print("\n[1/3] Synthesizing synchronized electronic soundtrack & sound effects...")
    generate_soundtrack(soundtrack_wav, total_duration=total_duration, sample_rate=44100)
    
    # Step 2: Launch FFmpeg encoder process
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    print(f"\n[2/3] Launching FFmpeg encoder ({ffmpeg_exe})...")
    
    cmd = [
        ffmpeg_exe,
        "-y",
        "-f", "rawvideo",
        "-vcodec", "rawvideo",
        "-s", f"{WIDTH}x{HEIGHT}",
        "-pix_fmt", "rgb24",
        "-r", str(FPS),
        "-i", "-",                       # Frame pipe from stdin
        "-i", soundtrack_wav,           # Audio stream
        "-c:v", "libx264",
        "-preset", "fast",
        "-crf", "18",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "192k",
        "-shortest",
        output_mp4
    ]
    
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    
    # Step 3: Render and stream frames
    print(f"\n[3/3] Rendering {total_frames} cinematic frames...")
    start_time = time.time()
    
    current_scene_idx = 0
    scene_start_frame = 0
    
    for frame_idx in range(total_frames):
        # Determine current scene and relative time
        while current_scene_idx < len(SCENE_TIMELINE) - 1:
            scene_name, renderer, dur = SCENE_TIMELINE[current_scene_idx]
            scene_frames = int(dur * FPS)
            if frame_idx >= scene_start_frame + scene_frames:
                scene_start_frame += scene_frames
                current_scene_idx += 1
            else:
                break
                
        scene_name, renderer, dur = SCENE_TIMELINE[current_scene_idx]
        scene_frames = int(dur * FPS)
        t_scene = (frame_idx - scene_start_frame) / FPS
        
        # Render main frame
        frame_img = renderer(t_scene, dur=dur)
        
        # Subtle cross-dissolve during the last 0.3s of scenes 1 through 6
        transition_frames = 9 # ~0.3s
        frames_left_in_scene = (scene_start_frame + scene_frames) - frame_idx
        if frames_left_in_scene <= transition_frames and current_scene_idx < len(SCENE_TIMELINE) - 1:
            next_scene_renderer = SCENE_TIMELINE[current_scene_idx + 1][1]
            next_scene_dur = SCENE_TIMELINE[current_scene_idx + 1][2]
            alpha_next = (transition_frames - frames_left_in_scene) / float(transition_frames)
            next_frame_img = next_scene_renderer(0.0, dur=next_scene_dur)
            # Alpha blend
            frame_img = Image.blend(frame_img, next_frame_img, alpha_next)
            
        # Convert to RGB bytes and write to FFmpeg stdin
        rgb_data = frame_img.convert("RGB").tobytes()
        proc.stdin.write(rgb_data)
        
        # Progress logging
        if (frame_idx + 1) % 90 == 0 or frame_idx == total_frames - 1:
            elapsed = time.time() - start_time
            fps_render = (frame_idx + 1) / elapsed
            eta = (total_frames - (frame_idx + 1)) / max(fps_render, 0.01)
            pct = ((frame_idx + 1) / total_frames) * 100
            print(f"  Frame {frame_idx + 1:4d}/{total_frames} ({pct:5.1f}%) | {scene_name[:28]:28s} | {fps_render:4.1f} fps | ETA: {eta:4.1f}s")

    # Close pipe and wait for FFmpeg to finish
    proc.stdin.close()
    stderr_output = proc.stderr.read().decode("utf-8", errors="ignore")
    proc.wait()
    
    total_time = time.time() - start_time
    if proc.returncode == 0:
        file_size_mb = os.path.getsize(output_mp4) / (1024 * 1024)
        print("\n" + "=" * 70)
        print("SHOWCASE VIDEO GENERATED SUCCESSFULLY!")
        print(f"Output File: {output_mp4}")
        print(f"File Size:   {file_size_mb:.2f} MB")
        print(f"Render Time: {total_time:.1f} seconds")
        print("=" * 70)
        return output_mp4
    else:
        print("\n[ERROR] FFmpeg failed with return code:", proc.returncode)
        print(stderr_output)
        sys.exit(1)

if __name__ == "__main__":
    render_showcase_video()
