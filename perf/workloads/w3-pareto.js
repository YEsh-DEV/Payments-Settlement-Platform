// perf/workloads/w3-pareto.js
// Workload W3: 80% of Captures concentrated on 20% of Sellers (Playbook Section 9.2)

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    w3_load: {
      executor: 'constant-vus',
      vus: __ENV.VUS ? parseInt(__ENV.VUS) : 32,
      duration: __ENV.DURATION || '60s',
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<250'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.PAYCORE_BASE_URL || 'http://localhost:8082';

export default function () {
  // 80% to top 20% (merchants 1..100), 20% to remaining 80% (merchants 101..500)
  const isTop20 = Math.random() < 0.80;
  const sellerNum = isTop20
    ? Math.floor(Math.random() * 100) + 1
    : Math.floor(Math.random() * 400) + 101;

  const sellerId = `seller_${sellerNum}`;
  const paymentId = `pay_w3_${__VU}_${__ITER}_${Date.now()}`;
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

  const res = http.post(`${BASE_URL}/v1/payments`, payload, params);

  check(res, {
    'status is 201': (r) => r.status === 201,
  });
}
