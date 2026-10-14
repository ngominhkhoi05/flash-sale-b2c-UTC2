"""Verify FK integrity + business rule consistency after bulk load."""
import os

import psycopg

# Doc tu env var de tranh commit secret.
CONN_STR = os.environ.get(
    "SUPABASE_DB_URL",
    "postgresql://postgres.dygnssynjofuaaapnvcg:"
    "%26zyXVX8TNLWYF%21B"
    "@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres"
    "?sslmode=require&connect_timeout=10",
)

QUERIES = [
    ("Seller users co store (1-1)",
     "SELECT COUNT(*) FROM users u JOIN user_roles ur ON u.id=ur.user_id "
     "JOIN roles r ON ur.role_id=r.id WHERE r.name='SELLER' "
     "AND EXISTS (SELECT 1 FROM stores s WHERE s.user_id=u.id)"),
    ("Buyer users co address",
     "SELECT COUNT(*) FROM users u JOIN user_roles ur ON u.id=ur.user_id "
     "JOIN roles r ON ur.role_id=r.id WHERE r.name='BUYER' "
     "AND EXISTS (SELECT 1 FROM addresses a WHERE a.user_id=u.id)"),
    ("San pham co SKU",
     "SELECT COUNT(*) FROM products p "
     "WHERE EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id)"),
    ("SKU khong co san pham (orphan)",
     "SELECT COUNT(*) FROM product_variants v "
     "WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id=v.product_id)"),
    ("Flash sale items co slot",
     "SELECT COUNT(*) FROM flash_sale_items fsi "
     "WHERE EXISTS (SELECT 1 FROM flash_sale_slots s WHERE s.id=fsi.slot_id)"),
    ("Flash sale items co variant",
     "SELECT COUNT(*) FROM flash_sale_items fsi "
     "WHERE EXISTS (SELECT 1 FROM product_variants v WHERE v.id=fsi.variant_id)"),
    ("Store voucher co store that",
     "SELECT COUNT(*) FROM vouchers v WHERE v.store_id IS NOT NULL "
     "AND EXISTS (SELECT 1 FROM stores s WHERE s.id=v.store_id)"),
    ("Orphan store voucher",
     "SELECT COUNT(*) FROM vouchers v WHERE v.store_id IS NOT NULL "
     "AND NOT EXISTS (SELECT 1 FROM stores s WHERE s.id=v.store_id)"),
    ("Images PRODUCT co product that",
     "SELECT COUNT(*) FROM images WHERE owner_type='PRODUCT' "
     "AND EXISTS (SELECT 1 FROM products p WHERE p.id=owner_id)"),
    ("Images VARIANT co variant that",
     "SELECT COUNT(*) FROM images WHERE owner_type='VARIANT' "
     "AND EXISTS (SELECT 1 FROM product_variants v WHERE v.id=owner_id)"),
    ("Images USER co user that",
     "SELECT COUNT(*) FROM images WHERE owner_type='USER' "
     "AND EXISTS (SELECT 1 FROM users u WHERE u.id=owner_id)"),
    ("Images STORE co store that",
     "SELECT COUNT(*) FROM images WHERE owner_type='STORE' "
     "AND EXISTS (SELECT 1 FROM stores s WHERE s.id=owner_id)"),
    ("Images REVIEW co review that",
     "SELECT COUNT(*) FROM images WHERE owner_type='REVIEW' "
     "AND EXISTS (SELECT 1 FROM product_reviews r WHERE r.id=owner_id)"),
    ("Stores user_id unique? (neu co 2 store cung user_id -> vi pham)",
     "SELECT COUNT(*) FROM (SELECT user_id, COUNT(*) c FROM stores "
     "GROUP BY user_id HAVING COUNT(*) > 1) t"),
    ("Constraint chk_addresses_owner (user XOR store)",
     "SELECT COUNT(*) FROM addresses "
     "WHERE NOT ((user_id IS NOT NULL AND store_id IS NULL) "
     "OR (user_id IS NULL AND store_id IS NOT NULL))"),
]

with psycopg.connect(CONN_STR) as conn:
    with conn.cursor() as cur:
        for label, q in QUERIES:
            cur.execute(q)
            n = cur.fetchone()[0]
            ok = "OK" if (n > 0 if "Orphan" not in label and "unique" not in label and "Constraint" not in label else n == 0) else "FAIL"
            print(f"  [{ok}] {label:50s} = {n}")
