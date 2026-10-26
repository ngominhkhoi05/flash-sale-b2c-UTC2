import sys
import os
import json
import time
import subprocess
import threading

sys.path.append('loadtest')
import runner

psql_path = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"

def sample_pg_stat(stop_event, log_file):
    env_pg = os.environ.copy()
    env_pg["PGPASSWORD"] = "root"
    sql = """
    SELECT pid, state, wait_event_type, wait_event, pg_blocking_pids(pid), 
           coalesce(extract(epoch from (now() - xact_start)), 0) as xact_age,
           substr(query, 1, 60)
    FROM pg_stat_activity
    WHERE datname = 'flash_sale_b2c' AND pid <> pg_backend_pid()
    ORDER BY xact_age DESC;
    """
    with open(log_file, "w", encoding="utf-8") as f:
        while not stop_event.is_set():
            res = subprocess.run([psql_path, "-U", "postgres", "-d", "flash_sale_b2c", "-c", sql],
                                 capture_output=True, text=True, env=env_pg)
            f.write(f"--- TIME: {time.time()} ---\n")
            f.write(res.stdout + "\n")
            time.sleep(1)

print("=== BẮT ĐẦU ĐO 5 LẦN CHO LUA LEGACY + REQUIRES_NEW (Kho 100) ===")
results = []
for i in range(1, 6):
    run_name = f"lua_legacy_requires_new_run_{i}"
    stop_event = threading.Event()
    stat_thread = threading.Thread(target=sample_pg_stat, args=(stop_event, f"loadtest/results/{run_name}_pg_stat.log"))
    stat_thread.start()
    
    try:
        m = runner.run_single_test(run_name, stock=100)
        results.append(m)
    finally:
        stop_event.set()
        stat_thread.join()

with open("loadtest/results/lua_legacy_requires_new_5runs.json", "w", encoding="utf-8") as f:
    json.dump(results, f, indent=2)

print("=== HOÀN TẤT 5 LẦN ĐO LUA LEGACY + REQUIRES_NEW ===")
