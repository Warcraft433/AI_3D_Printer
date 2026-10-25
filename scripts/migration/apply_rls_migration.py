#!/usr/bin/env python3
"""
PrintGuard AI — Safe RLS Migration Application Script
Applies V2__enable_rls_and_least_privilege.sql against PostgreSQL (Supabase) in an atomic transaction.
Never displays passwords or credentials.
DO NOT EXECUTE UNTIL EXPLICITLY APPROVED BY THE USER.
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
    print("=" * 70)
    print("PrintGuard AI — Applying V2 RLS & Least-Privilege Migration")
    print("=" * 70)

    migration_file = os.path.join(
        os.path.dirname(__file__),
        "..",
        "printguard-migration-starter",
        "backend-java",
        "src",
        "main",
        "resources",
        "db",
        "migration",
        "V2__enable_rls_and_least_privilege.sql"
    )
    migration_file = os.path.abspath(migration_file)
    if not os.path.isfile(migration_file):
        print(f"ERROR: Migration file not found at {migration_file}")
        sys.exit(1)

    with open(migration_file, "r", encoding="utf-8") as f:
        sql = f.read()

    print(f"Reading migration from: {migration_file}")
    print("Connecting to Supabase PostgreSQL...")
    conn = get_connection()
    cur = conn.cursor()

    try:
        print("Executing migration DDL in atomic transaction...")
        cur.execute(sql)
        conn.commit()
        print("[SUCCESS] V2 RLS Migration successfully applied and committed.")
    except Exception as e:
        conn.rollback()
        pwd = os.environ.get("SPRING_DATASOURCE_PASSWORD", "")
        err = str(e)
        if pwd:
            err = err.replace(pwd, "********")
        print(f"[FAILED] Error applying migration: {err}")
        sys.exit(1)
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    main()
