"""

Generate bulk seed CSV files for the Flash Sale B2C database.



Produces 17 CSV files covering GROUP A (mandatory) and GROUP B

(recommended demo) tables defined in docs/database-seed-data.md.



Tables / row counts:

    roles.csv                        3

    permission_groups.csv            3

    permissions.csv                  29

    group_permissions.csv           ~65

    categories.csv                   10

    banners.csv                       8

    users.csv                        30  (1 admin + 4 seller + 25 buyer)

    user_roles.csv                   30  (1 role per user)

    stores.csv                       10

    addresses.csv                    25  (buyer shipping only)

    products.csv                    200

    product_variants.csv           ~600  (~3 variants per product)

    flash_sale_slots.csv             10

    flash_sale_items.csv             40  (~4 items per slot)

    vouchers.csv                     12  (6 platform + 6 store)

    carts.csv                        30  (1 per user)

    store_followers.csv              30  (random)

    product_reviews.csv              80  (random)

    images.csv                     ~800  (1 per owner: USER + STORE +

                                            PRODUCT + VARIANT + ~50% REVIEW)



Total: ~1,963 rows across 17 files. Files are written to this script's

directory (script_python/) and are ignored by .gitignore (never committed).



If a target CSV already exists on disk, it is deleted and re-created

from scratch (see `write_csv` for details).



Usage:

    python generate_all_seeds.py            # use defaults

    python generate_all_seeds.py --out .    # custom output dir



After generation, load the files into PostgreSQL in FK-dependency order

(see docs/database-seed-data.md section 6 for the exact sequence). When

loading `images.csv`, it must come AFTER users, stores, products,

product_variants, and product_reviews (FK to those is logical only, but

`owner_id` must point to a real record).

"""



import argparse

import csv

import datetime

import json

import random

import re

import sys

from pathlib import Path



from faker import Faker



# ---------------------------------------------------------------------------

# Configuration

# ---------------------------------------------------------------------------

HERE = Path(__file__).resolve().parent



# Row counts (CLI-overridable in the future, kept simple for now)

N_ROLES = 3

N_PERMISSION_GROUPS = 3

N_PERMISSIONS = 29

N_USERS = 36                # 1 admin + 10 seller + 25 buyer

N_CATEGORIES = 10

N_BANNERS = 8

N_STORES = 10

N_ADDRESSES = 25

N_PRODUCTS = 200

N_VARIANTS_PER_PRODUCT_AVG = 3

N_FLASH_SALE_SLOTS = 10

N_FLASH_SALE_ITEMS_PER_SLOT_AVG = 4

N_VOUCHERS = 12

N_CARTS = N_USERS

N_STORE_FOLLOWERS = 30

N_PRODUCT_REVIEWS = 80



# Per-image policy: 1 image per owner (avoids V3 partial-unique-index

# conflicts on (USER, ACTIVE) and (STORE, ACTIVE), and on

# (PRODUCT|VARIANT, is_primary=true, ACTIVE)).

# Total: N_USERS + N_STORES + N_PRODUCTS + variants + ~50% of reviews.

N_IMAGES_USER = N_USERS

N_IMAGES_STORE = N_STORES

N_IMAGES_PRODUCT = N_PRODUCTS

N_IMAGES_VARIANT = 0  # computed at runtime = len(variant_rows)

N_IMAGES_REVIEW_PCT = 0.5   # 50% of reviews get 1 image each



# RBAC fixed seed (deterministic; must match docs/database-seed-data.md)

ROLE_NAMES = ["BUYER", "SELLER", "ADMIN"]

PERMISSION_GROUP_NAMES = ["buyer_basic", "seller_dashboard", "admin_full"]



# 28 permissions, matching section 2.3 of docs/database-seed-data.md

PERMISSIONS = [

    # AUTH & USER

    ("user:read",       "View profile",         "user"),

    ("user:update",     "Update profile",       "user"),

    # PRODUCT / CATEGORY

    ("product:read",    "View product",         "product"),

    ("product:create",  "Create product",       "product"),

    ("product:update",  "Update product",       "product"),

    ("product:delete",  "Delete product",       "product"),

    # ORDER

    ("order:read",      "View order",           "order"),

    ("order:create",    "Create order",         "order"),

    ("order:cancel",    "Cancel order",         "order"),

    # FLASH SALE

    ("flashsale:read",          "View Flash Sale",         "flashsale"),

    ("flashsale:item:create",   "Register SKU Flash Sale", "flashsale"),

    ("flashsale:item:approve",  "Approve SKU Flash Sale",  "flashsale"),

    ("flashsale:slot:create",   "Create time slot",        "flashsale"),

    ("flashsale:slot:prewarm",  "Pre-warm Redis",          "flashsale"),

    # VOUCHER / STORE / WALLET / REVIEW

    ("voucher:read",    "View voucher",         "voucher"),

    ("voucher:create",  "Create voucher",       "voucher"),

    ("store:read",      "View store",           "store"),

    ("store:update",    "Update store",         "store"),

    ("store:approve",   "Approve store",        "store"),

    ("wallet:read",     "View wallet",          "wallet"),

    ("review:read",     "View review",          "review"),

    ("review:create",   "Create review",        "review"),

    ("review:moderate", "Hide/delete review",   "review"),

    # IMAGE / ADDRESS

    ("image:upload",    "Upload image",         "image"),

    ("image:delete",    "Delete image",         "image"),

    ("address:read",    "View address",         "user"),

    ("address:create",  "Create address",       "user"),

    ("address:update",  "Update address",       "user"),

    ("address:delete",  "Delete address",       "user"),

]

