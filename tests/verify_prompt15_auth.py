"""
Prompt 15 — End-to-End Authentication and Security Verification Script
Tests actual running Spring Boot API on port 8080 against live Supabase PostgreSQL.
"""
import sys
import json
import time
import requests

BASE_URL = "http://localhost:8080"

def log_test(name, passed, detail):
    status = "PASS" if passed else "FAIL"
    print(f"[{status}] {name} - {detail}")
    return passed

results = []

def test_unauthenticated_endpoints():
    print("\n--- 1. Testing Unauthenticated Access Restrictions ---")
    endpoints = [
        ("GET", "/api/models/"),
        ("POST", "/api/generate/text/", {"prompt": "Test"}),
        ("GET", "/api/print-jobs/"),
        ("GET", "/api/dashboard/stats/"),
        ("GET", "/api/error-logs/"),
        ("GET", "/api/auth/me/"),
    ]
    all_passed = True
    for method, path, *body in endpoints:
        data = body[0] if body else None
        if method == "GET":
            r = requests.get(f"{BASE_URL}{path}")
        else:
            r = requests.post(f"{BASE_URL}{path}", json=data)
        
        ok = r.status_code == 401
        log_test(f"Unauthenticated {method} {path}", ok, f"HTTP {r.status_code} (expected 401)")
        if not ok:
            all_passed = False
    results.append(("Unauthenticated API requests return 401", all_passed))

def test_registration_and_login():
    print("\n--- 2. Testing Registration, Login, and Password Handling ---")
    ts = int(time.time())
    user_a = f"test_alpha_{ts}"
    pass_a = "AlphaPass123!"
    
    # Register
    r_reg = requests.post(f"{BASE_URL}/api/auth/register/", json={
        "username": user_a,
        "email": f"{user_a}@test.local",
        "password": pass_a
    })
    reg_ok = r_reg.status_code == 201 and "access" in r_reg.json()
    no_pass_leak = "password" not in r_reg.text and "pbkdf2" not in r_reg.text
    log_test("Register new account", reg_ok, f"HTTP {r_reg.status_code}, received token")
    log_test("Password not exposed in registration response", no_pass_leak, "No password or hash leaked in body")
    
    # Duplicate registration
    r_dup = requests.post(f"{BASE_URL}/api/auth/register/", json={
        "username": user_a,
        "email": f"dup_{user_a}@test.local",
        "password": pass_a
    })
    dup_ok = r_dup.status_code == 409
    log_test("Duplicate registration returns 409 Conflict", dup_ok, f"HTTP {r_dup.status_code} (expected 409)")
    
    # Incorrect password
    r_bad = requests.post(f"{BASE_URL}/api/auth/login/", json={
        "username": user_a,
        "password": "WrongPassword999!"
    })
    bad_ok = r_bad.status_code == 401
    log_test("Login with incorrect password returns 401", bad_ok, f"HTTP {r_bad.status_code} (expected 401)")
    
    # Nonexistent user
    r_nosuch = requests.post(f"{BASE_URL}/api/auth/login/", json={
        "username": f"nonexistent_{ts}",
        "password": pass_a
    })
    nosuch_ok = r_nosuch.status_code == 401
    log_test("Login with nonexistent username returns 401", nosuch_ok, f"HTTP {r_nosuch.status_code} (expected 401)")

    # Valid login
    r_login = requests.post(f"{BASE_URL}/api/auth/login/", json={
        "username": user_a,
        "password": pass_a
    })
    login_ok = r_login.status_code == 200 and "access" in r_login.json()
    token_a = r_login.json().get("access") if login_ok else None
    log_test("Login with correct credentials returns 200 and JWT", login_ok, f"HTTP {r_login.status_code}")
    
    # Verify /api/auth/me/
    headers_a = {"Authorization": f"Bearer {token_a}"}
    r_me = requests.get(f"{BASE_URL}/api/auth/me/", headers=headers_a)
    me_ok = r_me.status_code == 200 and r_me.json().get("username") == user_a
    me_no_pass = "password" not in r_me.text and "pbkdf2" not in r_me.text
    log_test("Access /api/auth/me with valid token", me_ok, f"HTTP {r_me.status_code}, user={r_me.json().get('username')}")
    log_test("Password not exposed in /api/auth/me", me_no_pass, "Sanitized profile returned")

    results.append(("Registration & Login flow", reg_ok and dup_ok and bad_ok and login_ok and me_ok))
    results.append(("Passwords never returned by APIs", no_pass_leak and me_no_pass))

    return user_a, pass_a, token_a, headers_a

