import { isSpanContextValid, SpanStatusCode, trace } from '@opentelemetry/api';

const TRACE_VERSION = '00';
const TRACE_FLAGS_SAMPLED = '01';

function randomHex(bytes) {
  const values = new Uint8Array(bytes);
  const cryptoApi = globalThis.crypto;

  if (cryptoApi?.getRandomValues) {
    cryptoApi.getRandomValues(values);
  } else {
    for (let index = 0; index < values.length; index += 1) {
      values[index] = Math.floor(Math.random() * 256);
    }
  }

  return Array.from(values, (value) => value.toString(16).padStart(2, '0')).join('');
}

export function createTraceContext() {
  return {
    traceId: randomHex(16),
    spanId: randomHex(8),
    sampled: true,
  };
}

export function formatTraceparent(traceContext = createTraceContext()) {
  return `${TRACE_VERSION}-${traceContext.traceId}-${traceContext.spanId}-${traceContext.sampled ? TRACE_FLAGS_SAMPLED : '00'}`;
}

export function withTraceHeaders(headers = {}, traceContext = createTraceContext()) {
  return {
    ...headers,
    traceparent: formatTraceparent(traceContext),
  };
}

// Wraps an API call in a span. With the SDK running (see otel.js) the fetch
// instrumentation adds traceparent and exports the span, so the trace is
// complete from the browser onward. Without it (tests, tracing turned off)
// the call still carries a generated traceparent so backend logs correlate.
export async function traceFetch(input, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const tracer = trace.getTracer('frontend');

  return tracer.startActiveSpan(`${method} ${String(input)}`, async (span) => {
    const sdkSpan = isSpanContextValid(span.spanContext());
    const traceContext = sdkSpan
      ? { traceId: span.spanContext().traceId, spanId: span.spanContext().spanId, sampled: true }
      : createTraceContext();
    const headers = sdkSpan ? { ...options.headers } : withTraceHeaders(options.headers, traceContext);
    const startedAt = performance.now();

    try {
      const response = await fetch(input, {
        ...options,
        headers,
      });
      span.setAttribute('http.response.status_code', response.status);
      if (response.status >= 500) {
        span.setStatus({ code: SpanStatusCode.ERROR });
      }

      console.info('frontend_http_request_completed', {
        trace_id: traceContext.traceId,
        span_id: traceContext.spanId,
        url: String(input),
        status: response.status,
        duration_ms: Math.round(performance.now() - startedAt),
      });

      return response;
    } catch (error) {
      span.recordException(error);
      span.setStatus({ code: SpanStatusCode.ERROR, message: error.message });
      console.error('frontend_http_request_failed', {
        trace_id: traceContext.traceId,
        span_id: traceContext.spanId,
        url: String(input),
        duration_ms: Math.round(performance.now() - startedAt),
        error: error.message,
      });
      throw error;
    } finally {
      span.end();
    }
  });
}
