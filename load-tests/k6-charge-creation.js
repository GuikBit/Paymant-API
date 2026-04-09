import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('charge_errors');
const chargeDuration = new Trend('charge_creation_duration');

// Test configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';
const COMPANY_ID = __ENV.COMPANY_ID || '1';

export const options = {
    scenarios: {
        // Ramp-up to peak load
        charge_creation: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 10 },   // ramp up to 10 VUs
                { duration: '3m', target: 50 },   // ramp up to 50 VUs (peak)
                { duration: '5m', target: 50 },   // sustain peak
                { duration: '2m', target: 75 },   // +50% margin above peak
                { duration: '3m', target: 75 },   // sustain above peak
                { duration: '1m', target: 0 },    // ramp down
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],    // 95% of requests under 2s
        http_req_failed: ['rate<0.05'],        // error rate below 5%
        charge_errors: ['rate<0.05'],
    },
};

const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${AUTH_TOKEN}`,
    'X-Company-Id': COMPANY_ID,
    'Idempotency-Key': '',
};

export default function () {
    const idempotencyKey = `k6-charge-${__VU}-${__ITER}-${Date.now()}`;
    headers['Idempotency-Key'] = idempotencyKey;

    const payload = JSON.stringify({
        customerId: 1,
        value: (Math.random() * 500 + 10).toFixed(2),
        dueDate: '2026-05-15',
        description: `k6 load test charge - VU ${__VU} Iter ${__ITER}`,
        externalReference: idempotencyKey,
    });

    const startTime = Date.now();
    const res = http.post(`${BASE_URL}/api/v1/charges/pix`, payload, { headers });
    chargeDuration.add(Date.now() - startTime);

    const success = check(res, {
        'status is 201': (r) => r.status === 201,
        'response has id': (r) => r.json('id') !== undefined,
    });

    errorRate.add(!success);
    sleep(Math.random() * 0.5 + 0.1);
}
