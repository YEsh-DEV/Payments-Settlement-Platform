import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    uniform_workload: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.PAYCORE_BASE_URL || 'http://localhost:8082';

export default function () {
  // W1: Uniform distribution across 10,000 sellers
  const sellerNum = Math.floor(Math.random() * 10000) + 1;
  const sellerId = `seller_${sellerNum}`;
  const paymentId = `pay_${uuidv4()}`;
  const idempotencyKey = `idemp_${paymentId}`;

  const payload = JSON.stringify({
    paymentId: paymentId,
    sellerId: sellerId,
    grossPaise: 50000, // Rs 500.00
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
  };

  const res = http.post(`${BASE_URL}/payments`, payload, params);

  check(res, {
    'status is 201': (r) => r.status === 201,
    'grossPaise matches': (r) => JSON.parse(r.body).grossPaise === 50000,
  });

  sleep(0.01);
}
