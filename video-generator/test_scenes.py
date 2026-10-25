"""
Test script to render preview frames from each scene and verify visual quality.
"""

import os
from scenes import (
    render_scene_1, render_scene_2, render_scene_3,
    render_scene_4, render_scene_5, render_scene_6, render_scene_7
)

os.makedirs("video-generator/temp", exist_ok=True)

test_scenes = [
    ("preview_scene_1.png", render_scene_1, 3.5),
    ("preview_scene_2.png", render_scene_2, 4.0),
    ("preview_scene_3.png", render_scene_3, 4.0),
    ("preview_scene_4.png", render_scene_4, 6.0),
    ("preview_scene_5.png", render_scene_5, 5.0),
    ("preview_scene_6.png", render_scene_6, 5.5),
    ("preview_scene_7.png", render_scene_7, 5.0),
]

for filename, renderer, t in test_scenes:
    out_path = os.path.join("video-generator", "temp", filename)
    img = renderer(t)
    # Convert RGBA to RGB for saving
    rgb = img.convert("RGB")
    rgb.save(out_path, quality=95)
    print(f"Rendered {filename} at t={t:.1f}s -> {out_path}")

print("All 7 scene test previews rendered successfully!")