def test_cross_user_isolation(user_a, token_a, headers_a):
    print("\n--- 3. Testing Cross-User Resource Isolation ---")
    ts = int(time.time())
    user_b = f"test_beta_{ts}"
    pass_b = "BetaPass123!"
    
    # Register User B
    r_reg_b = requests.post(f"{BASE_URL}/api/auth/register/", json={
        "username": user_b,
        "email": f"{user_b}@test.local",
        "password": pass_b
    })
    token_b = r_reg_b.json().get("access")
    headers_b = {"Authorization": f"Bearer {token_b}"}
    log_test("Register User B", r_reg_b.status_code == 201, f"User B={user_b}")

    # User A creates a model (using demo fallback prompt to avoid heavy slicing)
    r_gen = requests.post(f"{BASE_URL}/api/generate/text/", headers=headers_a, json={
        "prompt": "User A Private Test Cube",
        "steps": 32,
        "force_demo": True
    })
    gen_ok = r_gen.status_code == 201
    model_a_id = r_gen.json().get("id") if gen_ok else None
    log_test("User A creates private model", gen_ok, f"Model ID={model_a_id}")

    # User A views model -> 200
    r_view_a = requests.get(f"{BASE_URL}/api/models/{model_a_id}/", headers=headers_a)
    log_test("User A can view own model", r_view_a.status_code == 200, f"HTTP {r_view_a.status_code}")

    # User B attempts to view User A's model -> 403
    r_view_b = requests.get(f"{BASE_URL}/api/models/{model_a_id}/", headers=headers_b)
    view_b_ok = r_view_b.status_code == 403
    log_test("User B cannot view User A's model", view_b_ok, f"HTTP {r_view_b.status_code} (expected 403)")

    # User B attempts to delete User A's model -> 403
    r_del_b = requests.delete(f"{BASE_URL}/api/models/{model_a_id}/", headers=headers_b)
    del_b_ok = r_del_b.status_code == 403
    log_test("User B cannot delete User A's model", del_b_ok, f"HTTP {r_del_b.status_code} (expected 403)")

    # User B lists models -> User A's model must not be present
    r_list_b = requests.get(f"{BASE_URL}/api/models/", headers=headers_b)
    b_models = [m.get("id") for m in r_list_b.json()] if r_list_b.status_code == 200 else []
    list_isolated = model_a_id not in b_models
    log_test("User B model list excludes User A's model", list_isolated, f"Found {len(b_models)} models in User B list")

    # User B creates a print job for User A's model -> 403
    r_job_cross = requests.post(f"{BASE_URL}/api/print-jobs/", headers=headers_b, json={
        "model": model_a_id
    })
    job_cross_ok = r_job_cross.status_code == 403
    log_test("User B cannot create print job for User A's model", job_cross_ok, f"HTTP {r_job_cross.status_code} (expected 403)")

    # User A deletes own model -> 204
    r_del_a = requests.delete(f"{BASE_URL}/api/models/{model_a_id}/", headers=headers_a)
    del_a_ok = r_del_a.status_code == 204
    log_test("User A can delete own model", del_a_ok, f"HTTP {r_del_a.status_code}")

    results.append(("Cross-user model and job isolation", view_b_ok and del_b_ok and list_isolated and job_cross_ok))

def test_invalid_and_expired_tokens():
    print("\n--- 4. Testing Invalid and Malformed Token Handling ---")
    # Malformed token
    r_malformed = requests.get(f"{BASE_URL}/api/models/", headers={"Authorization": "Bearer not.a.valid.jwt"})
    malformed_ok = r_malformed.status_code == 401
    log_test("Malformed token rejected with 401", malformed_ok, f"HTTP {r_malformed.status_code}")

    # Invalid scheme
    r_scheme = requests.get(f"{BASE_URL}/api/models/", headers={"Authorization": "Token 123456"})
    scheme_ok = r_scheme.status_code == 401
    log_test("Non-Bearer authorization scheme rejected with 401", scheme_ok, f"HTTP {r_scheme.status_code}")

    # Random signature
    header_b64 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
    payload_b64 = "eyJzdWIiOiJhZG1pbiIsImV4cCI6OTk5OTk5OTk5OX0"
    tampered_token = f"{header_b64}.{payload_b64}.invalidSignatureHere123"
    r_tampered = requests.get(f"{BASE_URL}/api/models/", headers={"Authorization": f"Bearer {tampered_token}"})
    tampered_ok = r_tampered.status_code == 401
    log_test("Tampered token rejected with 401", tampered_ok, f"HTTP {r_tampered.status_code}")

    results.append(("Invalid and malformed tokens rejected with 401", malformed_ok and scheme_ok and tampered_ok))

if __name__ == "__main__":
    print("==================================================")
    print("  Prompt 15 Live Authentication Test Suite")
    print("==================================================")
    test_unauthenticated_endpoints()
    user_a, pass_a, token_a, headers_a = test_registration_and_login()
    test_cross_user_isolation(user_a, token_a, headers_a)
    test_invalid_and_expired_tokens()

    print("\n==================================================")
    print("  Summary of Results")
    print("==================================================")
    all_ok = True
    for name, ok in results:
        status = "PASS" if ok else "FAIL"
        print(f"[{status}] {name}")
        if not ok:
            all_ok = False
    
    sys.exit(0 if all_ok else 1)
