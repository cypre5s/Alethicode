#!/usr/bin/env python3
"""
normalize_language_pack_document.py

Normalizes a document for the language pack pipeline:
  - .pdf: direct passthrough (copy as canonical + preview)
  - .pptx/.docx: retain canonical, generate preview PDF via LibreOffice
  - .ppt/.doc: convert to .pptx/.docx via LibreOffice, then generate preview PDF

Usage:
    python3 normalize_language_pack_document.py <input_path> <output_dir> [--libreoffice <path>]

Output (JSON to stdout):
    {
        "canonical_path": "/path/to/canonical.pptx",
        "preview_pdf_path": "/path/to/preview.pdf",
        "page_count": 12
    }
"""

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

from pypdf import PdfReader


LIBREOFFICE_DEFAULT = "libreoffice"


def run_libreoffice(lo_path: str, input_path: Path, output_dir: Path, target_format: str) -> Path:
    cmd = [
        lo_path,
        "--headless",
        "--convert-to", target_format,
        "--outdir", str(output_dir),
        str(input_path),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    if result.returncode != 0:
        raise RuntimeError(
            f"LibreOffice conversion failed (exit {result.returncode}): "
            f"{result.stdout}\n{result.stderr}"
        )
    stem = input_path.stem
    output_file = output_dir / f"{stem}.{target_format}"
    if not output_file.exists():
        raise RuntimeError(f"Expected output file not found: {output_file}")
    return output_file


def count_pdf_pages(pdf_path: Path) -> int:
    reader = PdfReader(str(pdf_path))
    return len(reader.pages)


def normalize(input_path: Path, output_dir: Path, lo_path: str) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    suffix = input_path.suffix.lower()

    if suffix == ".pdf":
        canonical = output_dir / input_path.name
        shutil.copy2(input_path, canonical)
        preview = canonical
        page_count = count_pdf_pages(canonical)

    elif suffix in (".pptx", ".docx"):
        canonical = output_dir / input_path.name
        shutil.copy2(input_path, canonical)
        preview = run_libreoffice(lo_path, canonical, output_dir, "pdf")
        page_count = count_pdf_pages(preview)

    elif suffix == ".ppt":
        converted = run_libreoffice(lo_path, input_path, output_dir, "pptx")
        canonical = converted
        preview = run_libreoffice(lo_path, converted, output_dir, "pdf")
        page_count = count_pdf_pages(preview)

    elif suffix == ".doc":
        converted = run_libreoffice(lo_path, input_path, output_dir, "docx")
        canonical = converted
        preview = run_libreoffice(lo_path, converted, output_dir, "pdf")
        page_count = count_pdf_pages(preview)

    else:
        raise SystemExit(f"Unsupported file extension: {suffix}")

    return {
        "canonical_path": str(canonical),
        "preview_pdf_path": str(preview),
        "page_count": page_count,
    }


def main():
    parser = argparse.ArgumentParser(description="Normalize language pack document")
    parser.add_argument("input_path", type=Path, help="Path to input document")
    parser.add_argument("output_dir", type=Path, help="Directory for normalized output")
    parser.add_argument("--libreoffice", default=LIBREOFFICE_DEFAULT, help="Path to LibreOffice binary")
    args = parser.parse_args()

    input_path = args.input_path.expanduser().resolve()
    if not input_path.is_file():
        raise SystemExit(f"File not found: {input_path}")

    result = normalize(input_path, args.output_dir.resolve(), args.libreoffice)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
