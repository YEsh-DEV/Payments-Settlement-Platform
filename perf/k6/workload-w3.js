import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    hotspot_workload: {
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
    http_req_duration: ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.PAYCORE_BASE_URL || 'http://localhost:8082';

export default function () {
  // W3: Pareto hot-spot: 80% of traffic hits top 20% of sellers (Playbook 9.2)
  const isParetoTop = Math.random() < 0.80;
  const sellerId = isParetoTop
    ? `seller_${Math.floor(Math.random() * 20) + 1}`
    : `seller_${Math.floor(Math.random() * 80) + 21}`;

  const paymentId = `pay_${uuidv4()}`;
  const idempotencyKey = `idemp_${paymentId}`;

  const payload = JSON.stringify({
    paymentId: paymentId,
    sellerId: sellerId,
    grossPaise: 120000, // Rs 1200.00
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

  sleep(0.005);
}
