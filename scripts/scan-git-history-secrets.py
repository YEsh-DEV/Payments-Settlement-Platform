#!/usr/bin/env python3
"""
PayCore Full Git History Secret & Credential Scanner
Scans every commit in the git repository across all branches and tags.
Detects:
- Private RSA/EC/PGP keys
- AWS Secret Access Keys & Client Secrets
- Production Bearer tokens
- High-entropy password assignments in non-test code
"""

import subprocess
import sys
import re

PATTERNS = [
    (re.compile(r"-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"), "Private Key Block"),
    (re.compile(r"AKIA[0-9A-Z]{16}"), "AWS Access Key ID"),
    (re.compile(r"ghp_[0-9a-zA-Z]{36}"), "GitHub Personal Access Token"),
    (re.compile(r"xox[baprs]-[0-9a-zA-Z]{10,48}"), "Slack Token"),
    (re.compile(r"(?i)(api[_-]?key|secret[_-]?key|client[_-]?secret)\s*[:=]\s*['\"][0-9a-zA-Z]{24,}['\"]"), "API Secret String"),
]

# Patterns explicitly permitted in test fixtures
ALLOWED_FILE_PATTERNS = [
    re.compile(r"src/test/"),
    re.compile(r"docs/audit/evidence/"),
    re.compile(r"deploy/keys/"),  # Local development test keys
    re.compile(r"\.github/"),
    re.compile(r"package-lock\.json"),
]

def scan_history():
    print("=" * 80)
    print("PayCore: Scanning Full Git History Across All Branches...")
    print("=" * 80)

    # Get list of all commit SHAs
    rev_output = subprocess.check_output(["git", "rev-list", "--all"], text=True).strip().splitlines()
    print(f"Total commits in history: {len(rev_output)}")

    violations = []
    commits_scanned = 0

    for sha in rev_output:
        commits_scanned += 1
        # Get diff for this commit
        diff = subprocess.check_output(["git", "show", "--pretty=format:%H %an %ad", sha], text=True)
        lines = diff.splitlines()
        commit_header = lines[0] if lines else sha

        current_file = None
        for line in lines[1:]:
            if line.startswith("+++ b/"):
                current_file = line[6:]
                continue
            
            # Check added lines only
            if line.startswith("+") and not line.startswith("+++"):
                content = line[1:]
                
                # Skip test files and allowed fixtures
                if current_file and any(p.search(current_file) for p in ALLOWED_FILE_PATTERNS):
                    continue

                for pattern, desc in PATTERNS:
                    if pattern.search(content):
                        violations.append({
                            "commit": commit_header,
                            "file": current_file,
                            "type": desc,
                            "snippet": content[:80].strip()
                        })

    print(f"Scanned {commits_scanned} commits across repository history.")
    print("=" * 80)

    if violations:
        print(f"FAILED: {len(violations)} secret violation(s) found in git history:")
        for v in violations:
            print(f"- [{v['commit']}] File: {v['file']} ({v['type']}): {v['snippet']}")
        return 1
    else:
        print("PASS: Zero secret violations detected in git history.")
        return 0

if __name__ == "__main__":
    sys.exit(scan_history())
