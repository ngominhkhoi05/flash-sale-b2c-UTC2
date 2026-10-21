"""Load CSV vao Supabase (Postgres) - dung column mapping de tranh schema drift.

Thay vi COPY truc tiep (se loi neu CSV co cot khong co trong DB), script
nay doc CSV, query information_schema de biet DB columns,
va insert cac cot co mat trong ca CSV va DB. Cot CSV thua (vd 'id' khi
PK la composite) se duoc bo qua; cot CSV thieu se lay DEFAULT.

Thuc hien trong 1 transaction: TRUNCATE + bulk insert. Neu loi o bat
ky bang nao -> rollback toan bo.

Sau COPY, script tu dong sync tat ca sequences (RESTART IDENTITY chi dat
ve 1, COPY voi explicit ID khong advance sequence -> phai setval thu cong).

Cai dat:
    pip install "psycopg[binary]"

Su dung:
    $env:SUPABASE_DB_URL = "postgresql://user:pass@host:5432/db?sslmode=require"
    python load_to_supabase.py
"""

import csv
import os
import re
import sys
import time
from io import StringIO
from pathlib import Path

import psycopg

CONN_STR = os.environ.get(
    "SUPABASE_DB_URL",
    "postgresql://USER:PASSWORD@HOST:5432/DB?sslmode=require",
)

HERE = Path(__file__).parent

LOAD_ORDER = [
    ("roles.csv",              "roles"),
    ("permission_groups.csv",  "permission_groups"),
    ("permissions.csv",        "permissions"),
    ("group_permissions.csv",  "group_permissions"),
    ("categories.csv",         "categories"),
    ("banners.csv",            "banners"),
    ("users.csv",              "users"),
    ("user_roles.csv",         "user_roles"),
    ("stores.csv",             "stores"),
    ("addresses.csv",          "addresses"),
    ("products.csv",           "products"),
    ("product_variants.csv",   "product_variants"),
    ("flash_sale_slots.csv",  "flash_sale_slots"),
    ("flash_sale_items.csv",   "flash_sale_items"),
    ("vouchers.csv",           "vouchers"),
    ("carts.csv",              "carts"),
    ("store_followers.csv",    "store_followers"),
    ("product_reviews.csv",     "product_reviews"),
    ("images.csv",             "images"),
]


def get_db_columns(cur, table: str) -> list[str]:
    cur.execute(
        """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema='public' AND table_name=%s
        ORDER BY ordinal_position
        """,
        (table,),
    )
    return [r[0] for r in cur.fetchall()]


def copy_csv_to_table(cur, table: str, csv_path: Path) -> int:
    with open(csv_path, "r", encoding="utf-8", newline="") as fh:
        reader = csv.reader(fh)
        header = next(reader)
        db_cols = get_db_columns(cur, table)
        db_cols_lower = {c.lower(): c for c in db_cols}

        col_indices = []
        skipped = []
        for idx, h in enumerate(header):
            db_name = db_cols_lower.get(h.lower())
            if db_name:
                col_indices.append((idx, db_name))
            else:
                skipped.append(h)
        if skipped:
            print(f"      WARN: skip CSV columns not in DB: {skipped}")
        if not col_indices:
            raise ValueError(f"{csv_path.name}: khong co cot nao khop DB schema")

        buf = StringIO()
        writer = csv.writer(buf, lineterminator="\n")
        n_rows = 0
        n_skipped = 0
        expected_cols = len(col_indices)
        for line_no, row in enumerate(reader, start=2):
            if len(row) != len(header):
                n_skipped += 1
                print(f"      WARN: line {line_no} has {len(row)} cols "
                      f"(expected {len(header)}): {row[:3]}...")
                continue
            filtered = [row[idx] for idx, _ in col_indices]
            if len(filtered) != expected_cols:
                n_skipped += 1
                print(f"      WARN: line {line_no} mapped cols = {len(filtered)} "
                      f"(expected {expected_cols})")
                continue
            writer.writerow(filtered)
            n_rows += 1
        if n_skipped:
            print(f"      WARN: skipped {n_skipped} malformed rows, kept {n_rows}")
        buf.seek(0)

        cols_str = ", ".join(f'"{c}"' for _, c in col_indices)
        copy_sql = (
            f'COPY "{table}" ({cols_str}) '
            "FROM STDIN WITH (FORMAT csv, HEADER false)"
        )
        with cur.copy(copy_sql) as copy:
            copy.write(buf.read())
        cur.execute(f'SELECT COUNT(*) FROM "{table}"')
        return cur.fetchone()[0]


