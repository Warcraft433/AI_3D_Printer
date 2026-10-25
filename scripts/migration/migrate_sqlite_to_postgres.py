#!/usr/bin/env python3
"""
PrintGuard AI — Safe, Repeatable SQLite to PostgreSQL Migration Script
Migrates all persistent application data from SQLite (backend/db.sqlite3)
to PostgreSQL without data loss or downtime.

Safety features:
- Reads SQLite in read-only mode (URI mode=ro).
- Uses a single database transaction in PostgreSQL (atomic commit or rollback).
- Preserves explicit IDs, relationships, timestamps, and Django PBKDF2 password hashes.
- Automatically resets PostgreSQL sequences to MAX(id).
- Compares record counts table-by-table without printing sensitive passwords or secrets.
- Supports --dry-run mode for pre-flight validation.
"""

import os
import sys
import argparse
import sqlite3
import psycopg2
from psycopg2.extras import execute_batch

TABLES = [
    {
        "name": "auth_user",
        "seq": "auth_user_id_seq",
        "columns": [
            "id", "password", "last_login", "is_superuser", "username",
            "first_name", "last_name", "email", "is_staff", "is_active", "date_joined"
        ],
        "bool_columns": ["is_superuser", "is_staff", "is_active"]
    },
    {
        "name": "generated_models_generatedmodel",
        "seq": "generated_models_generatedmodel_id_seq",
        "columns": [
            "id", "prompt", "file_path", "thumbnail_path", "generation_time",
            "status", "is_repaired", "source_type", "steps", "created_at",
            "user_id", "error_detail", "celery_task_id"
        ],
        "bool_columns": ["is_repaired"]
    },
    {
        "name": "print_jobs_printjob",
        "seq": "print_jobs_printjob_id_seq",
        "columns": [
            "id", "status", "gcode_path", "estimated_time", "progress",
            "start_time", "end_time", "printer_ip", "auto_pause_enabled",
            "error_detail", "celery_task_id", "created_at", "model_id"
        ],
        "bool_columns": ["auto_pause_enabled"]
    },
    {
        "name": "error_logs_errorlog",
        "seq": "error_logs_errorlog_id_seq",
        "columns": [
            "id", "error_type", "confidence", "action_taken", "timestamp",
            "print_job_id", "frame_image"
        ],
        "bool_columns": []
    },
    {
        "name": "print_jobs_auditlog",
        "seq": "print_jobs_auditlog_id_seq",
        "columns": [
            "id", "action", "job_id", "timestamp", "user_id"
        ],
        "bool_columns": []
    }
]


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


def get_pg_connection():
    import re
    load_env()
    jdbc_url = os.getenv("SPRING_DATASOURCE_URL")
    if jdbc_url:
        m = re.match(r"jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)(?:\?(.*))?", jdbc_url)
        if m:
            host = m.group(1)
            port = int(m.group(2) or 5432)
            dbname = m.group(3)
            query_params = m.group(4) or ""
            sslmode = "require" if "sslmode=require" in query_params else "prefer"
            user = os.getenv("SPRING_DATASOURCE_USERNAME", os.getenv("PGUSER", "postgres"))
            password = os.getenv("SPRING_DATASOURCE_PASSWORD", os.getenv("PGPASSWORD", ""))
            return psycopg2.connect(
                host=host,
                port=port,
                dbname=dbname,
                user=user,
                password=password,
                sslmode=sslmode,
                connect_timeout=15
            )

    host = os.getenv("PGHOST", "localhost")
    port = int(os.getenv("PGPORT", "5432"))
    dbname = os.getenv("PGDATABASE", "printguard")
    user = os.getenv("PGUSER", "printguard_app")
    password = os.getenv("PGPASSWORD", "change-this-password")

    return psycopg2.connect(
        host=host,
        port=port,
        dbname=dbname,
        user=user,
        password=password
    )


def read_sqlite_records(sqlite_path, table_info):
    table_name = table_info["name"]
    cols = table_info["columns"]
    bool_cols = set(table_info.get("bool_columns", []))
    col_str = ", ".join(f'"{c}"' for c in cols)

    # Open SQLite read-only
    uri = f"file:{os.path.abspath(sqlite_path)}?mode=ro"
    conn = sqlite3.connect(uri, uri=True)
    cur = conn.cursor()
    cur.execute(f'SELECT {col_str} FROM "{table_name}" ORDER BY id ASC')
    raw_rows = cur.fetchall()
    conn.close()

    processed_rows = []
    for r in raw_rows:
        row_list = list(r)
        for idx, col in enumerate(cols):
            if col in bool_cols and row_list[idx] is not None:
                row_list[idx] = bool(row_list[idx])
        processed_rows.append(tuple(row_list))

    return processed_rows


