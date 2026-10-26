import subprocess
import time
import os
import json
import urllib.request
import statistics
import re
import sys
sys.path.append('loadtest')
import runner

psql_path = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"

def stop_backend():
    subprocess.run("powershell -Command \"Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue\"", shell=True)
    time.sleep(2)

def start_backend_and_log(profile, tx_flow, extra_args, log_file):
    stop_backend()
    cmd = f".\\gradlew.bat bootRun --args=\"--spring.profiles.active={profile} --flashsale.tx-flow={tx_flow} {extra_args}\""
    print(f"Starting backend: {cmd}")
    f = open(log_file, "w", encoding="utf-8")
    proc = subprocess.Popen(cmd, shell=True, stdout=f, stderr=subprocess.STDOUT)
    for _ in range(60):
        try:
            with urllib.request.urlopen("http://localhost:8080/actuator/health", timeout=2) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                if data.get("status") == "UP":
                    print("Backend is UP!")
                    return proc, f
        except Exception:
            time.sleep(1)
    raise Exception("Backend failed to start within 60s")

def parse_lock_profile(log_file):
    content = open(log_file, "r", encoding="utf-8", errors="ignore").read()
    # Pattern: [LOCK_PROFILE] itemId=1 acquired_nanos=123 released_nanos=456 hold_nanos=333
    lock_matches = re.findall(r'\[LOCK_PROFILE\] itemId=\d+ acquired_nanos=(\d+) released_nanos=(\d+) hold_nanos=(\d+)', content)
    # Pattern: [UNLOCK_PROFILE] itemId=1 unlock_nanos=123
    unlock_matches = re.findall(r'\[UNLOCK_PROFILE\] itemId=\d+ unlock_nanos=(\d+)', content)
    
    holds_ms = []
    gaps_ms = []
    prev_release = None
    
    for acq_s, rel_s, hold_s in lock_matches:
        acq = int(acq_s)
        rel = int(rel_s)
        hold_ns = int(hold_s)
        holds_ms.append(hold_ns / 1_000_000.0)
        if prev_release is not None:
            gap_ns = acq - prev_release
            # gap có thể âm nếu concurrent threads log chéo nhau, nhưng lock tuần tự trên itemId=1
            gaps_ms.append(max(0.0, gap_ns / 1_000_000.0))
        prev_release = rel
        
    unlocks_ms = [int(u) / 1_000_000.0 for u in unlock_matches]
    
    def stats(arr):
        if not arr: return {"avg": 0, "med": 0, "p95": 0, "max": 0, "min": 0}
        s_arr = sorted(arr)
        p95_idx = int(len(s_arr) * 0.95)
        return {
            "count": len(arr),
            "avg": round(sum(arr) / len(arr), 2),
            "med": round(statistics.median(arr), 2),
            "p95": round(s_arr[min(p95_idx, len(s_arr)-1)], 2),
            "max": round(max(arr), 2),
            "min": round(min(arr), 2)
        }
        
    return {
        "hold_time": stats(holds_ms),
        "gap_time": stats(gaps_ms),
        "unlock_time": stats(unlocks_ms)
    }

configs = [
    ("Redisson_Legacy", "legacy"),
    ("Redisson_Optimized", "optimized")
]

profile_results = {}

for name, tx_flow in configs:
    print(f"\n=======================================================")
    print(f"=== MEASURING LOCK PROFILING FOR {name} (tx_flow={tx_flow}) ===")
    print(f"=======================================================")
    
    log_file = f"loadtest/results/{name}_lock_runs.log"
    proc, log_f = start_backend_and_log("loadtest", tx_flow, "--flashsale.lock.wait-ms=200", log_file)
    
    # 1 Warmup
    print("Warm-up run...")
    runner.run_single_test(f"{name}_lock_warmup", stock=1000)
    
    # 3 lần đo
    runs_data = []
    for r in range(1, 4):
        run_name = f"{name}_lock_meas_run{r}"
        print(f"Measurement run {r}/3: {run_name}")
        m = runner.run_single_test(run_name, stock=1000)
        runs_data.append(m)
        
    time.sleep(2)
    stop_backend()
    log_f.close()
    
    profile_data = parse_lock_profile(log_file)
    profile_results[name] = {
        "k6_runs": runs_data,
        "lock_profile": profile_data
    }
    print(f"\n[{name} Lock Profile Summary]")
    print("Hold time (ms):", profile_data["hold_time"])
    print("Gap time (ms):", profile_data["gap_time"])
    print("Unlock command time (ms):", profile_data["unlock_time"])

with open("loadtest/results/redisson_detailed_lock_comparison.json", "w", encoding="utf-8") as f:
    json.dump(profile_results, f, indent=2)

print("=== HOÀN TẤT ĐO LOCK PROFILING CẢ 2 LUỒNG! ===")
