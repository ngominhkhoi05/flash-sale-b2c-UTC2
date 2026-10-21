"""Test cho load_to_supabase.py: _build_connect_kwargs (RED -> GREEN).

Bước 5.1 của workflow-process.mdc: test đỏ trước khi sửa code.
Bug fix: Supabase pgbouncer 2024+ yeu cau startup packet co
'project=<ref>' (options=-c project=...) + psycopg3 can disable
prepared statements (prepare_threshold=0) de tranh loi
'_pg3_0 does not exist' tren transaction-mode pooler.
"""

import importlib
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import load_to_supabase as lts  # noqa: E402


def _set_url(monkeypatch_module, url: str) -> None:
    monkeypatch_module.CONN_STR = url


def test_build_kwargs_default_pooler_url_has_project_and_no_prepare():
    """Conn string binh thuong (postgresql://user:p@host:port/db) phai:
    - options = '-c project=<ref>' (lay ref tu username 'postgres.<ref>')
    - prepare_threshold = 0 (disable psycopg3 prepared statements)
    """
    # Override CONN_STR gia lap
    original = lts.CONN_STR
    lts.CONN_STR = (
        "postgresql://postgres.dygnssynjofuaaapnvcg:%26zyXVX8TNLWYF%21B"
        "@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require"
    )
    try:
        kwargs = lts._build_connect_kwargs()
        assert kwargs.get("prepare_threshold") == 0, (
            f"prepare_threshold phai = 0 de tranh loi '_pg3_0 does not exist' "
            f"khi COPY qua pgbouncer transaction mode. Hien tai: {kwargs}"
        )
        assert kwargs.get("options") == "-c project=dygnssynjofuaaapnvcg", (
            f"options phai chua '-c project=<ref>' de pgbouncer xac dinh "
            f"tenant. Hien tai: {kwargs.get('options')!r}"
        )
        assert kwargs.get("connect_timeout") == 30
    finally:
        lts.CONN_STR = original


def test_build_kwargs_user_without_dot_has_no_project_option():
    """Username khong co 'postgres.<ref>' (vd 'postgres' don gian) ->
    KHONG set options (tranh psycopg bao loi unknown GUC)."""
    original = lts.CONN_STR
    lts.CONN_STR = "postgresql://postgres:secret@localhost:5432/mydb"
    try:
        kwargs = lts._build_connect_kwargs()
        # Van set prepare_threshold=0 (an toan)
        assert kwargs.get("prepare_threshold") == 0
        # KHONG set options vi khong xac dinh duoc project ref
        assert "options" not in kwargs, (
            f"options KHONG nen co khi username khong co '.<ref>'. "
            f"Hien tai: {kwargs}"
        )
    finally:
        lts.CONN_STR = original


def test_build_kwargs_has_required_keys():
    """Tra ve dict co keys: prepare_threshold, connect_timeout, options (optional)."""
    original = lts.CONN_STR
    lts.CONN_STR = (
        "postgresql://postgres.abc123:pw@host.supabase.com:6543/postgres"
    )
    try:
        kwargs = lts._build_connect_kwargs()
        assert "prepare_threshold" in kwargs
        assert "connect_timeout" in kwargs
        assert kwargs["connect_timeout"] > 0
    finally:
        lts.CONN_STR = original
