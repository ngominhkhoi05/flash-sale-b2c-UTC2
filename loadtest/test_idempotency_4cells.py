import subprocess
import time
import os
import json
import urllib.request
import concurrent.futures

psql_path = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"
redis_cli = r"C:\Program Files\Redis\redis-cli.exe"

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

with open('loadtest/tokens.json', 'r', encoding='utf-8') as f:
    users = json.load(f)

user = users[1]
token = user['token']
address_id = user['addressId']
url = 'http://localhost:8080/api/v1/flash-sales/reserve'

def send_reserve_req(key, user_token=token, addr_id=address_id):
    headers = {
        'Content-Type': 'application/json',
        'Authorization': f'Bearer {user_token}',
        'Idempotency-Key': key
    }
    data = json.dumps({
        'flashSaleItemId': 1,
        'quantity': 1,
        'addressId': addr_id
    }).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read().decode('utf-8')
            return resp.status, body
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        return e.code, body

def get_db_order_count():
    env_pg = os.environ.copy()
    env_pg["PGPASSWORD"] = "root"
    res = subprocess.run([psql_path, "-U", "postgres", "-d", "flash_sale_b2c", "-t", "-A", "-c", "SELECT count(*) FROM orders;"], capture_output=True, text=True, env=env_pg)
    return int(res.stdout.strip()) if res.stdout.strip() else 0

def reset(stock=1000):
    subprocess.run(f"powershell -ExecutionPolicy Bypass -File loadtest/reset.ps1 -Stock {stock} -ItemId 1", shell=True, capture_output=True)

matrix_cells = [
    ("Cell_1_Lua_Legacy", "loadtest-lua", "legacy", ""),
    ("Cell_2_Lua_Optimized", "loadtest-lua", "optimized", ""),
    ("Cell_3_Redisson_Legacy", "loadtest", "legacy", "--flashsale.lock.wait-ms=200"),
    ("Cell_4_Redisson_Optimized", "loadtest", "optimized", "--flashsale.lock.wait-ms=200"),
]

idempotency_table = {}

for cell_name, profile, tx_flow, extra_args in matrix_cells:
    print(f"\n=======================================================")
    print(f"Testing Idempotency for {cell_name}")
    print(f"=======================================================")
    start_backend(profile, tx_flow, extra_args)
    
    cell_results = {}
    
    # --- Ca 1: Replay thông thường ---
    reset(1000)
    cnt_before_c1 = get_db_order_count()
    key_c1 = f"idem-key-c1-{cell_name}"
    s1, b1 = send_reserve_req(key_c1)
    d1 = json.loads(b1) if s1 in [200, 201] else {}
    order_code_1 = d1.get("orderCode") or d1.get("reservationId")
    
    # Replay lại
    s2, b2 = send_reserve_req(key_c1)
    d2 = json.loads(b2) if s2 in [200, 201] else {}
    order_code_2 = d2.get("orderCode") or d2.get("reservationId")
    cnt_after_c1 = get_db_order_count()
    
    cell_results["case_1_replay_normal"] = {
        "req1_status": s1,
        "req2_status": s2,
        "order_code_1": order_code_1,
        "order_code_2": order_code_2,
        "is_same_order": (order_code_1 == order_code_2 and order_code_1 is not None),
        "db_orders_created": cnt_after_c1 - cnt_before_c1
    }
    print(f"Ca 1: Req1={s1}, Req2={s2}, SameOrder={order_code_1 == order_code_2}, DB_Orders={cnt_after_c1 - cnt_before_c1}")

    # --- Ca 2: Replay sau khi user đã đạt limit ---
    reset(1000)
    # User mua limit = 10 lần (đầy limit)
    limit_keys = [f"limit-key-{cell_name}-{i}" for i in range(10)]
    first_order_code = None
    for i, lk in enumerate(limit_keys):
        st, bd = send_reserve_req(lk)
        if i == 0 and st in [200, 201]:
            first_order_code = (json.loads(bd).get("orderCode") or json.loads(bd).get("reservationId"))
    
    # Thử request thứ 11 với key mới -> phải 409 limit
    s_over, b_over = send_reserve_req(f"limit-key-{cell_name}-11")
    cnt_at_limit = get_db_order_count()
    
    # Giờ replay lại key đầu tiên limit_keys[0] khi user đã đạt limit
    s_rep_limit, b_rep_limit = send_reserve_req(limit_keys[0])
    d_rep = json.loads(b_rep_limit) if s_rep_limit in [200, 201] else {}
    rep_order_code = d_rep.get("orderCode") or d_rep.get("reservationId")
    cnt_after_rep_limit = get_db_order_count()
    
    cell_results["case_2_replay_after_limit"] = {
        "over_limit_status": s_over,
        "replay_status": s_rep_limit,
        "first_order_code": first_order_code,
        "replay_order_code": rep_order_code,
        "is_same_order": (first_order_code == rep_order_code and first_order_code is not None),
        "db_orders_added_on_replay": cnt_after_rep_limit - cnt_at_limit
    }
    print(f"Ca 2: OverLimit={s_over}, ReplayStatus={s_rep_limit}, SameOrder={first_order_code == rep_order_code}, AddedDBOrders={cnt_after_rep_limit - cnt_at_limit}")

    # --- Ca 3: Hai request cùng key gửi song song (concurrent) ---
    reset(1000)
    cnt_before_c3 = get_db_order_count()
    parallel_key = f"parallel-key-{cell_name}"
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(send_reserve_req, parallel_key) for _ in range(2)]
        results_c3 = [f.result() for f in futures]
    
    cnt_after_c3 = get_db_order_count()
    st_list = [r[0] for r in results_c3]
    bd_list = [r[1] for r in results_c3]
    
    conflict_detected = any("IDEMPOTENCY_CONFLICT" in b or s == 409 for s, b in results_c3)
    success_count = sum(1 for s in st_list if s in [200, 201])
    
    cell_results["case_3_concurrent_same_key"] = {
        "statuses": st_list,
        "success_count": success_count,
        "conflict_detected": conflict_detected,
        "db_orders_created": cnt_after_c3 - cnt_before_c3
    }
    print(f"Ca 3: Statuses={st_list}, SuccessCount={success_count}, ConflictDetected={conflict_detected}, DB_OrdersCreated={cnt_after_c3 - cnt_before_c3}")
    
    idempotency_table[cell_name] = cell_results

with open("loadtest/results/idempotency_table_4cells.json", "w", encoding="utf-8") as f:
    json.dump(idempotency_table, f, indent=2)

print("\n=== HOÀN TẤT ĐO IDEMPOTENCY CẢ 4 Ô ===")
stop_backend()
