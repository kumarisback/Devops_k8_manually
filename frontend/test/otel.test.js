import assert from 'node:assert/strict';
import test from 'node:test';

import { trace } from '@opentelemetry/api';
import {
  BasicTracerProvider,
  InMemorySpanExporter,
  SimpleSpanProcessor,
} from '@opentelemetry/sdk-trace-base';

import { startTracing } from '../src/lib/otel.js';
import { traceFetch } from '../src/lib/tracing.js';

// Runs in its own process (node --test), so registering a global provider
// here doesn't affect tracing.test.js.
const exporter = new InMemorySpanExporter();
trace.setGlobalTracerProvider(new BasicTracerProvider({
  spanProcessors: [new SimpleSpanProcessor(exporter)],
}));

async function withFetch(status, run) {
  const originalFetch = globalThis.fetch;
  const originalInfo = console.info;
  const requests = [];
  const logs = [];
  globalThis.fetch = async (input, options) => {
    requests.push({ input, options });
    return { status };
  };
  console.info = (...args) => logs.push(args);
  try {
    await run();
  } finally {
    globalThis.fetch = originalFetch;
    console.info = originalInfo;
  }
  return { requests, logs };
}

test('startTracing does nothing outside a browser', () => {
  assert.equal(startTracing('/otlp/v1/traces'), false);
});

test('with the SDK, traceFetch records a span and logs its trace id', async () => {
  exporter.reset();
  const { requests, logs } = await withFetch(200, () => traceFetch('/api/tasks', { method: 'post' }));

  const [span] = exporter.getFinishedSpans();
  assert.equal(span.name, 'POST /api/tasks');
  assert.equal(span.attributes['http.response.status_code'], 200);
  // The fetch instrumentation adds traceparent in the browser; traceFetch
  // must not add a second, unrelated one.
  assert.equal(requests[0].options.headers.traceparent, undefined);
  assert.equal(logs[0][1].trace_id, span.spanContext().traceId);
});

test('with the SDK, a 5xx response marks the span as an error', async () => {
  exporter.reset();
  await withFetch(503, () => traceFetch('/api/orders'));

  const [span] = exporter.getFinishedSpans();
  assert.equal(span.status.code, 2); // SpanStatusCode.ERROR
});
