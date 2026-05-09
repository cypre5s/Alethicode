# tutor_graph

LangGraph-based AI tutor workflow runtime used by Alethicode.

## Responsibilities

- Hold the LangGraph state, phase transitions, and checkpoints.
- Execute tutor nodes (reading / ideating / diagnosis / ac_review / transfer / chat / knowledge_review).
- Call back to the Java side via internal `/internal/ai-tutor/*` APIs for evidence and side effects.
- Emit runtime events to the Java gateway over `/internal/graph/runs/{runId}/events`.

Tutor workflow is the **only** source of truth for LangGraph state. Java holds projection tables for admin / UI recovery but never mutates LangGraph state directly.

## Environment

| Variable | Required | Default | Notes |
|----------|----------|---------|-------|
| `TUTOR_GRAPH_DATABASE_URI` | when `TUTOR_GRAPH_CHECKPOINTER=postgres` | — | e.g. `postgresql://user:pass@host:5432/langgraph` |
| `TUTOR_GRAPH_JAVA_TOOL_BASE_URL` | yes | — | Java backend URL (e.g. `http://backend:8080`) |
| `TUTOR_GRAPH_INTERNAL_SERVICE_KEY` | yes | — | Must match Java `alethicode.internal.service-key` |
| `TUTOR_GRAPH_CHECKPOINTER` | no | `postgres` | `postgres` (prod, fail-fast) or `memory` (tests only) |
| `TUTOR_GRAPH_LLM_PROVIDER` | no | `openai` | OpenAI-compatible provider |
| `TUTOR_GRAPH_LLM_MODEL` | no | `gpt-4o` | |
| `TUTOR_GRAPH_LLM_API_KEY` | no | — | Blank allowed only when `replay_fixtures` supply responses |
| `TUTOR_GRAPH_LLM_BASE_URL` | no | — | |
| `TUTOR_GRAPH_LLM_TEMPERATURE` | no | `0.3` | |
| `TUTOR_GRAPH_REACT_ENABLED` | no | `false` | Tutor ReAct tool loop stays off per project policy |

Postgres mode is mandatory in production. Memory mode skips the DB requirement but **loses all state on restart** and must never be used outside unit tests.
When deployed with `deploy/docker-compose.yml`, the PostgreSQL checkpointer must connect
directly to `postgres:5432`. Do not route `TUTOR_GRAPH_DATABASE_URI` through PgBouncer
transaction pooling, because LangGraph's `psycopg` checkpointer uses pipeline /
prepared-statement behavior that is bound to a single PostgreSQL backend connection.

### Single-worker requirement

`tutor_graph` keeps per-run event buffers, active-run registry, and background task state
in process memory (see `_run_events` / `_active_runs` / `_background_tasks` in
`app/main.py`). Running multiple uvicorn workers would fan state across processes and
the Java gateway's polling would randomly miss events. Always start the service with
`--workers 1` (the shipped Dockerfile enforces this). Horizontal scaling is planned via
a Redis-backed event bus; until then scale vertically and rely on the Postgres
checkpointer for cross-instance persistence of LangGraph thread state.

## Run locally

```bash
cd tutor_graph
python -m pip install -e .
TUTOR_GRAPH_JAVA_TOOL_BASE_URL=http://127.0.0.1:8080 \
TUTOR_GRAPH_INTERNAL_SERVICE_KEY=dev-internal-key \
TUTOR_GRAPH_CHECKPOINTER=memory \
uvicorn app.main:app --host 0.0.0.0 --port 8100
```

## Tests

```bash
cd tutor_graph
python -m pip install -e ".[dev]"
python -m pytest -q
```

## Container

The Dockerfile is driven from the repo root so the `contracts/` directory stays in sync:

```bash
docker build -f services/tutor-graph/Dockerfile -t alethicode/tutor-graph:local .
docker run --rm -p 8100:8100 \
    -e TUTOR_GRAPH_CHECKPOINTER=memory \
    -e TUTOR_GRAPH_JAVA_TOOL_BASE_URL=http://host.docker.internal:8080 \
    -e TUTOR_GRAPH_INTERNAL_SERVICE_KEY=dev-internal-key \
    alethicode/tutor-graph:local
```
