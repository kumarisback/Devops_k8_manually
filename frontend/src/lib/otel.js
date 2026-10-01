// Browser tracing with the OpenTelemetry web SDK. Spans are batched and
// sent to the same origin at /otlp/v1/traces, which the ingress routes to
// the OTel Collector's browser receiver (gitops repo), so a trace starts in
// the browser and continues through the ingress and the backend services.
import { trace } from '@opentelemetry/api';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { BatchSpanProcessor, WebTracerProvider } from '@opentelemetry/sdk-trace-web';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';

const DEFAULT_ENDPOINT = '/otlp/v1/traces';

// VITE_OTEL_TRACES_ENDPOINT overrides the endpoint at build time; an empty
// value turns browser tracing off (traceFetch then falls back to sending a
// bare traceparent header).
export function startTracing(endpoint = import.meta.env?.VITE_OTEL_TRACES_ENDPOINT ?? DEFAULT_ENDPOINT) {
  if (!endpoint || typeof window === 'undefined') {
    return false;
  }

  const provider = new WebTracerProvider({
    resource: resourceFromAttributes({ [ATTR_SERVICE_NAME]: 'frontend' }),
    spanProcessors: [new BatchSpanProcessor(new OTLPTraceExporter({ url: endpoint }))],
  });
  // W3C tracecontext + baggage propagation, the same as the Java agent's.
  provider.register();

  registerInstrumentations({
    instrumentations: [
      new FetchInstrumentation({
        // Same-origin API calls get traceparent automatically; the span
        // export itself must not be traced.
        ignoreUrls: [/\/otlp\//],
      }),
    ],
  });

  return true;
}

export function getTracer() {
  return trace.getTracer('frontend');
}
