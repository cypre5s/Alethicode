"""Runtime path helpers shared by multiple tutor-graph modules.

Keeping this lookup in one place means that if the repo layout or the
Docker image layout ever changes again (e.g. when renaming ``app/`` or
moving ``contracts/``), only this file must be updated.
"""

from __future__ import annotations

from pathlib import Path


def locate_card_schema_dir() -> Path:
    """Locate ``contracts/tutor_workflow/cards`` across both deployment layouts.

    - Repo layout: ``<repo>/services/tutor-graph/app/paths.py`` ->
      ``<repo>/contracts/tutor_workflow/cards`` (4 levels up).
    - Container layout: ``/app/app/paths.py`` ->
      ``/app/contracts/tutor_workflow/cards`` (3 levels up, because the
      Dockerfile copies ``contracts`` as a sibling of ``app`` under ``/app``).

    Hard-coding a fixed number of parents breaks one of the two layouts.
    Searching parents instead stays correct for both and fails fast at
    module import if neither directory exists.
    """
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "contracts" / "tutor_workflow" / "cards"
        if candidate.is_dir():
            return candidate
    raise FileNotFoundError(
        "contracts/tutor_workflow/cards directory not found searching upward "
        f"from {here}; check Dockerfile COPY directives or the repo layout."
    )


CARD_SCHEMA_DIR: Path = locate_card_schema_dir()
