// perf/workloads/w1-captures.js
// Workload W1: 100% Payment Captures uniformly distributed across sellers

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    w1_load: {
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
  const sellerNum = Math.floor(Math.random() * 1000) + 1;
  const sellerId = `seller_${sellerNum}`;
  const paymentId = `pay_w1_${__VU}_${__ITER}`;
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

  const res = http.post(`${BASE_URL}/v1/payments`, payload, params);

  check(res, {
    'status is 201': (r) => r.status === 201,
  });
}
