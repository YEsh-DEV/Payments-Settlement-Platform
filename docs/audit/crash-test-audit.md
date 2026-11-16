# Audit of Crash and Recovery Harness (Hypothesis G1)

## 1. Executive Summary & Verdict
- **Verdict on Hypothesis G1:** **CONFIRMED**
- **Analysis:** The previous crash harness implementation in `scripts/crash-test` (commit `e13a14e`) utilized an in-process multi-threaded simulation harness where "crash" was signaled via `crashed.set()` on a `threading.Event` and "restart" was simulated with `time.sleep()`. Consequently, the reported 85.63 ms recovery time represented an in-memory thread synchronization delay rather than the real-world OS process restart, JVM class loading, Spring Boot context initialization, and Flyway migration time of a real separate process.
- **Remediation Action:** The harness has been redesigned and rewritten to execute a real, separate OS process, deliver authentic OS kernel `SIGKILL` (`kill -9 <pid>`), wait for full OS process termination, relaunch the process, wait for `/actuator/health` UP and the first successful live HTTP transaction, and record the real measured recovery time distribution (min, median, p95, max).

---

## 2. Forensic Code Audit of Previous `scripts/crash-test`

| Audit Question | Previous Implementation in `scripts/crash-test` | Actual Reality / Forensic Finding |
| :--- | :--- | :--- |
| **What was killed?** | `crashed = threading.Event()`<br>`crashed.set()` | No OS process or container was killed. It was an in-process Python thread event flag. |
| **What command sent the kill?** | In-process method call `crashed.set()` | No OS `kill -9` or `docker kill` command was invoked. |
| **What started the service again?** | `db.startup_recovery()` followed by `time.sleep(random.uniform(0.05, 0.12))` | No process was spawned; it re-used the same in-memory Python process and objects. |
| **How was recovery time measured?** | `restart_start_ns = time.perf_counter_ns()`<br>`time.sleep(...)`<br>`recovery_time_ms = (perf_counter_ns() - start) / 1e6` | Measured the sleep duration of the simulated in-memory pause (~50–120 ms, averaging 85.63 ms). |

---

## 3. Real Process Protocol Specification (Playbook Section 9.4)

### 3.1 Process Execution Model
The rewritten harness executes the ledger service as a real separate operating system process with an isolated PID:
1. **Target:** Native separate process running the ledger HTTP service.
2. **Crash Signal:** Real POSIX `SIGKILL` (`signal.SIGKILL` / `kill -9 <pid>`).
3. **Termination Guarantee:** Harness awaits process termination via `poll()` / `wait()`, confirming exit code `-9` / `137`.
4. **Restart & Recovery Measurement:**
   - Restart timestamp $T_{restart}$ captured immediately before issuing the exec command.
   - Service initializes, runs Flyway migrations, and executes `recoverOrphanedInFlightKeysOnStartup()`.
   - Harness continuously polls the health check endpoint `/actuator/health` until HTTP 200 `UP`.
   - Harness immediately dispatches a real verification request.
   - Upon first successful response, timestamp $T_{success}$ is captured.
   - **Real Recovery Time:** $T_{success} - T_{restart}$.

### 3.2 Six Verifiable Assertions
- **Assertion A (Invariant I3):** Debits equal credits across the whole ledger.
- **Assertion B (Invariant I6):** Every idempotency key has at most one journal.
- **Assertion C (Invariant I8):** No payment or refund links to more than one journal.
- **Assertion D:** Outbox contains each committed event exactly once.
- **Assertion E:** No request returning 201 Created is missing a committed journal.
- **Assertion F (New Gate Check):** No request that returned 201 Created before the kill produces a second journal when replayed after restart (replayed response matches original snapshot).
