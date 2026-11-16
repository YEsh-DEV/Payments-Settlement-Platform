#!/usr/bin/env bash
set -euo pipefail

# Redirect legacy harness location to canonical Playbook 9.4 script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

echo "Notice: Invoking canonical crash test harness at scripts/crash-test..."
exec "${REPO_ROOT}/scripts/crash-test" "$@"
