import subprocess
import time
import os
import json
import urllib.request
import re

psql_path = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"

def stop_backend():
    subprocess.run("powershell -Command \"Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue\"", shell=True)
    time.sleep(2)

def start_backend_and_log(profile, tx_flow="optimized", extra_args="--flashsale.lock.wait-ms=200", log_file="backend_lock_profile.log"):
    stop_backend()
    cmd = f".\\gradlew.bat bootRun --args=\"--spring.profiles.active={profile} --flashsale.tx-flow={tx_flow} {extra_args}\""
    print(f"Starting backend with log redirect: {cmd}")
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

print("=== STARTING REDISSON PROFILE BENCHMARK ===")
proc, log_f = start_backend_and_log("loadtest", "optimized", "--flashsale.lock.wait-ms=200", "loadtest/results/redisson_profile.log")

# Reset và chạy k6 với stock 1000
subprocess.run("powershell -ExecutionPolicy Bypass -File loadtest/reset.ps1 -Stock 1000 -ItemId 1", shell=True)
env_vars = os.environ.copy()
env_vars["ITEM_ID"] = "1"
subprocess.run("k6 run loadtest/purchase.js", shell=True, env=env_vars)

time.sleep(2)
stop_backend()
log_f.close()

# Phân tích log [LOCK_PROFILE]
content = open("loadtest/results/redisson_profile.log", "r", encoding="utf-8", errors="ignore").read()
lock_times = [int(m) for m in re.findall(r'\[LOCK_PROFILE\] itemId=\d+ lock_held_ms=(\d+)', content)]

if lock_times:
    avg_hold = sum(lock_times) / len(lock_times)
    min_hold = min(lock_times)
    max_hold = max(lock_times)
    print(f"Total lock acquisitions: {len(lock_times)}")
    print(f"Average lock held time: {avg_hold:.2f} ms (Min: {min_hold} ms, Max: {max_hold} ms)")
    
    # Tính lý thuyết throughput: 1000ms / avg_hold
    theoretical_tput = 1000.0 / avg_hold if avg_hold > 0 else 0
    print(f"Theoretical serialized throughput limit: {theoretical_tput:.2f} ops/sec")
    
    with open("loadtest/results/redisson_hold_times.json", "w", encoding="utf-8") as out_j:
        json.dump({
            "count": len(lock_times),
            "avg_ms": avg_hold,
            "min_ms": min_hold,
            "max_ms": max_hold,
            "theoretical_tput": theoretical_tput,
            "samples": lock_times[:50]
        }, out_j, indent=2)
else:
    print("No LOCK_PROFILE entries found!")
