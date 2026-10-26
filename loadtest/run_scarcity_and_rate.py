import subprocess
import time
import os
import json
import urllib.request
import statistics
import sys
import threading
sys.path.append('loadtest')
import runner

psql_path = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"

def stop_backend():
    subprocess.run("powershell -Command \"Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue\"", shell=True)
    time.sleep(2)

def start_backend(profile, tx_flow="legacy", extra_args=""):
    stop_backend()
    cmd = f".\\gradlew.bat bootRun --args=\"--spring.profiles.active={profile} --flashsale.tx-flow={tx_flow} {extra_args}\""
    print(f"Starting backend: {cmd}")
    subprocess.Popen(cmd, shell=True)
    for _ in range(60):
        try:
            with urllib.request.urlopen("http://localhost:8080/actuator/health", timeout=2) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                if data.get("status") == "UP":
                    print("Backend is UP!")
                    return
        except Exception:
            time.sleep(1)
    raise Exception("Backend failed to start within 60s")

# 1. KỊCH BẢN SẮP HẾT HÀNG (Kho 10 và Kho 1, 1000 req/s, 3 lần mỗi cấu hình cho Lua Opt và Redisson Opt)
scarcity_configs = [
    ("Lua_Optimized", "loadtest-lua", "optimized", ""),
    ("Redisson_Optimized", "loadtest", "optimized", "--flashsale.lock.wait-ms=200")
]

scarcity_results = {}

for cfg_name, profile, tx_flow, extra_args in scarcity_configs:
    start_backend(profile, tx_flow, extra_args)
    for stock in [10, 1]:
        print(f"\n--- Testing Scarcity: {cfg_name} with Stock = {stock} ---")
        runs = []
        for r in range(1, 4):
            run_name = f"scarcity_{cfg_name}_stock{stock}_run{r}"
            # Chạy k6 rate 1000
            m = runner.run_single_test(run_name, stock=stock)
            
            # Kiểm tra sau 60s
            time.sleep(2) # Đợi đồng bộ
            # Query số lượng người mua duy nhất
            env_pg = os.environ.copy()
            env_pg["PGPASSWORD"] = "root"
            sql_users = "SELECT count(DISTINCT buyer_id) FROM orders o JOIN order_items oi ON o.id = oi.order_id WHERE oi.flash_sale_item_id = 1;"
            res_u = subprocess.run([psql_path, "-U", "postgres", "-d", "flash_sale_b2c", "-t", "-A", "-c", sql_users], capture_output=True, text=True, env=env_pg)
            distinct_buyers = int(res_u.stdout.strip()) if res_u.stdout.strip() else 0
            m["distinct_buyers"] = distinct_buyers
            runs.append(m)
        scarcity_results[f"{cfg_name}_stock{stock}"] = runs

with open("loadtest/results/scarcity_results.json", "w", encoding="utf-8") as f:
    json.dump(scarcity_results, f, indent=2)

print("=== SCARCITY TESTS COMPLETED! ===")
stop_backend()