def migrate(sqlite_path, dry_run=False):
    print("=" * 60)
    print("PrintGuard AI — SQLite to PostgreSQL Migration")
    print("=" * 60)
    print(f"Source SQLite: {sqlite_path}")
    print(f"Mode: {'DRY RUN (pre-flight only)' if dry_run else 'EXECUTE'}")

    if not os.path.isfile(sqlite_path):
        print(f"ERROR: SQLite database file not found at {sqlite_path}")
        sys.exit(1)

    # 1. Read all source data
    source_counts = {}
    source_data = {}
    for t in TABLES:
        rows = read_sqlite_records(sqlite_path, t)
        source_counts[t["name"]] = len(rows)
        source_data[t["name"]] = rows
        print(f"  Source '{t['name']}': {len(rows)} records found")

    if dry_run:
        print("\nPre-flight check completed. SQLite records verified.")
        return

    # 2. Connect to PostgreSQL
    try:
        pg_conn = get_pg_connection()
    except Exception as e:
        print(f"\nERROR: Could not connect to PostgreSQL: {e}")
        print("Please verify PostgreSQL is running and environment variables are set.")
        sys.exit(1)

    pg_cur = pg_conn.cursor()

    try:
        print("\nBeginning migration transaction...")
        for t in TABLES:
            table_name = t["name"]
            cols = t["columns"]
            rows = source_data[table_name]

            seq_name = t["seq"]
            if not rows:
                print(f"  Skipping '{table_name}' (0 records)")
                pg_cur.execute(f"SELECT setval('{seq_name}', 1, false);")
                continue

            col_names = ", ".join(cols)
            placeholders = ", ".join(["%s"] * len(cols))
            update_clauses = [f"{c} = EXCLUDED.{c}" for c in cols if c != "id"]
            update_str = ", ".join(update_clauses)

            sql = f"""
                INSERT INTO {table_name} ({col_names})
                VALUES ({placeholders})
                ON CONFLICT (id) DO UPDATE SET {update_str};
            """

            execute_batch(pg_cur, sql, rows)
            print(f"  Inserted/Updated {len(rows)} records in '{table_name}'")

            # Reset sequence to MAX(id)
            pg_cur.execute(f"""
                SELECT setval('{seq_name}', 
                              COALESCE((SELECT MAX(id) FROM {table_name}), 1), 
                              (SELECT COUNT(*) > 0 FROM {table_name}));
            """)

        # 3. Validation
        print("\nValidating table counts in PostgreSQL...")
        all_matched = True
        print(f"{'Table Name':<35} {'SQLite':<10} {'PostgreSQL':<10} {'Status':<10}")
        print("-" * 65)

        for t in TABLES:
            t_name = t["name"]
            pg_cur.execute(f"SELECT COUNT(*) FROM {t_name}")
            pg_count = pg_cur.fetchone()[0]
            sq_count = source_counts[t_name]
            matches = (pg_count >= sq_count)
            status_str = "MATCH" if matches else "MISMATCH"
            if not matches:
                all_matched = False
            print(f"{t_name:<35} {sq_count:<10} {pg_count:<10} {status_str:<10}")

        if not all_matched:
            raise RuntimeError("Validation failed: Source and destination counts do not match!")

        pg_conn.commit()
        print("\nMigration transaction COMMITTED successfully!")

    except Exception as e:
        pg_conn.rollback()
        pwd = os.getenv("SPRING_DATASOURCE_PASSWORD", "")
        err_msg = str(e)
        if pwd:
            err_msg = err_msg.replace(pwd, "********")
        print(f"\nERROR during migration: {err_msg}")
        print("Transaction ROLLED BACK. No changes were committed to PostgreSQL.")
        sys.exit(1)
    finally:
        pg_cur.close()
        pg_conn.close()


def validate_only():
    print("=" * 60)
    print("PrintGuard AI — Database Validation Check")
    print("=" * 60)
    try:
        pg_conn = get_pg_connection()
        pg_cur = pg_conn.cursor()
        print(f"{'Table Name':<35} {'PostgreSQL Count':<15}")
        print("-" * 50)
        for t in TABLES:
            t_name = t["name"]
            pg_cur.execute(f"SELECT COUNT(*) FROM {t_name}")
            cnt = pg_cur.fetchone()[0]
            print(f"{t_name:<35} {cnt:<15}")
        pg_cur.close()
        pg_conn.close()
        print("\nAll application tables exist and were queried successfully.")
    except Exception as e:
        print(f"PostgreSQL connection check failed: {e}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Migrate PrintGuard AI data from SQLite to PostgreSQL")
    parser.add_argument("--sqlite-path", default="backend/db.sqlite3", help="Path to SQLite database")
    parser.add_argument("--dry-run", action="store_true", help="Inspect source data without connecting to PostgreSQL")
    parser.add_argument("--validate-only", action="store_true", help="Check PostgreSQL table row counts")
    parser.add_argument("--execute", action="store_true", help="Execute the actual migration")

    args = parser.parse_args()

    if args.validate_only:
        validate_only()
    elif args.execute:
        migrate(args.sqlite_path, dry_run=False)
    else:
        migrate(args.sqlite_path, dry_run=True)
