import subprocess
import time
import os
import json
import urllib.request
import sys
sys.path.append('loadtest')
import runner

psql_path = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"

def stop_backend():
    print("Stopping any running Java processes...")
    subprocess.run("powershell -Command \"Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue\"", shell=True)
    time.sleep(2)

def start_backend(profile, tx_flow="legacy", extra_args=""):
    stop_backend()
    cmd = f".\\gradlew.bat bootRun --args=\"--spring.profiles.active={profile} --flashsale.tx-flow={tx_flow} {extra_args}\""
    print(f"Starting backend: {cmd}")
    subprocess.Popen(cmd, shell=True)
    
    print("Waiting for health check...")
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

matrix_configs = [
    # (Cell Name, profile, tx_flow, extra_args)
    ("Cell_1_Lua_Legacy", "loadtest-lua", "legacy", ""),
    ("Cell_2_Lua_Optimized", "loadtest-lua", "optimized", ""),
    ("Cell_3_Redisson_Legacy", "loadtest", "legacy", "--flashsale.lock.wait-ms=200"),
    ("Cell_4_Redisson_Optimized", "loadtest", "optimized", "--flashsale.lock.wait-ms=200"),
]

all_results = {}

for cell_name, profile, tx_flow, extra_args in matrix_configs:
    print(f"\n=======================================================")
    print(f"=== TESTING {cell_name} (profile={profile}, tx_flow={tx_flow}) ===")
    print(f"=======================================================")
    
    start_backend(profile, tx_flow, extra_args)
    
    for stock in [100, 1000]:
        print(f"\n--- Testing Stock = {stock} ---")
        # 1 warm-up (loại bỏ)
        print("Warm-up run...")
        runner.run_single_test(f"{cell_name}_stock{stock}_warmup", stock=stock)
        
        # 5 lần đo chính thức
        cell_runs = []
        for r in range(1, 6):
            run_id = f"{cell_name}_stock{stock}_run{r}"
            print(f"Measurement run {r}/5: {run_id}")
            m = runner.run_single_test(run_id, stock=stock)
            cell_runs.append(m)
            
        all_results[f"{cell_name}_stock{stock}"] = cell_runs

    # Chạy Kịch bản B (50 users x limit 3, kho 1000)
    print(f"\n--- Testing Scenario B (Limit check, 50 users, limit=3, stock=1000) ---")
    b_warmup = runner.run_single_test(f"{cell_name}_scenarioB_warmup", stock=1000, scenario_mode="B")
    b_run = runner.run_single_test(f"{cell_name}_scenarioB_run1", stock=1000, scenario_mode="B")
    all_results[f"{cell_name}_scenarioB"] = b_run

with open("loadtest/results/matrix_2x2_results.json", "w", encoding="utf-8") as f:
    json.dump(all_results, f, indent=2)

print("\n=== ALL MATRIX RUNS COMPLETED SUCCESSFULLY! ===")
stop_backend()
