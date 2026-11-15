import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    pareto_workload: {
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
    http_req_duration: ['p(95)<300', 'p(99)<700'],
    http_req_failed: ['rate<0.02'],
  },
};

const BASE_URL = __ENV.PAYCORE_BASE_URL || 'http://localhost:8082';

export default function () {
  // W2: 80/20 Pareto distribution: 80% traffic hits top 20% of 10,000 merchants (top 2000)
  const isTop20 = Math.random() < 0.8;
  const sellerNum = isTop20
    ? Math.floor(Math.random() * 2000) + 1
    : Math.floor(Math.random() * 8000) + 2001;

  const sellerId = `seller_${sellerNum}`;
  const paymentId = `pay_${uuidv4()}`;
  const idempotencyKey = `idemp_${paymentId}`;

  const payload = JSON.stringify({
    paymentId: paymentId,
    sellerId: sellerId,
    grossPaise: 75000, // Rs 750.00
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
  });

  sleep(0.01);
}