assert len(PERMISSIONS) == N_PERMISSIONS



# buyer_basic: read-only + cart/checkout + review + voucher claim

BUYER_BASIC_PERMS = {

    "user:read", "user:update",

    "product:read",

    "order:read", "order:create", "order:cancel",

    "voucher:read",

    "review:read", "review:create",

    "address:read", "address:create", "address:update", "address:delete",

    "wallet:read",

    "image:upload", "image:delete",

    "flashsale:read",

}

# seller_dashboard: manage store + products + flashsale + voucher + view orders

SELLER_DASHBOARD_PERMS = {

    "product:create", "product:update", "product:delete", "product:read",

    "store:read", "store:update",

    "order:read", "order:cancel",

    "flashsale:item:create", "flashsale:read",

    "voucher:create", "voucher:read",

    "review:read", "review:moderate",

    "image:upload", "image:delete",

    "wallet:read",

    "address:create", "address:update",

}

# admin_full: ALL active permissions

ADMIN_FULL_PERMS = {code for code, _, _ in PERMISSIONS}



# 10 categories - ASCII-safe so V8/V9 backfill slug correctly

CATEGORY_DATA = [

    ("Men Fashion",            "thoi-trang-nam",        0.0500, "Shirts, pants, accessories for men"),

    ("Women Fashion",          "thoi-trang-nu",         0.0500, "Shirts, pants, skirts, accessories for women"),

    ("Phones and Accessories", "dien-thoai-phu-kien",   0.0800, "Smartphone, tablet, cases"),

    ("Laptops and Computers",  "laptop-may-tinh",       0.0600, "Laptop, PC, components"),

    ("Home Appliances",        "do-gia-dung",           0.0500, "Kitchen, room devices"),

    ("Health and Beauty",      "suc-khoe-lam-dep",      0.0700, "Cosmetics, skincare"),

    ("Mom and Baby",           "me-be",                 0.0500, "Baby items, milk, toys"),

    ("Food and Beverages",     "thuc-pham",             0.0400, "Food, drinks, snacks"),

    ("Books and Stationery",   "sach-van-phong-pham",   0.0300, "Books, school supplies"),

    ("Sports and Travel",      "the-thao-du-lich",      0.0500, "Sports gear, luggage, backpacks"),

]

assert len(CATEGORY_DATA) == N_CATEGORIES



# Banner type / position / image / alt / cta_href / cta_text / is_active

BANNER_DATA = [

    ("HERO",              1, "/banners/hero-1.jpg",      "Flash Sale 12.12 - Up to 50% off",     "/flash-sales",                  "View now"),

    ("HERO",              2, "/banners/hero-2.jpg",      "Free shipping voucher",                "/vouchers",                     "Claim now"),

    ("HERO",              3, "/banners/hero-3.jpg",      "Men Fashion - End of season sale",     "/categories/thoi-trang-nam",    "Explore"),

    ("HERO",              4, "/banners/hero-4.jpg",      "Laptop deal of the month",             "/categories/laptop-may-tinh",   "Shop now"),

    ("FREESHIP",          1, "/banners/fs-1.jpg",        "FREESHIP XTRA - Orders from 0d",       "/promotions/freeship",          "Details"),

    ("VOUCHER_NEW_SHOP",  1, "/banners/vns-1.jpg",       "50K voucher for new customers",        "/vouchers/new-shop",            "Get now"),

    ("PROMOTION",         1, "/banners/promo-1.jpg",     "Buy 2 get 1 - Weekend only",           "/promotions/buy2get1",          "Buy now"),

    ("PROMOTION",         2, "/banners/promo-2.jpg",     "Mom and Baby mega sale",               "/categories/me-be",             "Discover"),

]

assert len(BANNER_DATA) == N_BANNERS



