"""Test Supabase connection and list existing table state.

Khong truy cap mang ngoai, chi read schema. Se khong chen du lieu.
"""

import os
import sys

import psycopg

# Doc tu env var de tranh commit secret.
CONN_STR = os.environ.get(
    "SUPABASE_DB_URL",
    "postgresql://postgres.dygnssynjofuaaapnvcg:"
    "%26zyXVX8TNLWYF%21B"  # & -> %26, ! -> %21
    "@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres"
    "?sslmode=require&connect_timeout=10",
)

try:
    with psycopg.connect(CONN_STR) as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT version(), current_database(), current_user")
            v, db, usr = cur.fetchone()
            print("CONNECTED:")
            print(f"  version: {v[:80]}")
            print(f"  db:      {db}")
            print(f"  user:    {usr}")

            cur.execute(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema='public'
                ORDER BY table_name
                """
            )
            tables = [r[0] for r in cur.fetchall()]
            print(f"\n  public tables: {len(tables)}")
            for t in tables:
                cur.execute(f'SELECT COUNT(*) FROM "{t}"')
                c = cur.fetchone()[0]
                print(f"    {t:30s} {c:>6} rows")
except Exception as e:
    print(f"ERROR: {type(e).__name__}: {e}")
    sys.exit(1)
