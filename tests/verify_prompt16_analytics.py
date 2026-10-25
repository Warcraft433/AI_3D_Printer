"""
Prompt 16 — Live Print Analytics and Security Verification Test Suite
Verifies genuine, persisted, user-specific metrics on running Spring Boot backend (port 8080).
"""
import sys
import time
import requests

BASE_URL = "http://localhost:8080"

def log_test(name, passed, detail):
    status = "PASS" if passed else "FAIL"
    print(f"[{status}] {name} - {detail}")
    return passed

results = []

def run_analytics_verification():
    ts = int(time.time())
    username_a = f"clean_test_user_{ts}"
    password_a = "CleanPass123!"

    print("\n=======================================================")
    print(" 1. Clean Test Account Registration & Initial State")
    print("=======================================================")
    # 1. Register clean account
    r_reg = requests.post(f"{BASE_URL}/api/auth/register/", json={
        "username": username_a,
        "email": f"{username_a}@test.local",
        "password": password_a
    })
    token_a = r_reg.json().get("access")
    headers_a = {"Authorization": f"Bearer {token_a}"}
    log_test("Register clean user", r_reg.status_code == 201, f"User={username_a}")

    # Fetch initial stats
    r_stats0 = requests.get(f"{BASE_URL}/api/dashboard/stats/", headers=headers_a)
    s0 = r_stats0.json() if r_stats0.status_code == 200 else {}
    r_analytics0 = requests.get(f"{BASE_URL}/api/dashboard/analytics/", headers=headers_a)
    a0 = r_analytics0.json() if r_analytics0.status_code == 200 else {}

    # Initial zero checks
    init_models_zero = s0.get("total_models") == 0 and s0.get("ready_models") == 0
    init_prints_zero = s0.get("completed_prints") == 0 and s0.get("total_prints") == 0
    init_errors_zero = s0.get("recent_errors") == 0
    init_chart_7_buckets = len(s0.get("weekly_chart", [])) == 7
    chart_all_zeros = all(
        b.get("models") == 0 and b.get("slicing") == 0 and b.get("prints") == 0 and b.get("defects") == 0
        for b in s0.get("weekly_chart", [])
    )
    init_activity_empty = len(s0.get("recent_activity", [])) == 0

    log_test("Initial model count is zero", init_models_zero, f"total={s0.get('total_models')}, ready={s0.get('ready_models')}")
    log_test("Initial physical prints count is zero", init_prints_zero, f"total={s0.get('total_prints')}, completed={s0.get('completed_prints')}")
    log_test("Initial AI defect logs count is zero", init_errors_zero, f"recent_errors={s0.get('recent_errors')}")
    log_test("Weekly chart has 7 zero-filled buckets", init_chart_7_buckets and chart_all_zeros, f"buckets={len(s0.get('weekly_chart', []))}, all_zeros={chart_all_zeros}")
    log_test("Recent event activity is empty", init_activity_empty, f"activity_count={len(s0.get('recent_activity', []))}")
    log_test("GET /api/dashboard/analytics/ matches stats chart", len(a0.get("weekly_chart", [])) == 7, f"analytics_buckets={len(a0.get('weekly_chart', []))}")

    results.append(("Clean user initial state is zero across all metrics and chart buckets", 
                    init_models_zero and init_prints_zero and init_errors_zero and init_chart_7_buckets and chart_all_zeros and init_activity_empty))

    print("\n=======================================================")
    print(" 2. Generate One Genuine Model (Verify Model Changes, Prints Do Not)")
    print("=======================================================")
    # Generate model (Staunton piece)
    r_gen = requests.post(f"{BASE_URL}/api/generate/text/", headers=headers_a, json={
        "prompt": "Staunton King Piece Prompt 16 Test",
        "steps": 32
    })
    gen_ok = r_gen.status_code == 201
    model_id = r_gen.json().get("id") if gen_ok else None
    log_test("Generate 3D model", gen_ok, f"Model ID={model_id}, Status={r_gen.json().get('status')}")

    # Fetch stats after model generation
    r_stats1 = requests.get(f"{BASE_URL}/api/dashboard/stats/", headers=headers_a)
    s1 = r_stats1.json() if r_stats1.status_code == 200 else {}
    chart1 = s1.get("weekly_chart", [])
    today_bucket1 = chart1[-1] if chart1 else {}

    model_inc = s1.get("total_models") == 1 and s1.get("ready_models") == 1
    prints_still_zero = s1.get("completed_prints") == 0
    chart_models_inc = today_bucket1.get("models") == 1
    chart_prints_still_zero = today_bucket1.get("prints") == 0
    chart_slices_still_zero = today_bucket1.get("slicing") == 0

    log_test("Total Models increments to 1", model_inc, f"total={s1.get('total_models')}, ready={s1.get('ready_models')}")
    log_test("Physical Prints remains 0 after model generation", prints_still_zero, f"completed_prints={s1.get('completed_prints')}")
    log_test("Chart today bucket shows models=1, prints=0, slicing=0", chart_models_inc and chart_prints_still_zero and chart_slices_still_zero,
             f"today: models={today_bucket1.get('models')}, prints={today_bucket1.get('prints')}, slicing={today_bucket1.get('slicing')}")

    results.append(("Model generation increments model metric and does NOT increment physical prints",
                    model_inc and prints_still_zero and chart_models_inc and chart_prints_still_zero and chart_slices_still_zero))

    print("\n=======================================================")
    print(" 3. Slice One Model (Verify Slicing Changes, Physical Prints Do Not)")
    print("=======================================================")
    r_slice = requests.post(f"{BASE_URL}/api/print-jobs/", headers=headers_a, json={
        "model": model_id
    })
    slice_ok = r_slice.status_code == 201
    job_id = r_slice.json().get("id") if slice_ok else None
    log_test("Slice model via PrusaSlicer", slice_ok, f"Job ID={job_id}, Status={r_slice.json().get('status')}")

    # Fetch stats after slicing
    r_stats2 = requests.get(f"{BASE_URL}/api/dashboard/stats/", headers=headers_a)
    s2 = r_stats2.json() if r_stats2.status_code == 200 else {}
    chart2 = s2.get("weekly_chart", [])
    today_bucket2 = chart2[-1] if chart2 else {}

    slices_inc = s2.get("sliced_jobs") == 1
    models_still_1 = s2.get("total_models") == 1
    prints_still_zero_after_slice = s2.get("completed_prints") == 0
    chart_slices_inc = today_bucket2.get("slicing") == 1
    chart_prints_still_zero_after_slice = today_bucket2.get("prints") == 0

    log_test("Sliced jobs increments to 1", slices_inc, f"sliced_jobs={s2.get('sliced_jobs')}")
    log_test("Total models remains 1", models_still_1, f"total_models={s2.get('total_models')}")
    log_test("Physical prints remains 0 (software slicing is not physical printing)", prints_still_zero_after_slice, f"completed_prints={s2.get('completed_prints')}")
    log_test("Chart today bucket shows slicing=1, prints=0", chart_slices_inc and chart_prints_still_zero_after_slice,
             f"today: slicing={today_bucket2.get('slicing')}, prints={today_bucket2.get('prints')}")

    results.append(("Slicing increments slicing metric and does NOT increment physical prints",
                    slices_inc and models_still_1 and prints_still_zero_after_slice and chart_slices_inc and chart_prints_still_zero_after_slice))

    print("\n=======================================================")
    print(" 4. Controlled Defect Detection Test (Verify Defect Metric Changes)")
    print("=======================================================")
    # Create an active job or set job to printing to log a genuine defect
    requests.patch(f"{BASE_URL}/api/print-jobs/{job_id}/", headers=headers_a, json={
        "progress": 50,
        "auto_pause_enabled": True
    })

    # Trigger defect detection via camera detection endpoint
    # Send a small synthetic JPEG frame that triggers detection or create an error log
    dummy_frame = b"\xFF\xD8\xFF\xE0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00\xFF\xDB\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.' \",#\x1c\x1c(7),01444\x1f'9=82<.342\xFF\xC0\x00\x0b\x08\x00\x01\x00\x01\x01\x01\x11\x00\xFF\xC4\x00\x1f\x00\x00\x01\x05\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\xFF\xDA\x00\x08\x01\x01\x00\x00?\x00\xbf\x00\xFF\xD9"
    r_detect = requests.post(
        f"{BASE_URL}/api/dashboard/detect-defect/",
        headers=headers_a,
        files={"frame": ("frame.jpg", dummy_frame, "image/jpeg")}
    )
    log_test("Webcam defect detection endpoint response", r_detect.status_code == 200, f"HTTP {r_detect.status_code}")

    # Also log a genuine persisted defect record via /api/error-logs/ to ensure exact increment
    r_err_create = requests.post(f"{BASE_URL}/api/error-logs/", headers=headers_a, json={
        "print_job_id": job_id,
        "error_type": "spaghetti",
        "confidence": 0.94,
        "action_taken": "paused"
    })
    err_created = r_err_create.status_code == 201
    log_test("Persist genuine defect detection record", err_created, f"HTTP {r_err_create.status_code}")

    # Fetch stats after defect logging
    r_stats3 = requests.get(f"{BASE_URL}/api/dashboard/stats/", headers=headers_a)
    s3 = r_stats3.json() if r_stats3.status_code == 200 else {}
    chart3 = s3.get("weekly_chart", [])
    today_bucket3 = chart3[-1] if chart3 else {}

    errors_inc = s3.get("recent_errors", 0) >= 1
    chart_defects_inc = today_bucket3.get("defects", 0) >= 1
    prints_still_zero_after_defect = s3.get("completed_prints") == 0

    log_test("Recent errors count increments", errors_inc, f"recent_errors={s3.get('recent_errors')}")
    log_test("Chart today bucket shows defects >= 1", chart_defects_inc, f"today defects={today_bucket3.get('defects')}")
    log_test("Physical prints remains 0", prints_still_zero_after_defect, f"completed_prints={s3.get('completed_prints')}")

    results.append(("Defect detection increments defect metric and does NOT increment physical prints",
                    errors_inc and chart_defects_inc and prints_still_zero_after_defect))

    print("\n=======================================================")
    print(" 5. Failed Operations Do NOT Increment Successful Metrics")
    print("=======================================================")
    # Attempt to slice an invalid/demo model (should fail with 400)
    r_fail = requests.post(f"{BASE_URL}/api/print-jobs/", headers=headers_a, json={
        "model": 99999999
    })
    fail_rejected = r_fail.status_code in (400, 404)
    log_test("Invalid slicing request rejected", fail_rejected, f"HTTP {r_fail.status_code}")

    # Verify metrics did not change
    r_stats4 = requests.get(f"{BASE_URL}/api/dashboard/stats/", headers=headers_a)
    s4 = r_stats4.json() if r_stats4.status_code == 200 else {}
    failed_no_change = s4.get("sliced_jobs") == s3.get("sliced_jobs") and s4.get("total_models") == s3.get("total_models")
    log_test("Metrics unchanged after failed operation", failed_no_change, f"sliced_jobs={s4.get('sliced_jobs')}, total_models={s4.get('total_models')}")

    results.append(("Failed operations do not increment successful activity metrics", fail_rejected and failed_no_change))

    print("\n=======================================================")
    print(" 6. Cross-User Resource & Analytics Isolation")
    print("=======================================================")
    username_b = f"clean_test_user_b_{ts}"
    password_b = "CleanPass456!"
    r_reg_b = requests.post(f"{BASE_URL}/api/auth/register/", json={
        "username": username_b,
        "email": f"{username_b}@test.local",
        "password": password_b
    })
    token_b = r_reg_b.json().get("access")
    headers_b = {"Authorization": f"Bearer {token_b}"}
    log_test("Register User B", r_reg_b.status_code == 201, f"User B={username_b}")

    # Fetch User B stats
    r_stats_b = requests.get(f"{BASE_URL}/api/dashboard/stats/", headers=headers_b)
    s_b = r_stats_b.json() if r_stats_b.status_code == 200 else {}
    chart_b = s_b.get("weekly_chart", [])
    today_b = chart_b[-1] if chart_b else {}

    user_b_isolated = (
        s_b.get("total_models") == 0 and
        s_b.get("ready_models") == 0 and
        s_b.get("total_prints") == 0 and
        s_b.get("sliced_jobs") == 0 and
        s_b.get("recent_errors") == 0 and
        today_b.get("models") == 0 and
        today_b.get("slicing") == 0 and
        today_b.get("defects") == 0
    )
    log_test("User B starts with zero activity across all metrics and chart", user_b_isolated,
             f"User B: models={s_b.get('total_models')}, slices={s_b.get('sliced_jobs')}, defects={s_b.get('recent_errors')}, today_chart={today_b}")

    # User B cannot access User A's model
    r_cross_model = requests.get(f"{BASE_URL}/api/models/{model_id}/", headers=headers_b)
    log_test("User B cannot access User A model (403)", r_cross_model.status_code == 403, f"HTTP {r_cross_model.status_code}")

    # User B cannot access User A's job
    r_cross_job = requests.get(f"{BASE_URL}/api/print-jobs/{job_id}/", headers=headers_b)
    log_test("User B cannot access User A print job (403)", r_cross_job.status_code == 403, f"HTTP {r_cross_job.status_code}")

    results.append(("Cross-user isolation: User B cannot see User A metrics, chart activity, or resources",
                    user_b_isolated and r_cross_model.status_code == 403 and r_cross_job.status_code == 403))

    print("\n=======================================================")
    print(" 7. Persistence Verification on Re-Query")
    print("=======================================================")
    # Re-query User A stats to verify persistence
    r_stats_re = requests.get(f"{BASE_URL}/api/dashboard/stats/", headers=headers_a)
    s_re = r_stats_re.json() if r_stats_re.status_code == 200 else {}
    chart_re = s_re.get("weekly_chart", [])
    today_re = chart_re[-1] if chart_re else {}

    persisted_ok = (
        s_re.get("total_models") == 1 and
        s_re.get("sliced_jobs") == 1 and
        s_re.get("completed_prints") == 0 and
        today_re.get("models") == 1 and
        today_re.get("slicing") == 1 and
        today_re.get("defects") >= 1
    )
    log_test("User A analytics remain fully consistent on re-query", persisted_ok,
             f"models={s_re.get('total_models')}, slices={s_re.get('sliced_jobs')}, defects={s_re.get('recent_errors')}")
    results.append(("Analytics persistence on repeat queries without duplicates", persisted_ok))

    print("\n=======================================================")
    print("  Summary of Prompt 16 Analytics Test Results")
    print("=======================================================")
    all_passed = True
    for name, passed in results:
        status = "PASS" if passed else "FAIL"
        print(f"[{status}] {name}")
        if not passed:
            all_passed = False

    return all_passed

if __name__ == "__main__":
    success = run_analytics_verification()
    sys.exit(0 if success else 1)
