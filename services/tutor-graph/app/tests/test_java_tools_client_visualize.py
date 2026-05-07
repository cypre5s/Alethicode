"""回归测试：可视化 422 响应必须保留 Java 返回的校验错误详情。"""

from __future__ import annotations

import httpx
import pytest

from app.clients.java_tools_client import JavaToolsClient


def _build_client(transport: httpx.MockTransport) -> JavaToolsClient:
    client = JavaToolsClient.__new__(JavaToolsClient)
    client._base = "http://java"
    client._headers = {"X-Internal-Service-Key": "k"}
    client._visualize_timeout = 120.0
    client._client = httpx.AsyncClient(base_url="http://java", transport=transport, timeout=5.0)
    return client


@pytest.mark.asyncio
async def test_dispatch_visualize_422_body_is_propagated_to_error_message():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            status_code=422,
            json={"error": "mermaid must start with flowchart|graph (TD/LR/...) / sequenceDiagram"},
        )

    client = _build_client(httpx.MockTransport(handler))
    try:
        with pytest.raises(httpx.HTTPStatusError) as excinfo:
            await client.dispatch_visualize({"intent": "flowchart", "prompt": "x"})

        message = str(excinfo.value)
        assert "422 visualize validation failed" in message
        assert "mermaid must start with" in message
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_dispatch_visualize_uses_visualize_specific_timeout():
    observed_read_timeout = []

    def handler(request: httpx.Request) -> httpx.Response:
        observed_read_timeout.append(request.extensions["timeout"]["read"])
        return httpx.Response(
            status_code=200,
            json={
                "card_id": "C-V-001",
                "card_payload": {
                    "intent": "flowchart",
                    "format": "mermaid",
                    "payload": "graph TD\nA --> B",
                    "alt_text": "x",
                },
            },
        )

    client = _build_client(httpx.MockTransport(handler))
    try:
        await client.dispatch_visualize({"intent": "flowchart", "prompt": "x"})
        assert observed_read_timeout == [120.0]
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_dispatch_visualize_422_with_non_json_body_falls_back_to_text():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(status_code=422, text="raw boom")

    client = _build_client(httpx.MockTransport(handler))
    try:
        with pytest.raises(httpx.HTTPStatusError) as excinfo:
            await client.dispatch_visualize({"intent": "flowchart", "prompt": "x"})

        assert "raw boom" in str(excinfo.value)
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_dispatch_visualize_200_returns_card_payload():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            status_code=200,
            json={
                "card_id": "C-V-001",
                "card_payload": {
                    "intent": "flowchart",
                    "format": "mermaid",
                    "payload": "graph TD\nA --> B",
                    "alt_text": "x",
                    "source_role": "Nene",
                },
            },
        )

    client = _build_client(httpx.MockTransport(handler))
    try:
        response = await client.dispatch_visualize({"intent": "flowchart", "prompt": "x"})
        assert response["card_id"] == "C-V-001"
        assert response["card_payload"]["payload"].startswith("graph TD")
    finally:
        await client.close()
