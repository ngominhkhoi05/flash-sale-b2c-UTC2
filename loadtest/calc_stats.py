import json
import statistics

data = json.load(open('loadtest/results/matrix_2x2_results.json', encoding='utf-8'))

def format_cell(runs):
    # runs is list of 5 dicts
    counts_200 = [r['count_200'] for r in runs]
    counts_409s = [r['count_409_stock'] for r in runs]
    counts_409l = [r['count_409_limit'] for r in runs]
    counts_429 = [r['count_429'] for r in runs]
    counts_5xx = [r['count_5xx'] for r in runs]
    timeouts = [r['client_timeout'] for r in runs]
    dropped = [r['dropped_iterations'] for r in runs]
    total_sums = [r['total_sum'] for r in runs]
    db_orders = [r['db_orders'] for r in runs]
    db_stocks = [r['db_stock'] for r in runs]
    redis_stocks = [r['redis_stock'] for r in runs]
    p95_200s = [r['p95_200'] for r in runs]
    p95_429s = [r['p95_429'] for r in runs]
    tputs = [r['throughput'] for r in runs]
    
    def mmm(arr):
        med = statistics.median(arr)
        mn = min(arr)
        mx = max(arr)
        if mn == mx:
            return f"{mn}"
        return f"{med} ({mn}–{mx})"

    return {
        "200": mmm(counts_200),
        "409s": mmm(counts_409s),
        "409l": mmm(counts_409l),
        "429": mmm(counts_429),
        "5xx": mmm(counts_5xx),
        "timeout": mmm(timeouts),
        "dropped": mmm(dropped),
        "total": mmm(total_sums),
        "db_orders": mmm(db_orders),
        "db_stock": mmm(db_stocks),
        "redis_stock": mmm(redis_stocks),
        "p95_200": mmm(p95_200s),
        "p95_429": mmm(p95_429s),
        "tput": mmm(tputs),
    }

print("=== TỔNG HỢP MATRIX 2x2 (Median, Min–Max) ===")
summary = {}
for k, v in data.items():
    if isinstance(v, list):
        summary[k] = format_cell(v)
    else:
        summary[k] = v

with open('loadtest/results/summary_table.json', 'w', encoding='utf-8') as f:
    json.dump(summary, f, indent=2)

for k, s in summary.items():
    if "scenarioB" not in k:
        print(f"\n[{k}]")
        for col, val in s.items():
            print(f"  {col:12}: {val}")
    else:
        print(f"\n[{k}] (Scenario B)")
        print(f"  200={s['count_200']} | 409l={s['count_409_limit']} | 429={s['count_429']} | DB_orders={s['db_orders']} | Redis_stock={s['redis_stock']} | DB_stock={s['db_stock']}")
