from __future__ import annotations

from app.clients.llm_client import LlmClient


class _FakeResponse:
    def __init__(self, content: str) -> None:
        self.content = content


class _FakeChatOpenAI:
    last_config = None

    def __init__(self, **kwargs) -> None:
        self.kwargs = kwargs

    async def ainvoke(self, messages, config=None):
        _FakeChatOpenAI.last_config = config
        return _FakeResponse("ok")


class _FakeJsonChatOpenAI:
    last_bound_kwargs = None

    def __init__(self, **kwargs) -> None:
        self.kwargs = kwargs

    def bind(self, **kwargs):
        _FakeJsonChatOpenAI.last_bound_kwargs = kwargs
        return self

    async def ainvoke(self, messages, config=None):
        return _FakeResponse('{"answer": "ok"}')


class _FakeCallbackHandler:
    pass


async def test_generate_should_pass_langfuse_callback_when_enabled(monkeypatch):
    import sys
    import types

    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk-test")
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "sk-test")
    monkeypatch.setenv("LANGFUSE_BASE_URL", "http://langfuse:3000")

    monkeypatch.setitem(sys.modules, "langchain_openai", types.SimpleNamespace(ChatOpenAI=_FakeChatOpenAI))
    monkeypatch.setitem(sys.modules, "langfuse.langchain", types.SimpleNamespace(CallbackHandler=_FakeCallbackHandler))

    client = LlmClient(provider="openai", model="gpt-4o")

    result = await client.generate(
        "system",
        "user",
        node_name="reading",
        metadata={
            "trace_id": "trace-1",
            "session_id": "session-1",
            "run_id": "run-1",
            "problem_id": 42,
            "user_id": 7,
            "phase": "READING",
        },
    )

    assert result == "ok"
    assert _FakeChatOpenAI.last_config is not None
    assert _FakeChatOpenAI.last_config["callbacks"]
    assert isinstance(_FakeChatOpenAI.last_config["callbacks"][0], _FakeCallbackHandler)
    assert _FakeChatOpenAI.last_config["metadata"]["trace_id"] == "trace-1"
    assert _FakeChatOpenAI.last_config["metadata"]["session_id"] == "session-1"
    assert _FakeChatOpenAI.last_config["metadata"]["run_id"] == "run-1"
    assert _FakeChatOpenAI.last_config["metadata"]["problem_id"] == 42
    assert _FakeChatOpenAI.last_config["metadata"]["user_id"] == 7
    assert _FakeChatOpenAI.last_config["metadata"]["phase"] == "READING"
    assert _FakeChatOpenAI.last_config["metadata"]["langfuse_observation_name"] == "reading"


async def test_generate_json_should_request_json_object_mode(monkeypatch):
    import sys
    import types

    _FakeJsonChatOpenAI.last_bound_kwargs = None
    monkeypatch.setitem(sys.modules, "langchain_openai", types.SimpleNamespace(ChatOpenAI=_FakeJsonChatOpenAI))

    client = LlmClient(provider="openai", model="gpt-4o")

    result = await client.generate_json("system JSON", "user", node_name="error_diagnosis")

    assert result == {"answer": "ok"}
    assert _FakeJsonChatOpenAI.last_bound_kwargs == {"response_format": {"type": "json_object"}}


def test_build_langfuse_metadata_should_include_stable_tutor_context():
    from app.nodes.langfuse_metadata import build_langfuse_metadata

    metadata = build_langfuse_metadata(
        {
            "trace_id": "trace-1",
            "session_id": "session-1",
            "thread_id": "thread-1",
            "run_id": "run-1",
            "user_id": 7,
            "problem_id": 42,
            "language": "Python3",
            "current_phase": "READING",
            "client_event": "PROBLEM_GUIDE",
        },
        "problem_guide",
    )

    assert metadata == {
        "service": "tutor_graph",
        "node_name": "problem_guide",
        "trace_id": "trace-1",
        "session_id": "session-1",
        "thread_id": "thread-1",
        "run_id": "run-1",
        "user_id": 7,
        "problem_id": 42,
        "language": "Python3",
        "current_phase": "READING",
        "client_event": "PROBLEM_GUIDE",
        "phase": "READING",
    }