PRODUCT_STATUSES = ["ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "INACTIVE", "OUT_OF_STOCK"]

DISCOUNT_TYPE_OPTIONS = ["FIXED", "PERCENT"]

DISCOUNT_VALUE_FIXED = [20000, 50000, 100000, 200000]

DISCOUNT_VALUE_PERCENT = [5.0, 10.0, 15.0, 20.0, 25.0, 30.0]

SLOT_STATUSES = ["UPCOMING", "UPCOMING", "ACTIVE", "ENDED"]

FLASH_SALE_ITEM_STATUSES = ["APPROVED", "APPROVED", "APPROVED", "ACTIVE"]

VOUCHER_STATUSES = ["ACTIVE", "ACTIVE", "ACTIVE", "EXPIRED"]

ADDRESS_PROVINCES = ["Ho Chi Minh", "Ha Noi", "Da Nang", "Can Tho", "Hai Phong", "Binh Duong"]

ADDRESS_DISTRICTS = ["District 1", "District 3", "District 7", "Cau Giay", "Hai Chau", "Ninh Kieu"]

REVIEW_STATUSES = ["VISIBLE", "VISIBLE", "VISIBLE", "VISIBLE", "HIDDEN"]

RATING_OPTIONS = [1, 2, 3, 4, 4, 5, 5, 5, 5, 5]   # skew towards 4-5





# ---------------------------------------------------------------------------

# Helpers

# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:

    parser = argparse.ArgumentParser(

        description="Generate Flash Sale B2C seed CSV files."

    )

    parser.add_argument(

        "--out", type=Path, default=HERE,

        help="Output directory for the CSV files (default: script_python/).",

    )

    parser.add_argument("--seed", type=int, default=42,

                        help="Random seed for reproducible output (default: 42).")

    return parser.parse_args()





def write_csv(path: Path, header: list, rows: list) -> None:

    """Write a single CSV file with the given header and rows.



    If `path` already exists, it is deleted first. This guarantees that

    the file always reflects the current run's output (no leftover rows

    from a previous run with different parameters). A short "overwriting"

    log line is printed to stderr so the user can see what happened.

    """

    path.parent.mkdir(parents=True, exist_ok=True)

    existed = path.exists()

    if existed:

        path.unlink()

        print(f"    (overwriting existing {path.name})", file=sys.stderr)

    with open(path, "w", newline="", encoding="utf-8") as fh:

        writer = csv.writer(fh)

        writer.writerow(header)

        writer.writerows(rows)





def make_users(n: int, fake: Faker) -> list:

    """Generate n users: 1 admin, 4 sellers, n-5 buyers."""

    users = []

    used_emails = set()



    def add_user(idx, full_name, email, password_hash, status, role_marker):

        users.append({

            "id": idx,

            "email": email,

            "password_hash": password_hash,

            "full_name": full_name,

            "phone": fake.msisdn()[:15],

            "status": status,

            "created_at": now,

            "updated_at": now,

            "_role": role_marker,   # internal, used to derive user_roles + stores

        })

        used_emails.add(email)



    # 1 admin

    add_user(1, "Administrator",

             "admin@flashsale.local",

             "$2a$10$placeholder.bcrypt.hash.admin123",

             "ACTIVE", "admin")



    # 10 sellers (also become store owners below)

    # 10 sellers (one per store) so stores.user_id stays UNIQUE

    for i in range(2, 12):

        suffix = f"seller{i-1}"

        add_user(i, f"Seller {i-1}",

                 f"{suffix}@demo.local",

             f"$2a$10$placeholder.bcrypt.hash.seller{i-1}",

             "ACTIVE", "seller")



    # remaining are buyers

    for i in range(12, n + 1):

        name = fake.name()

        # slugify email

        slug = name.lower().replace(" ", ".").replace(",", "")

        email = f"{slug}.buyer{i}@demo.local"

        if email in used_emails:

            email = f"buyer{i}@demo.local"

        add_user(i, name, email,

                 f"$2a$10$placeholder.bcrypt.hash.buyer{i}",

                 "ACTIVE", "buyer")



    return users





def make_variants(fake: Faker, product_id: int, n: int) -> list:

    """Generate n variants for a product. n is small (1..5)."""

    rows = []

    for j in range(1, n + 1):

        sku = f"PRD-{product_id:05d}-V{j}"

        variant_name = f"Variant {j} of #{product_id}"

        attrs = json_str_attrs(fake, product_id, j)

        original_price = round(random.uniform(50_000, 30_000_000), -3)

        if original_price < 1_000:

            original_price = 1_000

        stock = random.randint(10, 500)

        rows.append([

            product_id, sku, variant_name, attrs,

            f"{original_price:.2f}", stock, "ACTIVE", 0,   # version=0

            now,

        ])

    return rows





def json_str_attrs(fake: Faker, product_id: int, variant_idx: int) -> str:

    """Build a small JSON string for variant attributes (color/size)."""

    colors = ["Black", "White", "Red", "Blue", "Green", "Yellow", "Pink", "Gray"]

    sizes = ["S", "M", "L", "XL", "XXL"]

    payload = {

        "color": random.choice(colors),

        "size": random.choice(sizes),

        "variant_index": variant_idx,

    }

    import json

    return json.dumps(payload, ensure_ascii=False)





def slugify(name: str) -> str:

    """Chuyen 'Ao thun Cao cap #1' -> 'ao-thun-cao-cap-1'."""

    s = name.lower()

    # Bo dau tieng Viet co ban

    s = re.sub(r"[àáạảãâầấậẩẫăằắặẳẵ]", "a", s)

    s = re.sub(r"[èéẹẻẽêềếệểễ]", "e", s)

    s = re.sub(r"[ìíịỉĩ]", "i", s)

    s = re.sub(r"[òóọỏõôồốộổỗơờớợởỡ]", "o", s)

    s = re.sub(r"[ùúụủũưừứựửữ]", "u", s)

    s = re.sub(r"[ỳýỵỷỹ]", "y", s)

    s = re.sub(r"[đ]", "d", s)

    s = re.sub(r"[^a-z0-9]+", "-", s)

    s = s.strip("-")

    return s or "san-pham"





def make_product_specs(fake: Faker) -> str:

    """JSONB: thong so ky thuat (vat lieu, trong luong, bao hanh, xuat xu)."""

    return json.dumps({

        "material": random.choice([

            "Cotton 100%", "Polyester", "Hop kim nhom", "Nhua ABS cao cap",

            "Thep khong gi", "Da PU", "Vai Canvas", "Go tu nhien",

        ]),

        "weight": f"{random.randint(50, 3000)}g",

        "warranty": random.choice([

            "3 thang", "6 thang", "12 thang", "24 thang", "Khong bao hanh",

        ]),

        "origin": random.choice([

            "Viet Nam", "Trung Quoc", "Han Quoc", "Nhat Ban", "My", "Duc",

        ]),

    }, ensure_ascii=False)





def make_product_features() -> str:

    """JSONB: mang cac diem noi bat cua san pham (3-5 muc)."""

    pool = [

        "Thiet ke hien dai, tre trung",

        "Chat lieu cao cap, ben bi",

        "Khang nuoc, chong bui",

        "De dang su dung, thao tac don gian",

        "Tiet kiem nang luong, than thien moi truong",

        "Bao hanh chinh hang, doi tra trong 7 ngay",

        "Phu hop nhieu doi tuong su dung",

        "Mau sac da dang, thoi thuong",

        "Dong goi can than, sang trong",

    ]

    n = random.randint(3, 5)

    return json.dumps(random.sample(pool, n), ensure_ascii=False)





def make_product_commitments() -> str:

    """JSONB: cac cam ket tu shop (doi tra, bao hanh, giao hang)."""

    return json.dumps({

        "return_days": random.choice([3, 7, 14, 30]),

        "warranty_months": random.choice([0, 3, 6, 12, 24]),

        "authentic_guarantee": True,

        "shipping_time_hours": random.choice([24, 48, 72]),

        "free_shipping_threshold": random.choice([0, 200000, 500000]),

    }, ensure_ascii=False)





def make_product_size_chart() -> str:

    """JSONB: bang size (chi cho san pham co size). Tra ve None neu khong phu hop."""

    # 70% san pham co size chart

    if random.random() < 0.3:

        return ""

    # Tinh theo loai

    is_apparel = random.random() < 0.5

    if is_apparel:

        sizes = {

            "S":  {"chest_cm": random.randint(80, 90),  "length_cm": random.randint(60, 68)},

            "M":  {"chest_cm": random.randint(88, 96),  "length_cm": random.randint(64, 72)},

            "L":  {"chest_cm": random.randint(96, 104), "length_cm": random.randint(68, 76)},

            "XL": {"chest_cm": random.randint(104, 112),"length_cm": random.randint(72, 80)},

        }

    else:

        sizes = {

            "standard": {"width_cm": random.randint(10, 50), "height_cm": random.randint(10, 50)},

            "large":    {"width_cm": random.randint(50, 100),"height_cm": random.randint(50, 100)},

        }

    return json.dumps(sizes, ensure_ascii=False)





def make_product_description_rich(description: str) -> str:

    """JSONB: mo ta dang giau (intro + highlights)."""

    # Tach description thanh cac cau

    sentences = re.split(r"[.!?]+", description)

    sentences = [s.strip() for s in sentences if s.strip()]

    highlights = sentences[:3] if sentences else ["San pham chat luong cao"]

    return json.dumps({

        "intro": sentences[0] if sentences else description,

        "highlights": highlights,

        "paragraphs": sentences,

    }, ensure_ascii=False)





# ---------------------------------------------------------------------------

# Main

# ---------------------------------------------------------------------------

def main() -> int:

    args = parse_args()

    random.seed(args.seed)

    fake = Faker("vi_VN")

    Faker.seed(args.seed)

    global now

    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")



    out = args.out

    print(f"Output directory: {out}")

    print(f"Random seed: {args.seed}")



    # ------------------------------------------------------------------ roles

    roles_rows = []

    for i, name in enumerate(ROLE_NAMES, start=1):

        desc = {

            "BUYER": "Customer / Default buyer",

            "SELLER": "Seller / Store owner",

            "ADMIN": "System administrator",

        }[name]

        roles_rows.append([i, name, desc, now])

    write_csv(out / "roles.csv", ["id", "name", "description", "created_at"], roles_rows)

    print(f"  roles.csv                  {len(roles_rows):>4} rows")



    # --------------------------------------------------- permission_groups

    pg_rows = []

    for i, name in enumerate(PERMISSION_GROUP_NAMES, start=1):

        desc = {

            "buyer_basic":      "Basic Buyer permissions (purchase, review)",

            "seller_dashboard": "Seller permissions (manage store, products, orders)",

            "admin_full":       "Admin permissions (full system)",

        }[name]

        pg_rows.append([i, name, desc, now])

    write_csv(out / "permission_groups.csv",

              ["id", "name", "description", "created_at"], pg_rows)

    print(f"  permission_groups.csv      {len(pg_rows):>4} rows")



    # --------------------------------------------------------- permissions

    perm_rows = []

    perm_id_by_code = {}

    for i, (code, name, module) in enumerate(PERMISSIONS, start=1):

        perm_rows.append([i, code, name, module, True, now])

        perm_id_by_code[code] = i

    write_csv(out / "permissions.csv",

              ["id", "code", "name", "module", "is_active", "created_at"], perm_rows)

    print(f"  permissions.csv            {len(perm_rows):>4} rows")



    # --------------------------------------------------- group_permissions

    gp_rows = []

    gid_by_name = {n: i + 1 for i, n in enumerate(PERMISSION_GROUP_NAMES)}

    next_id = 1

    for code, _, _ in PERMISSIONS:

        pid = perm_id_by_code[code]

        for group_name, allowed in [

            ("buyer_basic",      code in BUYER_BASIC_PERMS),

            ("seller_dashboard", code in SELLER_DASHBOARD_PERMS),

            ("admin_full",       code in ADMIN_FULL_PERMS),

        ]:

            if allowed:

                gp_rows.append([next_id, gid_by_name[group_name], pid, now])

                next_id += 1

    write_csv(out / "group_permissions.csv",

              ["id", "group_id", "permission_id", "assigned_at"], gp_rows)

    print(f"  group_permissions.csv      {len(gp_rows):>4} rows")



    # --------------------------------------------------------- categories

    cat_rows = []

    for i, (name, slug, rate, desc) in enumerate(CATEGORY_DATA, start=1):

        cat_rows.append([i, name, slug, f"{rate:.4f}", desc])

    write_csv(out / "categories.csv",

              ["id", "name", "slug", "commission_rate", "description"], cat_rows)

    print(f"  categories.csv             {len(cat_rows):>4} rows")



    # ------------------------------------------------------------ banners

    banner_rows = []

    for i, (btype, pos, img, alt, href, text) in enumerate(BANNER_DATA, start=1):

        # start_date = now, end_date = now + random(7-30 days)

        end = (datetime.datetime.now() + datetime.timedelta(

            days=random.randint(7, 30))).strftime("%Y-%m-%d %H:%M:%S")

        start = now

        banner_rows.append([i, btype, pos, img, alt, href, text, True, start, end, now])

    write_csv(out / "banners.csv",

              ["id", "type", "position", "image_url", "image_alt",

               "cta_href", "cta_text", "is_active", "start_date", "end_date",

               "created_at"], banner_rows)

    print(f"  banners.csv                {len(banner_rows):>4} rows")



    # -------------------------------------------------------------- users

    users = make_users(N_USERS, fake)

    user_rows = []

    for u in users:

        user_rows.append([

            u["id"], u["email"], u["password_hash"], u["full_name"],

            u["phone"], "ACTIVE", u["created_at"], u["updated_at"],

        ])

    write_csv(out / "users.csv",

              ["id", "email", "password_hash", "full_name", "phone",

               "status", "created_at", "updated_at"], user_rows)

    print(f"  users.csv                  {len(user_rows):>4} rows")



    # ---------------------------------------------------------- user_roles

    user_role_rows = []

    role_id_by_name = {n: i + 1 for i, n in enumerate(ROLE_NAMES)}

    pg_id_by_name = gid_by_name

    for u in users:

        marker = u["_role"]

        if marker == "admin":

            role_name = "ADMIN"

            group_name = "admin_full"

        elif marker == "seller":

            role_name = "SELLER"

            group_name = "seller_dashboard"

        else:

            role_name = "BUYER"

            group_name = "buyer_basic"

        user_role_rows.append([

            u["id"], role_id_by_name[role_name], pg_id_by_name[group_name], now,

        ])

    write_csv(out / "user_roles.csv",

              ["user_id", "role_id", "permission_group_id", "assigned_at"],

              user_role_rows)

    print(f"  user_roles.csv             {len(user_role_rows):>4} rows")



    # -------------------------------------------------------------- stores

    seller_users = [u for u in users if u["_role"] == "seller"]

    # 1 store per seller (stores.user_id is UNIQUE in DB) — N_STORES == len(seller_users)

    assert N_STORES == len(seller_users), (

        f"N_STORES={N_STORES} must equal number of seller users={len(seller_users)}"

    )

    store_rows = []

    store_tiers = ["OFFICIAL_MALL", "PREFERRED", "NORMAL", "NORMAL", "NORMAL",

                   "NORMAL", "NORMAL", "PREFERRED", "NORMAL", "NORMAL"]

    store_names_seed = [

        "TechZone Official", "Fashion Street", "Mom and Baby Shop",

        "Book World", "Beauty Hub", "Sports Arena", "Home Essentials",

        "Audio Pro", "Mobile Center", "Pet Paradise",

    ]

    for i, owner in enumerate(seller_users):

        store_id = i + 1

        name = store_names_seed[i]

        tier = store_tiers[i]

        desc = f"Demo store #{store_id} - {name}"

        status = "APPROVED" if store_id <= 7 else "PENDING"

        store_rows.append([

            store_id, owner["id"], name, desc, "0.0500", status, tier, "", now,

        ])

    write_csv(out / "stores.csv",

              ["id", "user_id", "store_name", "description",

               "default_commission_rate", "status", "tier", "last_active_at",

               "created_at"], store_rows)

    print(f"  stores.csv                 {len(store_rows):>4} rows")



    # ------------------------------------------------------------ addresses

    # 1 buyer address per buyer (25), 1 warehouse per store (10) = 35

    # Cap at N_ADDRESSES (25) by sampling.

    address_rows = []

    addr_id = 1

    # Buyer addresses (one per buyer, 25 rows -> use all 25)

    for u in users:

        if u["_role"] != "buyer":

            continue

        if addr_id > N_ADDRESSES:

            break

        province = random.choice(ADDRESS_PROVINCES)

        district = random.choice(ADDRESS_DISTRICTS)

        address_rows.append([

            addr_id, u["id"], "", u["full_name"], u["phone"],

            province, district, "Ward 1", f"{random.randint(1, 999)} Main St",

            "10.762622", "106.660172", False, now, now,

        ])

        addr_id += 1

    write_csv(out / "addresses.csv",

              ["id", "user_id", "store_id", "contact_name", "phone",

               "province", "district", "ward", "detail_address",

               "latitude", "longitude", "is_default", "created_at", "updated_at"],

              address_rows)

    print(f"  addresses.csv              {len(address_rows):>4} rows")



    # ------------------------------------------------------------ products

    product_rows = []

    variant_rows = []

    for pid in range(1, N_PRODUCTS + 1):

        store_id_pick = random.choice([s[0] for s in store_rows])

        category_id_pick = random.randint(1, N_CATEGORIES)

        keyword = random.choice([

            "Ao thun", "Quan jeans", "Giay the thao", "Tui xach",

            "Dien thoai", "Laptop", "Tai nghe", "Sac du phong",

            "Noi com dien", "May xay", "Binh giu nhiet", "Den ban",

            "Sach", "Vo so", "Bong ro", "Yoga mat",

        ])

        modifier = random.choice([

            "Cao cap", "Pro", "Mini", "Ultra", "Plus", "Standard",

        ])

        name = f"{keyword} {modifier} #{pid}"

        description = fake.paragraph(nb_sentences=2)

        tier_variation = '[{"name":"Size","options":["S","M","L"]},{"name":"Color","options":["Black","White"]}]'

        status = random.choice(PRODUCT_STATUSES)

        product_rows.append([

            pid, store_id_pick, category_id_pick, name, description,

            tier_variation, status, 0,

            slugify(name),                                    # slug tu name

            make_product_specs(fake),                         # specs JSONB

            make_product_features(),                          # features JSONB array

            make_product_commitments(),                       # commitments JSONB

            make_product_size_chart(),                        # size_chart JSONB (hoac "" = NULL)

            make_product_description_rich(description),       # description_rich JSONB

            now,

        ])

        # variants

        n_var = max(1, int(random.gauss(N_VARIANTS_PER_PRODUCT_AVG, 1)))

        n_var = min(n_var, 5)

        for v in make_variants(fake, pid, n_var):

            variant_rows.append([len(variant_rows) + 1] + v)  # prepend id

    write_csv(out / "products.csv",

              ["id", "store_id", "category_id", "name", "description",

               "tier_variation_configs", "status", "total_sold", "slug",

               "specs", "features", "commitments", "size_chart",

               "description_rich", "created_at"], product_rows)

    print(f"  products.csv               {len(product_rows):>4} rows")

    write_csv(out / "product_variants.csv",

              ["id", "product_id", "sku", "variant_name", "attributes",

               "original_price", "stock_quantity", "status", "version",

               "created_at"], variant_rows)

    print(f"  product_variants.csv       {len(variant_rows):>4} rows")



    # ----------------------------------------------- flash_sale_slots

    slot_rows = []

    for sid in range(1, N_FLASH_SALE_SLOTS + 1):

        # Spread slots over the next 3 days, 3-4 hours apart

        base = datetime.datetime.now() + datetime.timedelta(hours=2 * sid)

        start = base.strftime("%Y-%m-%d %H:%M:%S")

        end = (base + datetime.timedelta(hours=2)).strftime("%Y-%m-%d %H:%M:%S")

        title = f"Flash Sale slot {sid}"

        status = random.choice(SLOT_STATUSES)

        slot_rows.append([sid, title, start, end, 300, status, now])

    write_csv(out / "flash_sale_slots.csv",

              ["id", "title", "start_time", "end_time",

               "reservation_ttl_seconds", "status", "created_at"], slot_rows)

    print(f"  flash_sale_slots.csv       {len(slot_rows):>4} rows")



    # ----------------------------------------------- flash_sale_items

    fsi_rows = []

    fsi_id = 1

    for sid in range(1, N_FLASH_SALE_SLOTS + 1):

        n_items = max(2, int(random.gauss(N_FLASH_SALE_ITEMS_PER_SLOT_AVG, 1)))

        n_items = min(n_items, 6)

        used_variants = set()

        for _ in range(n_items):

            if not variant_rows:

                break

            v = random.choice(variant_rows)

            v_id = v[0]

            if v_id in used_variants:

                continue

            used_variants.add(v_id)

            original_price = float(v[5])

            # flash price = 70-90% of original

            flash_price = round(original_price * random.uniform(0.7, 0.9), -3)

            if flash_price < 1_000:

                flash_price = 1_000

            allocated = random.randint(5, 50)

            available = allocated

            user_limit = random.choice([1, 2, 2, 3])

            comm_override = round(random.uniform(0.02, 0.06), 4)

            fsi_rows.append([

                fsi_id, sid, v_id, f"{flash_price:.2f}",

                allocated, available, user_limit, f"{comm_override:.4f}",

                random.choice(FLASH_SALE_ITEM_STATUSES), now,

            ])

            fsi_id += 1

    write_csv(out / "flash_sale_items.csv",

              ["id", "slot_id", "variant_id", "flash_sale_price",

               "allocated_stock", "available_stock", "user_purchase_limit",

               "commission_rate_override", "status", "created_at"], fsi_rows)

    print(f"  flash_sale_items.csv       {len(fsi_rows):>4} rows")



    # ------------------------------------------------------------ vouchers

    voucher_rows = []

    # 6 platform (store_id NULL), 6 store-voucher

    for vid in range(1, 7):

        code = f"PLATFORM{vid:02d}"

        val = random.choice(DISCOUNT_VALUE_FIXED)

        voucher_rows.append([

            vid, code, "",                  # store_id empty = platform

            "FIXED", f"{val:.2f}",

            "100000.00", "50000.00",

            random.randint(500, 5000), 0,

            1,

            now, (datetime.datetime.now() + datetime.timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S"),

            "ACTIVE", now,

        ])

    for vid in range(7, 13):

        code = f"STORE{vid:02d}"

        store_id_pick = random.choice([s[0] for s in store_rows])

        dtype = random.choice(DISCOUNT_TYPE_OPTIONS)

        if dtype == "FIXED":

            val = random.choice(DISCOUNT_VALUE_FIXED)

        else:

            val = random.choice(DISCOUNT_VALUE_PERCENT)

        voucher_rows.append([

            vid, code, store_id_pick,

            dtype, f"{val:.2f}",

            "200000.00", "50000.00",

            random.randint(100, 1000), 0,

            random.choice([1, 1, 2]),

            now, (datetime.datetime.now() + datetime.timedelta(days=14)).strftime("%Y-%m-%d %H:%M:%S"),

            random.choice(VOUCHER_STATUSES), now,

        ])

    write_csv(out / "vouchers.csv",

              ["id", "code", "store_id", "discount_type", "discount_value",

               "min_order_amount", "max_discount_amount", "total_quantity",

               "used_quantity", "user_usage_limit", "start_time", "end_time",

               "status", "created_at"], voucher_rows)

    print(f"  vouchers.csv               {len(voucher_rows):>4} rows")



    # --------------------------------------------------------------- carts

    # 1 cart per user (1-1 with users table, FK user_id UNIQUE)

    cart_rows = []

    for cid, u in enumerate(users, start=1):

        cart_rows.append([cid, u["id"], now, now])

    write_csv(out / "carts.csv",

              ["id", "user_id", "created_at", "updated_at"], cart_rows)

    print(f"  carts.csv                  {len(cart_rows):>4} rows")



    # ---------------------------------------------------- store_followers

    follower_rows = []

    f_id = 1

    used_pairs = set()

    while len(follower_rows) < N_STORE_FOLLOWERS:

        s_id = random.choice([s[0] for s in store_rows])

        u_id = random.choice([u["id"] for u in users if u["_role"] == "buyer"])

        if (s_id, u_id) in used_pairs:

            continue

        used_pairs.add((s_id, u_id))

        follower_rows.append([f_id, s_id, u_id, now])

        f_id += 1

    write_csv(out / "store_followers.csv",

              ["id", "store_id", "user_id", "created_at"], follower_rows)

    print(f"  store_followers.csv        {len(follower_rows):>4} rows")



    # ---------------------------------------------------- product_reviews

    review_rows = []

    rv_id = 1

    for _ in range(N_PRODUCT_REVIEWS):

        # Pick a random buyer and a random product

        buyer = random.choice([u for u in users if u["_role"] == "buyer"])

        product_id = random.randint(1, N_PRODUCTS)

        rating = random.choice(RATING_OPTIONS)

        comment = fake.paragraph(nb_sentences=1)

        # Seller reply cho 100% review (theo yeu cau user: khong de null)
        reply_text = random.choice([
            "Cam on quy khach da danh gia. Shop se co gang phuc vu tot hon!",
            "Rat vui khi quy khach hai long. Mong quy khach ung ho lan sau.",
            "Shop xin loi ve trai nghiem chua tot. Vui long lien he de duoc ho tro.",
            "Cam on quy khach! Don hang tiep theo se co voucher giam gia.",
            "Shop da ghi nhan y kien. Se cai thien chat luong san pham.",
        ])
        reply_at = (datetime.datetime.now() - datetime.timedelta(
            days=random.randint(1, 30))).strftime("%Y-%m-%d %H:%M:%S")

        review_rows.append([

            rv_id, buyer["id"], product_id, rv_id,  # order_item_id = rv_id (placeholder; real app must point to real order_items)

            rating, comment,

            reply_text, reply_at,

            random.choice(REVIEW_STATUSES), now, now,

        ])

        rv_id += 1

    write_csv(out / "product_reviews.csv",

              ["id", "user_id", "product_id", "order_item_id", "rating",

               "comment", "seller_reply", "seller_reply_at",

               "status", "created_at", "updated_at"], review_rows)

    print(f"  product_reviews.csv        {len(review_rows):>4} rows")



    # ---------------------------------------------------------- images

    # Polymorphic image table (V3 schema). 1 image per owner to keep the

    # V3 partial-unique-indexes (1 ACTIVE per USER, 1 ACTIVE per STORE,

    # 1 is_primary=true per PRODUCT|VARIANT) satisfied. Products and

    # variants are linked back to their image at QUERY TIME via:

    #   SELECT * FROM images

    #   WHERE owner_type='PRODUCT' AND owner_id=p.id AND is_primary=true

    # (no FK column on products/product_variants; the V3 model is purely

    #  polymorphic).

    image_rows = []

    img_id = 1



    def add_image(owner_type: str, owner_id: int, is_primary: bool = False):

        nonlocal img_id

        url = f"https://picsum.photos/seed/img{img_id}/600/600"

        public_id = f"flash-sale-b2c/{owner_type.lower()}/{owner_id}_{img_id}"

        image_rows.append([

            img_id, owner_type, owner_id, url, public_id,

            0,                 # display_order

            is_primary,        # is_primary

            False,             # cloudinary_deleted

            "ACTIVE",          # status

            now, now,

        ])

        img_id += 1



    # 1 avatar per user

    for u in users:

        add_image("USER", u["id"], is_primary=False)

    # 1 logo per store

    for s in store_rows:

        add_image("STORE", s[0], is_primary=False)

    # 1 primary image per product

    for pid in range(1, N_PRODUCTS + 1):

        add_image("PRODUCT", pid, is_primary=True)

    # 1 primary image per variant

    for v in variant_rows:

        add_image("VARIANT", v[0], is_primary=True)

    # ~50% of reviews get 1 image each (others have none)

    for r in review_rows:

        if random.random() < N_IMAGES_REVIEW_PCT:

            add_image("REVIEW", r[0], is_primary=False)



    write_csv(out / "images.csv",

              ["id", "owner_type", "owner_id", "url", "cloudinary_public_id",

               "display_order", "is_primary", "cloudinary_deleted", "status",

               "created_at", "updated_at"], image_rows)

    print(f"  images.csv                 {len(image_rows):>4} rows")



    # ---------------------------------------------------------------- done

    total_rows = (

        len(roles_rows) + len(pg_rows) + len(perm_rows) + len(gp_rows)

        + len(cat_rows) + len(banner_rows) + len(user_rows) + len(user_role_rows)

        + len(store_rows) + len(address_rows) + len(product_rows)

        + len(variant_rows) + len(slot_rows) + len(fsi_rows)

        + len(voucher_rows) + len(cart_rows) + len(follower_rows)

        + len(review_rows) + len(image_rows)

    )

    print()

    print(f"Done. {total_rows:,} total rows across 17 CSV files in {out}")

    print("Load into PostgreSQL in FK order (see docs/database-seed-data.md section 6).")

    print("`images.csv` must come AFTER users, stores, products,")

    print("product_variants, and product_reviews so owner_id values resolve.")

    return 0





if __name__ == "__main__":

    sys.exit(main())

