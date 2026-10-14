"""In ra schema cua cac bang lien quan de debug."""
import psycopg

CONN_STR = (
    "postgresql://postgres.dygnssynjofuaaapnvcg:"
    "%26zyXVX8TNLWYF%21B"
    "@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres"
    "?sslmode=require&connect_timeout=10"
)

TABLES = ["users", "stores", "user_roles", "addresses", "products",
          "product_variants", "flash_sale_slots", "flash_sale_items",
          "vouchers", "carts", "store_followers", "product_reviews",
          "images", "categories", "banners", "roles",
          "permission_groups", "permissions", "group_permissions"]

with psycopg.connect(CONN_STR) as conn:
    with conn.cursor() as cur:
        for t in TABLES:
            cur.execute(
                """
                SELECT column_name, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name=%s
                ORDER BY ordinal_position
                """,
                (t,),
            )
            rows = cur.fetchall()
            print(f"\n=== {t} ({len(rows)} cols) ===")
            for col, typ, nul, dflt in rows:
                d = f" DEFAULT {dflt}" if dflt else ""
                nn = " NULL" if nul == "YES" else " NOT NULL"
                print(f"  {col:30s} {typ:25s}{nn}{d}")
