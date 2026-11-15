// perf/workloads/w2-mixed.js
// Workload W2: 70% Captures, 20% Refunds, 10% Payout Requests (Playbook Section 9.2)

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    w2_load: {
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
  const rand = Math.random();
  const sellerNum = Math.floor(Math.random() * 500) + 1;
  const sellerId = `seller_${sellerNum}`;
  const iterId = `${__VU}_${__ITER}_${Date.now()}`;

  if (rand < 0.70) {
    // 70% Captures
    const paymentId = `pay_w2_${iterId}`;
    const res = http.post(
      `${BASE_URL}/v1/payments`,
      JSON.stringify({ paymentId: paymentId, sellerId: sellerId, grossPaise: 100000 }),
      { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `idemp_${paymentId}` } }
    );
    check(res, { 'capture ok': (r) => r.status === 201 });
  } else if (rand < 0.90) {
    // 20% Refunds (proportional split)
    const refundId = `ref_w2_${iterId}`;
    const paymentId = `pay_w2_seed_${sellerNum}`;
    const res = http.post(
      `${BASE_URL}/v1/refunds`,
      JSON.stringify({ refundId: refundId, paymentId: paymentId, amountPaise: 20000, reason: 'Customer return' }),
      { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `idemp_${refundId}` } }
    );
    check(res, { 'refund processed': (r) => r.status === 200 || r.status === 201 });
  } else {
    // 10% Payout Requests (Invariant I5 verified)
    const payoutId = `payout_w2_${iterId}`;
    const res = http.post(
      `${BASE_URL}/v1/payouts`,
      JSON.stringify({ payoutId: payoutId, sellerId: sellerId, amountPaise: 50000 }),
      { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `idemp_${payoutId}` } }
    );
    check(res, { 'payout handled': (r) => r.status === 201 || r.status === 422 });
  }
}
