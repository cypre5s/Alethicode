"""OpenTelemetry bootstrap for tutor_graph.

Setting this up in a dedicated module (rather than inline in ``app.main``) keeps
``main`` readable and makes it easy to skip OTel in unit tests by leaving the
``OTEL_EXPORTER_OTLP_ENDPOINT`` environment variable unset.

Defaults are aligned with the Java side so a request that enters Java and then
hops to tutor_graph shows up as a single distributed trace. Service names use the
``service.name`` resource attribute so Jaeger / Tempo / 阿里云 ARMS / SLS Trace
all render them with the same label.
"""

from __future__ import annotations

import logging
import os


def configure_otel(app) -> None:
    endpoint = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT")
    if not endpoint:
        logging.getLogger(__name__).info(
            "OTEL_EXPORTER_OTLP_ENDPOINT not set — tracing disabled for tutor_graph"
        )
        return

    # Imported lazily so unit tests without the OTel packages still import app.main.
    from opentelemetry import trace
    from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
    from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
    from opentelemetry.instrumentation.logging import LoggingInstrumentor
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor

    resource = Resource.create({
        "service.name": os.environ.get("OTEL_SERVICE_NAME", "tutor-graph"),
        "service.namespace": "alethicode",
        "deployment.environment": os.environ.get("DEPLOYMENT_ENVIRONMENT", "dev"),
    })
    provider = TracerProvider(resource=resource)
    exporter = OTLPSpanExporter(endpoint=f"{endpoint.rstrip('/')}/v1/traces")
    provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(provider)

    FastAPIInstrumentor.instrument_app(app)
    HTTPXClientInstrumentor().instrument()
    LoggingInstrumentor().instrument(set_logging_format=True)
