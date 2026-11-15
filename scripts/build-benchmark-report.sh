#!/usr/bin/env bash
set -euo pipefail

# Generates docs/benchmark-report.md from perf/results/*.json
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${ROOT_DIR}"
python3 scripts/build-benchmark-report.py
