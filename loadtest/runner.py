import subprocess
import time
import json
import re
import os
import urllib.request

psql_path = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"
redis_cli = r"C:\Program Files\Redis\redis-cli.exe"

def run_cmd(cmd, env=None):
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True, env=env)
    return res.stdout, res.stderr, res.returncode

def query_psql(sql):
    env_pg = os.environ.copy()
    env_pg["PGPASSWORD"] = "root"
    res = subprocess.run([psql_path, "-U", "postgres", "-d", "flash_sale_b2c", "-t", "-A", "-c", sql],
                         capture_output=True, text=True, env=env_pg)
    return res.stdout.strip()

def query_redis(args):
    cmd = [redis_cli] + args
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

def parse_k6_output(output):
    metrics = {
        "count_200": 0,
        "count_409_stock": 0,
        "count_409_limit": 0,
        "count_429": 0,
        "count_401_403": 0,
        "count_5xx": 0,
        "client_timeout": 0,
        "count_other": 0,
        "dropped_iterations": 0,
        "p95_200": 0.0,
        "p95_429": 0.0
    }
    
    m = re.search(r'count_200_success[\.\s]+:\s+(\d+)', output)
    if m: metrics["count_200"] = int(m.group(1))
    
    m = re.search(r'count_409_out_of_stock[\.\s]+:\s+(\d+)', output)
    if m: metrics["count_409_stock"] = int(m.group(1))

    m = re.search(r'count_409_limit_exceeded[\.\s]+:\s+(\d+)', output)
    if m: metrics["count_409_limit"] = int(m.group(1))

    m = re.search(r'count_429_lock_timeout[\.\s]+:\s+(\d+)', output)
    if m: metrics["count_429"] = int(m.group(1))

    m = re.search(r'count_401_403_auth_error[\.\s]+:\s+(\d+)', output)
    if m: metrics["count_401_403"] = int(m.group(1))

    m = re.search(r'count_5xx_server_error[\.\s]+:\s+(\d+)', output)
    if m: metrics["count_5xx"] = int(m.group(1))

    m = re.search(r'count_client_timeout[\.\s]+:\s+(\d+)', output)
    if m: metrics["client_timeout"] = int(m.group(1))

    m = re.search(r'count_other[\.\s]+:\s+(\d+)', output)
    if m: metrics["count_other"] = int(m.group(1))

    m = re.search(r'dropped_iterations[\.\s]+:\s+(\d+)', output)
    if m: metrics["dropped_iterations"] = int(m.group(1))

    m = re.search(r'duration_200_success[\.\s]+:.*p\(95\)=([0-9\.]+)', output)
    if m: metrics["p95_200"] = round(float(m.group(1)), 2)

    m = re.search(r'duration_429_lock_timeout[\.\s]+:.*p\(95\)=([0-9\.]+)', output)
    if m: metrics["p95_429"] = round(float(m.group(1)), 2)

    return metrics

def run_single_test(run_name, stock=100, item_id=1, scenario_mode='A', rate=None):
    # 1. Reset
    out, err, code = run_cmd(f"powershell -ExecutionPolicy Bypass -File loadtest/reset.ps1 -Stock {stock} -ItemId {item_id}")
    if code != 0:
        raise Exception(f"Reset failed: {err} {out}")

    # 2. Run k6
    env_vars = os.environ.copy()
    env_vars["ITEM_ID"] = str(item_id)
    env_vars["SCENARIO_MODE"] = scenario_mode
    if rate is not None:
        env_vars["RATE"] = str(rate)
    
    start_time = time.time()
    res = subprocess.run("k6 run loadtest/purchase.js", shell=True, capture_output=True, text=True, env=env_vars)
    k6_out = res.stdout + "\n" + res.stderr
    
    # Save k6 raw output
    with open(f"loadtest/results/{run_name}.log", "w", encoding="utf-8") as f:
        f.write(k6_out)

    metrics = parse_k6_output(k6_out)
    
    # 3. Wait up to 60s (or check DB immediately and at 60s)
    sql = f"SELECT count(*), coalesce(extract(epoch from (max(created_at) - min(created_at))), 0) FROM orders o JOIN order_items oi ON o.id = oi.order_id WHERE oi.flash_sale_item_id = {item_id};"
    db_out = query_psql(sql)
    db_parts = db_out.split('|')
    db_orders = int(db_parts[0]) if db_parts and db_parts[0] else 0
    span_sec = float(db_parts[1]) if len(db_parts) > 1 and db_parts[1] else 0.0
    throughput = round(db_orders / span_sec, 2) if span_sec > 0 else 0.0

    # DB Available stock
    db_stock_out = query_psql(f"SELECT available_stock FROM flash_sale_items WHERE id = {item_id};")
    db_stock = int(db_stock_out) if db_stock_out else 0

    # Redis Available stock
    redis_stock_out = query_redis(["GET", f"flash_sale:stock:{item_id}"])
    redis_stock = int(redis_stock_out) if redis_stock_out else 0

    metrics["run_name"] = run_name
    metrics["db_orders"] = db_orders
    metrics["db_stock"] = db_stock
    metrics["redis_stock"] = redis_stock
    metrics["throughput"] = throughput
    metrics["span_sec"] = span_sec

    total = (metrics["count_200"] + metrics["count_409_stock"] + metrics["count_409_limit"] +
             metrics["count_429"] + metrics["count_401_403"] + metrics["count_5xx"] +
             metrics["client_timeout"] + metrics["count_other"] + metrics["dropped_iterations"])
    metrics["total_sum"] = total

    print(f"[{run_name}] 200={metrics['count_200']} | 409s={metrics['count_409_stock']} | 409l={metrics['count_409_limit']} | 429={metrics['count_429']} | 5xx={metrics['count_5xx']} | timeout={metrics['client_timeout']} | dropped={metrics['dropped_iterations']} | Total={total} | DB_orders={db_orders} | DB_stock={db_stock} | Redis_stock={redis_stock} | p95_200={metrics['p95_200']}ms | p95_429={metrics['p95_429']}ms | Tput={throughput}")
    
    return metrics
