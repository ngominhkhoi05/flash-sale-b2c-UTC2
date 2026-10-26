import sys
import os
import json
sys.path.append('loadtest')
import runner

print("=== BẮT ĐẦU ĐO 5 LẦN CHO LUA LEGACY + REQUIRED (Kho 100) ===")
results = []
for i in range(1, 6):
    run_name = f"lua_legacy_required_run_{i}"
    m = runner.run_single_test(run_name, stock=100)
    results.append(m)

with open("loadtest/results/lua_legacy_required_5runs.json", "w", encoding="utf-8") as f:
    json.dump(results, f, indent=2)

print("=== HOÀN TẤT 5 LẦN ĐO LUA LEGACY + REQUIRED ===")
