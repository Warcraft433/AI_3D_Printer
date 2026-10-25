import os
import imageio_ffmpeg
from PIL import Image, ImageFont, ImageDraw

font_candidates = [
    'C:/Windows/Fonts/segoeui.ttf',
    'C:/Windows/Fonts/segoeuib.ttf',
    'C:/Windows/Fonts/consola.ttf',
    'C:/Windows/Fonts/consolab.ttf',
    'C:/Windows/Fonts/bahnschrift.ttf',
    'C:/Windows/Fonts/arial.ttf',
    'C:/Windows/Fonts/arialbd.ttf'
]
available = [f for f in font_candidates if os.path.exists(f)]
print("Available fonts:", available)
print("FFMPEG binary:", imageio_ffmpeg.get_ffmpeg_exe())
