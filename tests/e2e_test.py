import time
import requests
import json
import websocket
import sys

BASE_URL = "http://localhost:8000/api"
WS_BASE = "ws://localhost:8000/ws"

session = requests.Session()
token = None

try:
    # -------------------------------------------------------------------
    # Step 1: Authentication Test
    # -------------------------------------------------------------------
    print("🔹 Step 1: Authentication Test")
    try:
        session.post(f"{BASE_URL}/auth/register/", json={
            "username": "e2e_user", "password": "Password123!", "email": "e2e@test.com"
        })
    except:
        pass # Might exist
        
    resp = session.post(f"{BASE_URL}/auth/login/", json={
        "username": "e2e_user", "password": "Password123!"
    })
    
    if resp.status_code != 200:
        print(f"❌ Auth error: {resp.text}")
        sys.exit(1)
        
    token = resp.json()['access']
    session.headers.update({"Authorization": f"Bearer {token}"})
    print("✅ JWT Authentication successful")
    
    # -------------------------------------------------------------------
    # Step 2: Model Generation Test
    # -------------------------------------------------------------------
    prompts = [
        "Low poly chess knight piece",
        "Small gear with 12 teeth mechanical part",
        "Minimalist phone stand with triangular base"
    ]
    models = []
    
    print("\n🔹 Step 2: Model Generation Test")
    for i, prompt in enumerate(prompts):
        print(f"\nGenerating [Model {i+1}/3]: '{prompt}'")
        resp = session.post(f"{BASE_URL}/generate/text/", json={"prompt": prompt, "steps": 32})
        
        if resp.status_code not in [201, 202]:
            print(f"❌ Failed to start generation (HTTP {resp.status_code}): {resp.text}")
            continue
            
        model_id = resp.json()['id']
        models.append(model_id)

        ws_url = f"{WS_BASE}/generation/{model_id}/"
        ws = websocket.create_connection(ws_url)
        
        if i == 1:
            print("   [Test] Simulating browser refresh (disconnecting & reconnecting WS)...")
            ws.close()
            time.sleep(1.5)
            ws = websocket.create_connection(ws_url)

        states_seen = set()
        while True:
            try:
                msg = json.loads(ws.recv())
                status = msg.get('status')
                if status not in states_seen:
                    print(f"   [WS] -> {status}")
                    states_seen.add(status)
                    
                if status == 'ready':
                    file_url = msg.get('file_url')
                    print(f"   ✅ Generation complete.")
                    
                    # Verify database
                    m_resp = session.get(f"{BASE_URL}/models/{model_id}/").json()
                    assert m_resp['is_repaired'] == True, "Model is_repaired flag not True"
                    assert '.obj' in m_resp['file_path'], "File path missing .obj"
                    print(f"   ✅ DB Verified: path={m_resp['file_path']}, repaired={m_resp['is_repaired']}")
                    break
                    
                if status == 'failed':
                    print(f"   ❌ Generation failed: {msg.get('error_detail')}")
                    break
            except Exception as e:
                print(f"   ❌ WS Exception: {e}")
                break
        ws.close()

    # -------------------------------------------------------------------
    # Step 3: Slicing Test
    # -------------------------------------------------------------------
    print("\n🔹 Step 3: Slicing Test")
    print_jobs = []
    
    for i, model_id in enumerate(models):
        print(f"\nSlicing Model {model_id}...")
        resp = session.post(f"{BASE_URL}/print-jobs/", json={"model": model_id})
        if resp.status_code not in [201, 202]:
            print(f"❌ Failed to start slicing (HTTP {resp.status_code}): {resp.text}")
            continue
        job = resp.json()
        job_id = job['id']
        print_jobs.append(job_id)
        
        ws_url = f"{WS_BASE}/print-jobs/{job_id}/"
        ws = websocket.create_connection(ws_url)
            
        states_seen = set()
        while True:
            try:
                msg = json.loads(ws.recv())
                status = msg.get('status')
                if status not in states_seen:
                    print(f"   [WS] -> {status}")
                    states_seen.add(status)
                    
                if status == 'ready_to_print':
                    print(f"   ✅ Slicing complete.")
                    
                    # Verify DB
                    j_resp = session.get(f"{BASE_URL}/print-jobs/{job_id}/").json()
                    assert '.gcode' in j_resp['gcode_path'], "gcode_path missing .gcode"
                    print(f"   ✅ DB Verified: gcode_path={j_resp['gcode_path']}")
                    break
                    
                if status in ['failed', 'completed', 'cancelled']:
                    print(f"   ❌ Job unexpectedly entered {status}!")
                    break
            except Exception as e:
                print(f"   ❌ WS Exception: {e}")
                break
        ws.close()

    # -------------------------------------------------------------------
    # Step 4: Printer Integration Test
    # -------------------------------------------------------------------
    print("\n🔹 Step 4: Printer API Validation")
    for job_id in print_jobs:
        print(f"\nStarting Print for Job {job_id}...")
        resp = session.post(f"{BASE_URL}/print-jobs/{job_id}/start-print/")
        
        ws_url = f"{WS_BASE}/print-jobs/{job_id}/"
        ws = websocket.create_connection(ws_url)
        ws.settimeout(60.0)
        
        states_seen = set()
        started_printing = False
        last_prog_printed = -1
        
        while True:
            try:
                msg = json.loads(ws.recv())
                status = msg.get('status')
                if status not in states_seen:
                    print(f"   [WS] -> {status}")
                    states_seen.add(status)
                
                if status == 'printing':
                    if not started_printing:
                        print("   🖨️ OctoPrint simulation running. Tracking telemetry...")
                        started_printing = True
                        
                    prog = msg.get('progress')
                    if prog is not None:
                        prog_int = int(prog)
                        # Print every 25% to avoid spam
                        if prog_int % 25 == 0 and prog_int != last_prog_printed:
                            temps = msg.get('temperatures', {})
                            bed = temps.get('bed', {}).get('actual', 0)
                            tool = temps.get('tool0', {}).get('actual', 0)
                            print(f"       -> [Telemetry] Progress: {prog_int}%, Bed: {bed}C, Tool: {tool}C")
                            last_prog_printed = prog_int
                        
                if status == 'completed':
                    print(f"   ✅ Print completed successfully!")
                    break
                if status == 'failed':
                    print(f"   ❌ Print failed!")
                    break
            except websocket.WebSocketTimeoutException:
                print("   ❌ Timeout waiting for print.")
                break
            except Exception as e:
                print(f"   ❌ WS Exception: {e}")
                break
        ws.close()

    print("\n🎉 End-to-End Validation Script Completed Automatically!\n")
    
except Exception as e:
    print(f"\n❌ FATAL SCRIPT EXCEPTION: {e}")
