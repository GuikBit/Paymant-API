import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('report_errors');
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';
const COMPANY_ID = __ENV.COMPANY_ID || '1';

export const options = {
    scenarios: {
        report_queries: {
            executor: 'constant-vus',
            vus: 20,
            duration: '5m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'],
        http_req_failed: ['rate<0.05'],
    },
};

const headers = {
    'Authorization': `Bearer ${AUTH_TOKEN}`,
    'X-Company-Id': COMPANY_ID,
};

const endpoints = [
    '/api/v1/reports/revenue?from=2026-01-01&to=2026-12-31&groupBy=method',
    '/api/v1/reports/revenue?from=2026-01-01&to=2026-12-31&groupBy=day',
    '/api/v1/reports/revenue?from=2026-01-01&to=2026-12-31&groupBy=origin',
    '/api/v1/reports/subscriptions/mrr',
    '/api/v1/reports/subscriptions/churn?from=2026-01-01&to=2026-04-01',
    '/api/v1/reports/overdue',
];

export default function () {
    const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];
    const res = http.get(`${BASE_URL}${endpoint}`, { headers });

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    errorRate.add(!success);
    sleep(Math.random() * 2 + 0.5);
}
