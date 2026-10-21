# 2026-10-18 — Banner image_url: use real online image links

## Scope

Sửa file `script_python/generate_all_seeds.py` để cột `image_url` trong
`banners.csv` dùng **link ảnh thật từ picsum.photos** (1400x400, seed theo
`BannerType`) thay vì đường dẫn local `/banners/xxx.jpg` giả định hiện tại.

## Tham khảo (rules)

- `.cursor/rules/09-database.mdc` — Migration đã áp dụng (`V5__create_banners_table.sql`):
  - `banners.image_url VARCHAR(500) NOT NULL` — link mới phải ≤ 500 ký tự.
  - KHÔNG sửa migration; chỉ sửa script seed (CSV không do schema quản lý).
- `.cursor/rules/33-task-checklist.mdc` (suy ra) — task nhỏ (< 3 file) chạy thẳng.
- `.cursor/rules/30-git-workflow.mdc` — branch từ dev, commit EN, tự commit 1 subplan.

## Cấu trúc hiện tại

`BANNER_DATA` trong `generate_all_seeds.py` (dòng 357–364):

```python
BANNER_DATA = [
    ("HERO",              1, "/banners/hero-1.jpg",   "Flash Sale 12.12 ...", "/flash-sales",  "View now"),
    ("HERO",              2, "/banners/hero-2.jpg",   "Free shipping voucher",...),
    ...
]
```

Mỗi tuple `(btype, position, image_path, alt, cta_href, cta_text)`. Phần tử
thứ 3 là path local giả — cần thay.

## Thay đổi

### 1. `script_python/generate_all_seeds.py` (~5 dòng)

Trong hàm `main()` ở khối `# ------------------------------------------------------------ banners`:

```python
banner_rows = []

for i, (btype, pos, img, alt, href, text) in enumerate(BANNER_DATA, start=1):
    # --- BẮT ĐẦU THAY ĐỔI (fix/banner-image-url-real-links) ---
    # Trước: img là path local "/banners/hero-1.jpg" — giả định, không truy cập được.
    # Sau: dùng picsum.photos với seed theo loại banner (HERO, FREESHIP, ...)
    # + index, kích thước 1400x400 (chuẩn banner ngang).
    # URL dạng: https://picsum.photos/seed/<btype>-<pos>/1400/400
    # Độ dài URL < 60 ký tự, vừa VARCHAR(500).
    slug_seed = f"{btype.lower()}-{pos}"
    real_image_url = f"https://picsum.photos/seed/{slug_seed}/1400/400"
    # --- KẾT THÚC THAY ĐỔI ---

    end = (datetime.datetime.now() + datetime.timedelta(
        days=random.randint(7, 30))).strftime("%Y-%m-%d %H:%M:%S")
    start = now
    banner_rows.append([i, btype, pos, real_image_url, alt, href, text,
                         True, start, end, now])
```

### 2. Giữ nguyên (không sửa)

- `BANNER_DATA` (vẫn giữ `image_path` ở vị trí thứ 3 — không cần xóa, vẫn là
  data tách biệt; chỉ thay biến `img` -> `real_image_url` ở nơi sử dụng).
- `banners.csv` header.
- Toàn bộ phần còn lại của `generate_all_seeds.py`.

## Subplan & model

- 1 subplan nhỏ (1 file Python, < 20 dòng thay đổi) → chạy thẳng, model
  `inherit`. Không cần dispatch subagent (Bước 2).

## Test strategy

### Bắt buộc (TDD — Red → Green)

1. **RED**: Viết `script_python/test_banner_image_url.py` (pytest) verify
   `BANNER_DATA` sau khi sửa phải cho ra `image_url` dạng
   `https://picsum.photos/seed/<type>-<pos>/1400/400`. Nếu chưa sửa code,
   test FAIL (URL hiện tại là `/banners/hero-1.jpg`).
2. **GREEN**: Sửa code theo snippet trên.
3. **REFACTOR**: Check format URL đúng regex.

### Câu lệnh test

```bash
cd D:\flash-sale-b2c-UTC2\script_python
python -m pytest test_banner_image_url.py -v
```

### Smoke check (chạy generator thật)

```bash
python generate_all_seeds.py --out ./tmp_seed_check
# verify banners.csv chứa đúng 8 dòng, image_url là link picsum.
type .\tmp_seed_check\banners.csv
# Cleanup
Remove-Item -Recurse -Force .\tmp_seed_check
```

### Verify (không bắt buộc)

- Tổng số rows: vẫn 8 banner.
- Không làm thay đổi số rows ảnh hưởng đến `images.csv`, `product_reviews.csv`, ...

## Risks

| Risk | Mitigation |
|---|---|
| URL > 500 chars | picsum URL < 60 chars, dư sứa. |
| picsum.photos offline lúc demo | Ghi chú trong README: thumbnail có thể không load nếu offline. |
| Hard-code kích thước 1400x400 | Đây là chuẩn hero ngang; có thể dùng `BannerType.IMAGE_DIMENSIONS` nếu sau này cần. |

## Files changed

| File | Loại | Sửa? |
|---|---|---|
| `script_python/generate_all_seeds.py` | LOGIC (sinh CSV) | ✅ ~5 dòng |
| `script_python/test_banner_image_url.py` | TEST mới | ✅ add |

## Tự commit

- Branch: `fix/banner-image-url-real-links`
- Commit message: `fix(seed): use real online image links for banner image_url`
- Tự push? KHÔNG (theo git-workflow.mdc — chờ user duyệt push).
