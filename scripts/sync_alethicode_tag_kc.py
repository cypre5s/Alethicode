#!/usr/bin/env python3
"""
将 Alethicode 源库中的标签/KC 相关数据同步到 Java 迁移库。

同步范围（以 Alethicode 源库为真源）：
1) problem_tag
2) problem_problem_tags（源表 problem_tags）
3) ai_knowledge_component（源表 knowledge_component，按 tag_id 对齐）
4) ai_problem_kc_mapping（源表 problem_kc_mapping，按 knowledge_component.tag_id 映射）
"""

from __future__ import annotations

import os
from dataclasses import dataclass

import psycopg


@dataclass
class SyncStats:
    inserted_problem_tags: int = 0
    inserted_problem_problem_tags: int = 0
    synced_ai_kc: int = 0
    synced_ai_problem_kc_mapping: int = 0


def get_dsn(env_key: str, default: str) -> str:
    value = os.getenv(env_key, "").strip()
    return value if value else default


def main() -> None:
    src_dsn = get_dsn(
        "ALETHICODE_SRC_DSN",
        "postgresql://onlinejudge:ChangeMeBeforeDeploy_2026!@127.0.0.1:5435/aethicode",
    )
    dst_dsn = get_dsn(
        "ALETHICODE_DST_DSN",
        "postgresql://onlinejudge:ChangeMeBeforeDeploy_2026!@127.0.0.1:5436/alethicode",
    )

    stats = SyncStats()

    with psycopg.connect(src_dsn) as src_conn, psycopg.connect(dst_dsn) as dst_conn:
        src_conn.autocommit = False
        dst_conn.autocommit = False

        with src_conn.cursor() as src_cur, dst_conn.cursor() as dst_cur:
            dst_cur.execute("select id from problem")
            existing_problem_ids = {row[0] for row in dst_cur.fetchall()}

            # 1) 同步 problem_tag
            src_cur.execute("select id, name from problem_tag order by id asc")
            src_tags = src_cur.fetchall()
            dst_cur.executemany(
                """
                insert into problem_tag(id, name)
                values (%s, %s)
                on conflict (id) do update set name = excluded.name
                """,
                src_tags,
            )
            stats.inserted_problem_tags = len(src_tags)
            dst_cur.execute("select id from problem_tag")
            existing_tag_ids = {row[0] for row in dst_cur.fetchall()}

            # 2) 同步 problem_tags -> problem_problem_tags
            src_cur.execute(
                """
                select id, problem_id, problemtag_id
                from problem_tags
                order by id asc
                """
            )
            src_relations = src_cur.fetchall()
            src_relations = [
                row for row in src_relations
                if row[1] in existing_problem_ids and row[2] in existing_tag_ids
            ]
            dst_cur.executemany(
                """
                insert into problem_problem_tags(id, problem_id, problemtag_id)
                values (%s, %s, %s)
                on conflict (id) do update
                set problem_id = excluded.problem_id,
                    problemtag_id = excluded.problemtag_id
                """,
                src_relations,
            )
            stats.inserted_problem_problem_tags = len(src_relations)

            # 3) 同步 ai_knowledge_component（以 source knowledge_component 为真源，按 tag_id 对齐）
            src_cur.execute(
                """
                select tag_id, name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess
                from knowledge_component
                where tag_id is not null
                order by tag_id asc
                """,
            )
            source_kcs = src_cur.fetchall()
            source_kcs = [row for row in source_kcs if row[0] in existing_tag_ids]
            dst_cur.executemany(
                """
                insert into ai_knowledge_component(
                    id, name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess
                )
                values (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                on conflict (id) do update set
                    name = excluded.name,
                    name_en = excluded.name_en,
                    chapter = excluded.chapter,
                    description = excluded.description,
                    p_init = excluded.p_init,
                    p_transit = excluded.p_transit,
                    p_slip = excluded.p_slip,
                    p_guess = excluded.p_guess
                """,
                source_kcs,
            )
            stats.synced_ai_kc = len(source_kcs)
            existing_ai_kc_ids = {row[0] for row in source_kcs}

            # 4) 同步 ai_problem_kc_mapping（以 source problem_kc_mapping 为真源）
            src_cur.execute(
                """
                select pkm.problem_id, kc.tag_id, coalesce(pkm.weight, 1.0) as weight
                from problem_kc_mapping pkm
                join knowledge_component kc on kc.id = pkm.kc_id
                where kc.tag_id is not null
                order by pkm.problem_id asc, kc.tag_id asc
                """
            )
            source_problem_kc = src_cur.fetchall()
            source_problem_kc = [
                row for row in source_problem_kc
                if row[0] in existing_problem_ids and row[1] in existing_ai_kc_ids
            ]
            dst_cur.executemany(
                """
                insert into ai_problem_kc_mapping(problem_id, kc_id, weight)
                values (%s, %s, %s)
                on conflict (problem_id, kc_id) do update
                set weight = excluded.weight
                """,
                source_problem_kc,
            )
            stats.synced_ai_problem_kc_mapping = len(source_problem_kc)

            # 序列回正
            dst_cur.execute(
                """
                select setval(
                    'problem_tag_id_seq',
                    coalesce((select max(id) from problem_tag), 1),
                    true
                )
                """
            )
            dst_cur.execute(
                """
                select setval(
                    'problem_problem_tags_id_seq',
                    coalesce((select max(id) from problem_problem_tags), 1),
                    true
                )
                """
            )
            dst_cur.execute(
                """
                select setval(
                    'ai_knowledge_component_id_seq',
                    coalesce((select max(id) from ai_knowledge_component), 1),
                    true
                )
                """
            )
            dst_cur.execute(
                """
                select setval(
                    'ai_problem_kc_mapping_id_seq',
                    coalesce((select max(id) from ai_problem_kc_mapping), 1),
                    true
                )
                """
            )

        dst_conn.commit()

    print("Sync completed:")
    print(f"- problem_tag rows synced: {stats.inserted_problem_tags}")
    print(f"- problem_problem_tags rows synced: {stats.inserted_problem_problem_tags}")
    print(f"- ai_knowledge_component rows synced: {stats.synced_ai_kc}")
    print(f"- ai_problem_kc_mapping rows synced: {stats.synced_ai_problem_kc_mapping}")


if __name__ == "__main__":
    main()
