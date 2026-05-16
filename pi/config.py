# [STEP A] YOUR LAPTOP'S IP ADDRESS
BACKEND_URL = "http://192.168.31.61:8000" 
API_TOKEN = "your_api_token_here"

# [STEP B] OCTOPRINT SETTINGS
OCTOPRINT_URL = "http://10.122.104.112:5000"
OCTOPRINT_API_KEY = "h7iAOVn4FTh0DwsDUdp5mGyYn0-mRDVDlBd97S7HrbE"

# Camera settings
CAMERA_INDEX = 0  # 0 for default
USE_V4L2 = True   # Optimized for Pi

# YOLOv8 settings
MODEL_PATH = "best.onnx"  # ONNX is faster on Raspberry Pi
CONFIDENCE_THRESHOLD = 0.5
REPORT_BASE64 = True
SIMULATION_MODE = True
