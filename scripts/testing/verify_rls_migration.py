#!/usr/bin/env python3
"""
PrintGuard AI — Comprehensive RLS & Least-Privilege Verification Script
Audits the 5 target tables and verifies:
1. Row Level Security (RLS) enabled status (pg_class)
2. Active policies in pg_policies
3. Role grants in information_schema.role_table_grants
4. Data preservation (active row counts)
5. Live Access Behavior Simulation (verifying unprivileged role 'anon' is blocked and 'postgres'/'service_role' retain access)

Never exposes passwords or sensitive credentials.
"""

import os
import re
import sys
import psycopg2

def load_env():
    for path in [".env", os.path.join(os.path.dirname(__file__), "..", ".env"), "c:\\Projects01\\AI_3D_Printer\\.env"]:
        if os.path.isfile(path):
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    if "=" in line:
                        k, v = line.split("=", 1)
                        k = k.strip()
                        v = v.strip().strip("'\"")
                        if k and v and k not in os.environ:
                            os.environ[k] = v
            break

def get_connection():
    load_env()
    jdbc_url = os.environ.get("SPRING_DATASOURCE_URL", "")
    user = os.environ.get("SPRING_DATASOURCE_USERNAME", "")
    pwd = os.environ.get("SPRING_DATASOURCE_PASSWORD", "")

    if not jdbc_url:
        jdbc_url = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require"
    if not user:
        user = "postgres.wlpskpsysnamtmzilpft"

    m = re.match(r"jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)(?:\?(.*))?", jdbc_url)
    if not m:
        raise ValueError("Invalid JDBC URL format")

    host = m.group(1)
    port = int(m.group(2) or 5432)
    dbname = m.group(3)
    query_params = m.group(4) or ""
    sslmode = "require" if "sslmode=require" in query_params else "prefer"

    return psycopg2.connect(
        host=host,
        port=port,
        dbname=dbname,
        user=user,
        password=pwd,
        sslmode=sslmode,
        connect_timeout=15
    )

def main():
    print("=" * 75)
    print("PrintGuard AI — Database RLS Status, Policies & Access Behavior Audit")
    print("=" * 75)

    tables = [
        "auth_user",
        "error_logs_errorlog",
        "generated_models_generatedmodel",
        "print_jobs_auditlog",
        "print_jobs_printjob"
    ]

    try:
        conn = get_connection()
        cur = conn.cursor()
    except Exception as e:
        pwd = os.environ.get("SPRING_DATASOURCE_PASSWORD", "")
        msg = str(e)
        if pwd:
            msg = msg.replace(pwd, "********")
        print(f"[ERROR] Failed to connect to database: {msg}")
        sys.exit(1)

    # 1. RLS Status
    print("\n[1] Row Level Security (RLS) Status (pg_class):")
    cur.execute("""
        SELECT relname, relrowsecurity, relforcerowsecurity
        FROM pg_class
        JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace
        WHERE nspname = 'public' AND relname = ANY(%s)
        ORDER BY relname;
    """, (tables,))
    all_protected = True
    for r in cur.fetchall():
        status = "PROTECTED (RLS Enabled)" if r[1] else "UNRESTRICTED (RLS Disabled)"
        print(f"  - {r[0]:<35} : {status}")
        if not r[1]:
            all_protected = False

    # 2. Active Policies
    print("\n[2] Active RLS Policies in pg_policies:")
    cur.execute("""
        SELECT tablename, policyname, roles, cmd, permissive
        FROM pg_policies
        WHERE schemaname = 'public' AND tablename = ANY(%s)
        ORDER BY tablename, policyname;
    """, (tables,))
    policies = cur.fetchall()
    if not policies:
        print("  - [WARNING] No RLS policies found on target tables.")
    else:
        for p in policies:
            print(f"  - Table: {p[0]:<30} | Policy: {p[1]:<35} | Roles: {p[2]} | Action: {p[3]}")

    # 3. Public Grants
    print("\n[3] Grants on Target Tables for untrusted roles (anon / authenticated):")
    cur.execute("""
        SELECT grantee, table_name, privilege_type
        FROM information_schema.role_table_grants
        WHERE table_schema = 'public' 
          AND table_name = ANY(%s)
          AND grantee IN ('anon', 'authenticated')
        ORDER BY table_name, grantee, privilege_type;
    """, (tables,))
    grants = cur.fetchall()
    if not grants:
        print("  - [SECURE] Zero privileges granted to 'anon' or 'authenticated' on target tables.")
    else:
        print(f"  - [WARNING] Found {len(grants)} active privileges on target tables for public roles:")
        for g in grants[:10]:
            print(f"      * {g[1]:<30} -> {g[0]:<15} : {g[2]}")
        if len(grants) > 10:
            print(f"      * ... and {len(grants) - 10} more.")

    # 4. Data Preservation
    print("\n[4] Active Row Counts (Data Preservation Check via 'postgres' role):")
    for t in tables:
        cur.execute(f"SELECT COUNT(*) FROM {t};")
        cnt = cur.fetchone()[0]
        print(f"  - {t:<35} : {cnt} rows")

    # 5. Access Behavior Simulation
    print("\n[5] Access Behavior Simulation:")
    
    # 5.1 Test unprivileged 'anon' access
    print("  Testing unprivileged 'anon' access simulation...")
    try:
        conn.rollback() # Ensure clean transaction state
        cur.execute("SET LOCAL ROLE anon;")
        cur.execute("SELECT COUNT(*) FROM auth_user;")
        count_as_anon = cur.fetchone()[0]
        if count_as_anon == 0:
            print("  - [BEHAVIOR: SECURE] Role 'anon' returned 0 rows on 'auth_user' under RLS.")
        else:
            print(f"  - [BEHAVIOR: VULNERABLE] Role 'anon' was able to read {count_as_anon} rows from 'auth_user'!")
        conn.rollback()
    except psycopg2.errors.InsufficientPrivilege:
        conn.rollback()
        print("  - [BEHAVIOR: SECURE] Role 'anon' received 'InsufficientPrivilege' (access denied as expected).")
    except Exception as e:
        conn.rollback()
        print(f"  - [BEHAVIOR NOTE] Query as 'anon' failed: {e}")

    # 5.2 Test 'service_role' administrative access
    print("  Testing administrative 'service_role' access simulation...")
    try:
        conn.rollback()
        cur.execute("SET LOCAL ROLE service_role;")
        cur.execute("SELECT COUNT(*) FROM auth_user;")
        count_as_service = cur.fetchone()[0]
        print(f"  - [BEHAVIOR: OPERATIONAL] Role 'service_role' read {count_as_service} rows (administrative access confirmed).")
        conn.rollback()
    except Exception as e:
        conn.rollback()
        print(f"  - [BEHAVIOR ERROR] Query as 'service_role' failed: {e}")

    cur.close()
    conn.close()
    print("\n" + "=" * 75)
    print("Verification completed.")
    print("=" * 75)

if __name__ == "__main__":
    main()
