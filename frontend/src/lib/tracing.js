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

export async function traceFetch(input, options = {}) {
  const startedAt = performance.now();
  const traceContext = createTraceContext();
  const headers = withTraceHeaders(options.headers, traceContext);

  try {
    const response = await fetch(input, {
      ...options,
      headers,
    });

    console.info('frontend_http_request_completed', {
      trace_id: traceContext.traceId,
      span_id: traceContext.spanId,
      url: String(input),
      status: response.status,
      duration_ms: Math.round(performance.now() - startedAt),
    });

    return response;
  } catch (error) {
    console.error('frontend_http_request_failed', {
      trace_id: traceContext.traceId,
      span_id: traceContext.spanId,
      url: String(input),
      duration_ms: Math.round(performance.now() - startedAt),
      error: error.message,
    });
    throw error;
  }
}
