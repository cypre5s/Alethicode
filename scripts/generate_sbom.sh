#!/usr/bin/env bash
# 生成 Alethicode 的软件物料清单 (SBOM) 以满足:
#   * 等保 2.0 三级之"软件与应用"审计要求
#   * 《关键信息基础设施安全保护条例》的第三方组件登记
#   * SLSA Level 2+ 供应链要求
#
# 国内镜像：
#   * syft 下载：docker.aliyuncs.com/anchore/syft:latest
#   * 结果文件：sbom.backend.cdx.json / sbom.tutor-graph.cdx.json / sbom.frontend.cdx.json
#
# 使用：
#   ./scripts/generate_sbom.sh
#   CYCLONEDX_OUTPUT=xml ./scripts/generate_sbom.sh   # 改 XML 格式

set -euo pipefail

SYFT_IMAGE="${SYFT_IMAGE:-anchore/syft:latest}"
OUTPUT_DIR="${OUTPUT_DIR:-./build/sbom}"
OUTPUT_FORMAT="${CYCLONEDX_OUTPUT:-cyclonedx-json}"

# Fail fast when neither syft nor docker is available — half-empty SBOMs are worse
# than no SBOM because they silently claim coverage in compliance reports.
if ! command -v syft >/dev/null 2>&1 && ! command -v docker >/dev/null 2>&1; then
    echo "error: neither 'syft' nor 'docker' is installed. Install syft from" >&2
    echo "       https://github.com/anchore/syft or pull ${SYFT_IMAGE} via docker." >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

echo "== Backend (Maven) =="
if command -v syft >/dev/null 2>&1; then
    syft dir:backend -o "$OUTPUT_FORMAT" > "$OUTPUT_DIR/sbom.backend.cdx.json"
else
    docker run --rm -v "$(pwd)":/workspace "$SYFT_IMAGE" \
        dir:/workspace/backend -o "$OUTPUT_FORMAT" > "$OUTPUT_DIR/sbom.backend.cdx.json"
fi

echo "== tutor_graph (Python) =="
if command -v syft >/dev/null 2>&1; then
    syft dir:tutor_graph -o "$OUTPUT_FORMAT" > "$OUTPUT_DIR/sbom.tutor-graph.cdx.json"
else
    docker run --rm -v "$(pwd)":/workspace "$SYFT_IMAGE" \
        dir:/workspace/tutor_graph -o "$OUTPUT_FORMAT" > "$OUTPUT_DIR/sbom.tutor-graph.cdx.json"
fi

echo "== Frontend (npm) =="
if command -v syft >/dev/null 2>&1; then
    syft dir:frontend -o "$OUTPUT_FORMAT" > "$OUTPUT_DIR/sbom.frontend.cdx.json"
else
    docker run --rm -v "$(pwd)":/workspace "$SYFT_IMAGE" \
        dir:/workspace/frontend -o "$OUTPUT_FORMAT" > "$OUTPUT_DIR/sbom.frontend.cdx.json"
fi

echo
echo "SBOMs written to $OUTPUT_DIR:"
ls -la "$OUTPUT_DIR"
