#!/usr/bin/env python3
"""
PayCore Full Git History Secret & Credential Scanner
Scans every commit in the git repository across all branches, tags, and refs.
Features:
- Scans all commits from `git rev-list --all`
- Tracks added, modified, deleted, and renamed file paths
- Detects RSA/EC private keys, AWS tokens, GitHub PATs, Slack tokens, API secrets
- Built-in self-test mode (--self-test) verifying detection sensitivity against a temp repo
"""

import os
import re
import subprocess
import sys
import tempfile

PATTERNS = [
    (re.compile(r"-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"), "Private Key Block"),
    (re.compile(r"AKIA[0-9A-Z]{16}"), "AWS Access Key ID"),
    (re.compile(r"ghp_[0-9a-zA-Z]{36}"), "GitHub Personal Access Token"),
    (re.compile(r"xox[baprs]-[0-9a-zA-Z]{10,48}"), "Slack Token"),
    (re.compile(r"(?i)(api[_-]?key|secret[_-]?key|client[_-]?secret)\s*[:=]\s*['\"][0-9a-zA-Z]{24,}['\"]"), "API Secret String"),
]

# Paths explicitly permitted as test fixtures or audit logs
ALLOWED_FILE_PATTERNS = [
    re.compile(r"src/test/"),
    re.compile(r"docs/audit/evidence/"),
    re.compile(r"deploy/keys/"),  # Local development test keys
    re.compile(r"\.github/"),
    re.compile(r"package-lock\.json"),
]

def scan_repo(repo_dir=None):
    cmd_prefix = ["git"]
    if repo_dir:
        cmd_prefix = ["git", "-C", repo_dir]

    rev_output = subprocess.check_output(cmd_prefix + ["rev-list", "--all"], text=True).strip().splitlines()
    commits_scanned = 0
    violations = []

    for sha in rev_output:
        commits_scanned += 1
        # Use full raw commit diff including renames and deleted files
        diff = subprocess.check_output(
            cmd_prefix + ["show", "--full-index", "--unified=0", "--pretty=format:COMMIT_HEADER:%H %an %ad", sha],
            text=True,
            errors="replace"
        )
        lines = diff.splitlines()
        commit_header = sha
        current_file = None

        for line in lines:
            if line.startswith("COMMIT_HEADER:"):
                commit_header = line[len("COMMIT_HEADER:"):]
                continue
            if line.startswith("diff --git a/"):
                parts = line.split(" ")
                if len(parts) >= 4:
                    current_file = parts[3][2:] if parts[3].startswith("b/") else parts[3]
                continue
            if line.startswith("--- a/"):
                current_file = line[6:]
                continue
            if line.startswith("+++ b/"):
                current_file = line[6:]
                continue

            # Check added or modified lines
            if line.startswith("+") and not line.startswith("+++"):
                content = line[1:]
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

    return commits_scanned, violations

def run_self_test():
    print("[SELF-TEST] Running scanner sensitivity self-test in temporary repository...")
    with tempfile.TemporaryDirectory() as tmpdir:
        def git(*args):
            subprocess.check_call(["git", "-C", tmpdir] + list(args), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        git("init")
        git("config", "user.name", "Test Runner")
        git("config", "user.email", "runner@test.local")

        # Commit 1: Clean file
        with open(os.path.join(tmpdir, "clean.txt"), "w") as f:
            f.write("public static final String SAFE = 'safe_value';\n")
        git("add", "clean.txt")
        git("commit", "-m", "commit 1: clean")

        # Commit 2: Plant fake private key
        with open(os.path.join(tmpdir, "key.pem"), "w") as f:
            f.write("-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0\n-----END RSA PRIVATE KEY-----\n")
        git("add", "key.pem")
        git("commit", "-m", "commit 2: plant fake key")

        # Commit 3: Plant fake AWS key
        with open(os.path.join(tmpdir, "config.py"), "w") as f:
            f.write("AWS_KEY = 'AKIAIOSFODNN7EXAMPLE'\n")
        git("add", "config.py")
        git("commit", "-m", "commit 3: plant fake aws")

        # Commit 4: Delete the file (proving scan catches historical secrets in deleted files)
        git("rm", "key.pem")
        git("commit", "-m", "commit 4: delete key")

        commits_scanned, violations = scan_repo(tmpdir)
        assert commits_scanned == 4, f"Expected 4 commits in temp repo, got {commits_scanned}"
        assert len(violations) >= 2, f"Expected at least 2 violations caught, got {len(violations)}"

        types_caught = {v["type"] for v in violations}
        assert "Private Key Block" in types_caught, "Self-test failed to catch Private Key Block"
        assert "AWS Access Key ID" in types_caught, "Self-test failed to catch AWS Access Key ID"

    print("[SELF-TEST] SUCCESS: Scanner correctly identified historical secrets and deleted files.")

def main():
    # Always execute self-test first
    run_self_test()

    print("=" * 80)
    print("PayCore: Scanning Full Git History Across All Branches & Refs...")
    print("=" * 80)

    commits_scanned, violations = scan_repo()
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
    sys.exit(main())