def sync_sequences(cur, table: str) -> None:
    """Sau COPY, sequence van o gia tri cu. COPY voi explicit ID khong advance sequence.
    Phai setval ve max_id de INSERT tiep theo tra ID dung.

    Bug da gap: pg_get_serial_sequence() tra ve sequence name nhung bang
    co the khong co cot id (vd group_permissions: composite PK group_id + permission_id).
    Neu khong co cot id -> pg_get_serial_sequence tra ve None, SKIP.
    Neu co cot id nhung no khong phai serial -> pg_get_serial_sequence tra ve None, SKIP.
    """
    # Buoc 1: Kiem tra cot id co ton tai khong
    cur.execute(
        "SELECT 1 FROM information_schema.columns "
        "WHERE table_schema='public' AND table_name=%s AND column_name='id'",
        (table,),
    )
    if not cur.fetchone():
        return  # bang khong co cot id -> skip

    # Buoc 2: Lay sequence name
    cur.execute(
        "SELECT pg_get_serial_sequence(%s, 'id')",
        (table,),
    )
    result = cur.fetchone()
    if not (result and result[0]):
        return  # cot id khong phai serial -> skip

    seq_name = result[0]

    # Buoc 3: Lay max_id va setval
    # Dung subquery de tranh SQL injection (table da duoc validate o buoc 1)
    cur.execute(
        "SELECT setval(%s, (SELECT COALESCE(MAX(id), 0) FROM \"" + table + "\"), true)",
        (seq_name,),
    )


def _build_connect_kwargs() -> dict:
    """Tra ve kwargs cho psycopg.connect().

    Supabase pgbouncer 2024+ yeu cau startup packet co 'project=<ref>' de
    xac dinh tenant (options=-c project=<ref>). Lay project_ref tu username
    (format postgres.<ref>).

    Them prepare_threshold=0 de psycopg3 KHONG dung prepared statement,
    vi pgbouncer transaction mode (port 6543) khong ho tro qua pool
    boundaries (-> loi '_pg3_0 does not exist').
    """
    kwargs: dict = {"connect_timeout": 30, "prepare_threshold": 0}
    m = re.search(r"://([^:@]+):", CONN_STR)
    if m:
        user = m.group(1)
        if "." in user:
            project_ref = user.split(".")[-1]
            kwargs["options"] = f"-c project={project_ref}"
    return kwargs


def main() -> int:
    if not HERE.joinpath("roles.csv").exists():
        print("ERROR: CSV files khong ton tai. Chay generate_all_seeds.py truoc.")
        return 1

    total_start = time.time()
    try:
        connect_kwargs = _build_connect_kwargs()
        with psycopg.connect(CONN_STR, **connect_kwargs) as conn:
            conn.execute("SET session_replication_role = 'replica'")
            print("== Pre-load: TRUNCATE all loaded tables ==")
            tables_to_trunc = [t for _, t in LOAD_ORDER]
            conn.execute(
                "TRUNCATE TABLE " + ", ".join(f'"{t}"' for t in tables_to_trunc) +
                " RESTART IDENTITY CASCADE"
            )
            conn.commit()
            print(f"   truncated {len(tables_to_trunc)} tables")

            print("\n== Bulk load ==")
            for filename, table in LOAD_ORDER:
                csv_path = HERE / filename
                if not csv_path.exists():
                    print(f"   SKIP  {filename:30s} (file missing)")
                    continue
                t0 = time.time()
                try:
                    with conn.cursor() as cur:
                        n = copy_csv_to_table(cur, table, csv_path)
                    conn.commit()
                    dt = time.time() - t0
                    print(f"   OK    {filename:30s} -> {table:20s} {n:>5} rows ({dt:.2f}s)")
                except Exception as e:
                    conn.rollback()
                    print(f"   FAIL  {filename:30s} -> {table:20s} ERROR: {e}")
                    raise

            print("\n== Sync sequences ==")
            with conn.cursor() as cur:
                for _, table in LOAD_ORDER:
                    sync_sequences(cur, table)
                conn.commit()
            print("   sequences synced")

            conn.execute("SET session_replication_role = 'origin'")
            conn.commit()

            print("\n== Verify (SELECT COUNT) ==")
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema='public' AND table_type='BASE TABLE'
                    ORDER BY table_name
                    """
                )
                for (t,) in cur.fetchall():
                    cur.execute(f'SELECT COUNT(*) FROM "{t}"')
                    c = cur.fetchone()[0]
                    print(f"    {t:30s} {c:>6} rows")

        print(f"\nTotal elapsed: {time.time() - total_start:.2f}s")
        return 0
    except Exception as e:
        print(f"\nFATAL: {type(e).__name__}: {e}")
        return 2


if __name__ == "__main__":
    sys.exit(main())
