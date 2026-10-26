import subprocess
import time
import os
import json
import urllib.request
import statistics
import sys
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

matrix_cells = [
    ("Cell_1_Lua_Legacy", "loadtest-lua", "legacy", ""),
    ("Cell_2_Lua_Optimized", "loadtest-lua", "optimized", ""),
    ("Cell_3_Redisson_Legacy", "loadtest", "legacy", "--flashsale.lock.wait-ms=200"),
    ("Cell_4_Redisson_Optimized", "loadtest", "optimized", "--flashsale.lock.wait-ms=200"),
]

rate = 600
print(f"=== SO SÁNH KHO 1000 CÔNG BẰNG TẠI RATE = {rate} req/s ===")
fair_results = {}

for cell_name, profile, tx_flow, extra_args in matrix_cells:
    start_backend(profile, tx_flow, extra_args)
    print(f"\n--- Running Fair Test for {cell_name} (Rate={rate}) ---")
    
    # Warm-up (loại bỏ)
    runner.run_single_test(f"{cell_name}_rate{rate}_warmup", stock=1000)
    
    runs = []
    for r in range(1, 6):
        run_name = f"{cell_name}_rate{rate}_run{r}"
        
        # Bắt đầu theo dõi CPU JVM và k6
        env_vars = os.environ.copy()
        env_vars["RATE"] = str(rate)
        
        # Đo bằng runner
        m = runner.run_single_test(run_name, stock=1000)
        runs.append(m)
        
    fair_results[cell_name] = runs

with open(f"loadtest/results/fair_comparison_rate{rate}.json", "w", encoding="utf-8") as f:
    json.dump(fair_results, f, indent=2)

print("=== HOÀN TẤT ĐO SO SÁNH CÔNG BẰNG TẠI RATE 600! ===")
stop_backend()
