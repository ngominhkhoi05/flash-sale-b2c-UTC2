import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// Metrics counters
export const count200 = new Counter('count_200_success');
export const count409OutOfStock = new Counter('count_409_out_of_stock');
export const count409LimitExceeded = new Counter('count_409_limit_exceeded');
export const count429LockTimeout = new Counter('count_429_lock_timeout');
export const count401Or403 = new Counter('count_401_403_auth_error');
export const count5xx = new Counter('count_5xx_server_error');
export const countClientTimeout = new Counter('count_client_timeout');
export const countOther = new Counter('count_other');

// Trends
export const trend200Duration = new Trend('duration_200_success');
export const trend429Duration = new Trend('duration_429_lock_timeout');

const tokens = new SharedArray('users', function () {
    return JSON.parse(open('./tokens.json'));
});

export const options = {
    scenarios: {
        flash_sale_purchase: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.RATE || 1000),
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 500,
            maxVUs: 2000,
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ITEM_ID = Number(__ENV.ITEM_ID || '1');
// SCENARIO_MODE: 'A' (1 user per request), 'B' (50 users looping), 'IDEMPOTENT_TEST' (replaying same idempotency key)
const SCENARIO_MODE = __ENV.SCENARIO_MODE || 'A';

function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

export default function () {
    const globalIter = exec.scenario.iterationInTest;
    let userIndex;
    let idemKey;

    if (SCENARIO_MODE === 'B') {
        userIndex = globalIter % 50; // Chỉ 50 user bắn 10.000 requests
        idemKey = uuidv4();
    } else if (SCENARIO_MODE === 'IDEMPOTENT_TEST') {
        userIndex = globalIter % 50;
        // Mỗi user dùng cố định 1 idempotency key để test replay
        idemKey = 'fixed-idem-key-user-' + userIndex;
    } else {
        userIndex = globalIter % tokens.length;
        idemKey = uuidv4();
    }

    const user = tokens[userIndex];
    const url = `${BASE_URL}/api/v1/flash-sales/reserve`;

    const payload = JSON.stringify({
        flashSaleItemId: ITEM_ID,
        quantity: 1,
        addressId: user.addressId,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${user.token}`,
            'Idempotency-Key': idemKey,
        },
        timeout: '5s',
    };

    const res = http.post(url, payload, params);

    if (res.status === 200 || res.status === 201) {
        count200.add(1);
        trend200Duration.add(res.timings.duration);
    } else if (res.status === 409) {
        const bodyStr = res.body || '';
        if (bodyStr.includes('FS_409_OUT_OF_STOCK') || bodyStr.includes('hết hàng') || bodyStr.includes('OUT_OF_STOCK')) {
            count409OutOfStock.add(1);
        } else if (bodyStr.includes('FS_409_PURCHASE_LIMIT_EXCEEDED') || bodyStr.includes('giới hạn') || bodyStr.includes('PURCHASE_LIMIT_EXCEEDED')) {
            count409LimitExceeded.add(1);
        } else {
            count409OutOfStock.add(1);
        }
    } else if (res.status === 429) {
        count429LockTimeout.add(1);
        trend429Duration.add(res.timings.duration);
    } else if (res.status === 401 || res.status === 403) {
        count401Or403.add(1);
    } else if (res.status >= 500 && res.status < 600) {
        count5xx.add(1);
    } else if (res.status === 0 || res.error_code === 1050 || (res.error && res.error.includes('timeout'))) {
        countClientTimeout.add(1);
    } else {
        countOther.add(1);
    }

    check(res, {
        'status is valid': (r) => r.status === 200 || r.status === 201 || r.status === 409 || r.status === 429,
    });
}
