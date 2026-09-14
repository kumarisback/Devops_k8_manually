import assert from 'node:assert/strict';
import test from 'node:test';

import { createTraceContext, formatTraceparent, traceFetch, withTraceHeaders } from '../src/lib/tracing.js';

test('creates W3C traceparent headers', () => {
  const traceContext = createTraceContext();
  const traceparent = formatTraceparent(traceContext);

  assert.match(traceContext.traceId, /^[0-9a-f]{32}$/);
  assert.match(traceContext.spanId, /^[0-9a-f]{16}$/);
  assert.match(traceparent, /^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
});

test('preserves caller headers while adding trace context', () => {
  const headers = withTraceHeaders({ Authorization: 'Bearer token' }, {
    traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
    spanId: '00f067aa0ba902b7',
    sampled: true,
  });

  assert.equal(headers.Authorization, 'Bearer token');
  assert.equal(headers.traceparent, '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01');
});

test('traceFetch sends traceparent to downstream APIs', async () => {
  const originalFetch = globalThis.fetch;
  const originalPerformance = globalThis.performance;
  const requests = [];

  globalThis.performance = { now: () => 100 };
  globalThis.fetch = async (input, options) => {
    requests.push({ input, options });
    return { status: 200 };
  };

  try {
    await traceFetch('/api/tasks', {
      headers: {
        Authorization: 'Bearer token',
      },
    });
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.performance = originalPerformance;
  }

  assert.equal(requests[0].input, '/api/tasks');
  assert.equal(requests[0].options.headers.Authorization, 'Bearer token');
  assert.match(requests[0].options.headers.traceparent, /^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
});
