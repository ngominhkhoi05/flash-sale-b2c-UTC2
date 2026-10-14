"""Load CSV vao Supabase (Postgres) - dung column mapping de tranh schema drift.

Thay vi COPY truc tiep (se loi neu CSV co cot khong co trong DB), script
nay doc CSV bang DictReader, query information_schema de biet DB columns,
va insert cac cot co mat trong ca CSV va DB. Cot CSV thua (vd 'id' khi
PK la composite) se duoc bo qua; cot CSV thieu se lay DEFAULT.

Thuc hien trong 1 transaction: TRUNCATE + bulk insert. Neu loi o bat
ky bang nao -> rollback toan bo.

Cai dat:
    .venv\Scripts\python.exe -m pip install "psycopg[binary]"
"""

import csv
import sys
import time
from pathlib import Path

import psycopg

CONN_STR = (
    "postgresql://postgres.dygnssynjofuaaapnvcg:"
    "%26zyXVX8TNLWYF%21B"
    "@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres"
    "?sslmode=require&connect_timeout=10"
)

HERE = Path(__file__).parent

# (filename, table) theo thu tu FK dependency
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
    ("flash_sale_slots.csv",   "flash_sale_slots"),
    ("flash_sale_items.csv",   "flash_sale_items"),
    ("vouchers.csv",           "vouchers"),
    ("carts.csv",              "carts"),
    ("store_followers.csv",    "store_followers"),
    ("product_reviews.csv",    "product_reviews"),
    ("images.csv",             "images"),
]


def get_db_columns(cur, table: str) -> list[str]:
    """Lay ten cac cot trong DB theo dung thu tu."""
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
    """Dung COPY ... FROM STDIN voi header cua CSV de Postgres parse.

    CSV co the co cot thua (vd 'id' voi composite PK) hoac cot khac ten
    voi DB. Script:
      1. Doc header CSV
      2. Query information_schema de lay DB columns
      3. Intersection (case-insensitive) lam column list
      4. Filter moi data row chi giu cac cot trong intersection
      5. COPY voi HEADER false (vi ta da loc o buoc 4)
    """
    with open(csv_path, "r", encoding="utf-8", newline="") as fh:
        reader = csv.reader(fh)
        header = next(reader)
        db_cols = get_db_columns(cur, table)
        db_cols_lower = {c.lower(): c for c in db_cols}

        # Map CSV header index -> DB col name
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

        # Filter rows: chi giu cac cot mapped + validate length
        from io import StringIO
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

        # Build copy SQL with explicit column list
        cols_str = ", ".join(f'"{c}"' for _, c in col_indices)
        copy_sql = (
            f'COPY "{table}" ({cols_str}) '
            "FROM STDIN WITH (FORMAT csv, HEADER false)"
        )
        with cur.copy(copy_sql) as copy:
            copy.write(buf.read())
        cur.execute(f'SELECT COUNT(*) FROM "{table}"')
        return cur.fetchone()[0]


def main() -> int:
    if not HERE.joinpath("roles.csv").exists():
        print("ERROR: CSV files khong ton tai. Chay generate_all_seeds.py truoc.")
        return 1

    total_start = time.time()
    try:
        with psycopg.connect(CONN_STR) as conn:
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
