"""
Verification script for Prompt 11: Real 3D Output and Genuine Slicing
"""
import requests
import json
import os
import sys

BASE_URL = 'http://localhost:8080'

print('=== 1. GENERATING CHESS KING ===')
king_resp = requests.post(f'{BASE_URL}/api/generate/text/', json={
    'prompt': 'Staunton Chess King with cross finial and pedestal base',
    'steps': 32
})
print('King status code:', king_resp.status_code)
king_data = king_resp.json()
print('King response:', json.dumps(king_data, indent=2))

print('\n=== 2. GENERATING CHESS KNIGHT ===')
knight_resp = requests.post(f'{BASE_URL}/api/generate/text/', json={
    'prompt': 'Staunton Chess Knight with horse head profile',
    'steps': 32
})
print('Knight status code:', knight_resp.status_code)
knight_data = knight_resp.json()
print('Knight response:', json.dumps(knight_data, indent=2))

print('\n=== 3. GEOMETRY VALIDATION ===')
king_file = os.path.join(r'C:\Projects01\AI_3D_Printer\backend\media', king_data['file_path'])
knight_file = os.path.join(r'C:\Projects01\AI_3D_Printer\backend\media', knight_data['file_path'])

king_size = os.path.getsize(king_file)
knight_size = os.path.getsize(knight_file)

with open(king_file) as f:
    king_lines = f.readlines()
with open(knight_file) as f:
    knight_lines = f.readlines()

king_verts = sum(1 for l in king_lines if l.startswith('v '))
king_faces = sum(1 for l in king_lines if l.startswith('f '))
knight_verts = sum(1 for l in knight_lines if l.startswith('v '))
knight_faces = sum(1 for l in knight_lines if l.startswith('f '))

print(f'King: size={king_size} bytes, verts={king_verts}, faces={king_faces}')
print(f'Knight: size={knight_size} bytes, verts={knight_verts}, faces={knight_faces}')
print(f'Distinct Geometry: Vertices different? {king_verts != knight_verts}, Faces different? {king_faces != knight_faces}, Sizes different? {king_size != knight_size}')

print('\n=== 4. SLICING CHESS KING ===')
king_job_resp = requests.post(f'{BASE_URL}/api/print-jobs/', json={'model': king_data['id']})
print('King job status code:', king_job_resp.status_code)
king_job = king_job_resp.json()
print('King job:', json.dumps(king_job, indent=2))

print('\n=== 5. SLICING CHESS KNIGHT ===')
knight_job_resp = requests.post(f'{BASE_URL}/api/print-jobs/', json={'model': knight_data['id']})
print('Knight job status code:', knight_job_resp.status_code)
knight_job = knight_job_resp.json()
print('Knight job:', json.dumps(knight_job, indent=2))

print('\n=== 6. G-CODE DOWNLOAD & VALIDATION ===')
gcode_king_resp = requests.get(f"{BASE_URL}/api/print-jobs/{king_job['id']}/download-gcode/")
print('King G-code HTTP status:', gcode_king_resp.status_code, 'size:', len(gcode_king_resp.content), 'bytes')
print('King G-code sample header:\n', gcode_king_resp.text[:400])

gcode_knight_resp = requests.get(f"{BASE_URL}/api/print-jobs/{knight_job['id']}/download-gcode/")
print('Knight G-code HTTP status:', gcode_knight_resp.status_code, 'size:', len(gcode_knight_resp.content), 'bytes')
print('Knight G-code sample header:\n', gcode_knight_resp.text[:400])

print('\n=== 7. DEMO MESH REJECTION TEST ===')
demo_job_resp = requests.post(f'{BASE_URL}/api/print-jobs/', json={'model': 9})
print('Demo print job status code:', demo_job_resp.status_code)
print('Demo print job response:', demo_job_resp.text)
