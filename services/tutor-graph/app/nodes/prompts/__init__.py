"""Centralized prompt template assemblers for tutor_graph nodes.

Currently exposes:
- assemble_learner_block: P1 Persistent Memory profile injection
"""

from app.nodes.prompts.learner_block import assemble_learner_block

__all__ = ["assemble_learner_block"]
