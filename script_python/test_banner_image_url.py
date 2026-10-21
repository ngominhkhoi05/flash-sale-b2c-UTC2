"""Test cho BANNER_DATA image_url trong generate_all_seeds.py.

Muc tieu (fix/banner-image-url-real-links, 2026-10-18):
- Banner image_url phai la link picsum.photos that, KHONG con path local
  dang "/banners/...". Truoc khi sua code, cac test nay se FAIL vi
  BANNER_DATA van chua "/banners/hero-1.jpg"...
- Sau khi sua code (hàm main() ở khối # banners dung `real_image_url`
  thay vi `img`), cac test PASS.

Chay test:
    cd script_python
    python -m pytest test_banner_image_url.py -v
"""

import importlib
import re
import sys
from pathlib import Path


HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import generate_all_seeds as gas  # noqa: E402


# ----- Constants -----------------------------------------------------------

EXPECTED_RE_PATTERN = re.compile(
    r"^https://picsum\.photos/seed/[a-z0-9-]+/1400/400$"
)


# ----- Tests --------------------------------------------------------------


def test_banner_data_has_eight_rows():
    """BANNER_DATA phai co dung 8 banner (theo N_BANNERS)."""
    assert len(gas.BANNER_DATA) == gas.N_BANNERS == 8


def test_banner_data_image_url_format_in_generated_csv(tmp_path):
    """Khi chay generator, banners.csv phai co image_url la link picsum.

    Day la test integration (RED -> GREEN): tao ra output thuc su
    trong tmp_path, doc lai, kiem tra tung row.
    """
    out_dir = tmp_path / "seed_out"
    rc = gas.main_with_output(out_dir)

    # main() return 0 va thong bao "Done"
    assert rc == 0

    csv_path = out_dir / "banners.csv"
    assert csv_path.exists(), "banners.csv phai ton tai sau khi chay generator"

    rows = csv_path.read_text(encoding="utf-8").strip().splitlines()
    header = rows[0].split(",")
    assert "image_url" in header

    image_url_idx = header.index("image_url")
    type_idx = header.index("type")
    position_idx = header.index("position")

    banner_rows = rows[1:]
    assert len(banner_rows) == gas.N_BANNERS

    for line in banner_rows:
        cells = line.split(",")
        image_url = cells[image_url_idx]
        btype = cells[type_idx]
        pos = cells[position_idx]

        assert EXPECTED_RE_PATTERN.match(image_url), (
            f"image_url '{image_url}' (type={btype}, pos={pos}) "
            f"khong phai link picsum hop le."
        )

        # Seed phai chua type (lower, '_' -> '-') + position, de moi banner
        # co 1 anh rieng (re-visit cung seed -> cung anh).
        expected_seed = f"{btype.lower().replace('_', '-')}-{pos}"
        assert expected_seed in image_url, (
            f"Seed '{expected_seed}' (type+pos) phai co trong URL: {image_url}"
        )


def test_no_local_path_in_csv(tmp_path):
    """Khong con dong nao trong banners.csv co '/banners/' (path local)."""
    out_dir = tmp_path / "seed_out"
    gas.main_with_output(out_dir)

    text = (out_dir / "banners.csv").read_text(encoding="utf-8")
    assert "/banners/" not in text, (
        "banners.csv van con path local (vi du '/banners/hero-1.jpg'). "
        f"Noi dung:\n{text}"
    )
