"""验证生产编排中 tutor-graph 的数据库连接边界。"""

from pathlib import Path


def test_tutor_graph_defaults_to_direct_postgres_connection():
    compose = Path(__file__).parents[4] / "deploy" / "docker-compose.yml"
    text = compose.read_text(encoding="utf-8")

    assert (
        "TUTOR_GRAPH_DATABASE_URI: "
        "${TUTOR_GRAPH_DATABASE_URI:-postgresql://onlinejudge:${DB_PASSWORD}@postgres:5432/alethicode}"
        in text
    )
    assert (
        "TUTOR_GRAPH_DATABASE_URI: "
        "${TUTOR_GRAPH_DATABASE_URI:-postgresql://onlinejudge:${DB_PASSWORD}@pgbouncer:6432/alethicode}"
        not in text
    )
