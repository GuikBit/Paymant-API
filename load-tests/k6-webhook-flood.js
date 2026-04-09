import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('webhook_errors');
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COMPANY_ID = __ENV.COMPANY_ID || '1';
const WEBHOOK_TOKEN = __ENV.WEBHOOK_TOKEN || 'test-token';

export const options = {
    scenarios: {
        // Simulate Asaas webhook burst
        webhook_flood: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 200,
            stages: [
                { duration: '30s', target: 10 },   // normal load
                { duration: '1m', target: 100 },   // burst
                { duration: '2m', target: 100 },   // sustain burst
                { duration: '1m', target: 150 },   // +50% margin
                { duration: '2m', target: 150 },   // sustain
                { duration: '30s', target: 0 },    // ramp down
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],     // webhook ingress must be fast (<500ms)
        http_req_failed: ['rate<0.01'],        // <1% error rate
        webhook_errors: ['rate<0.01'],
    },
};

const eventTypes = [
    'PAYMENT_CREATED', 'PAYMENT_CONFIRMED', 'PAYMENT_RECEIVED',
    'PAYMENT_OVERDUE', 'PAYMENT_DELETED', 'PAYMENT_REFUNDED',
];

export default function () {
    const eventType = eventTypes[Math.floor(Math.random() * eventTypes.length)];
    const paymentId = `pay_k6_${__VU}_${__ITER}_${Date.now()}`;

    const payload = JSON.stringify({
        id: `evt_${paymentId}`,
        event: eventType,
        payment: {
            id: paymentId,
            customer: 'cus_test',
            billingType: 'PIX',
            value: 100.00,
            status: eventType.replace('PAYMENT_', ''),
            dueDate: '2026-05-15',
        },
    });

    const res = http.post(
        `${BASE_URL}/api/v1/webhooks/asaas?companyId=${COMPANY_ID}&access_token=${WEBHOOK_TOKEN}`,
        payload,
        { headers: { 'Content-Type': 'application/json' } }
    );

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    errorRate.add(!success);
}
