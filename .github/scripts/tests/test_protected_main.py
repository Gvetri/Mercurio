"""Verify the protected-main workflow declaration, the commit-message check, and the conformance check."""
import json
import os
import re
import shutil
from pathlib import Path
import subprocess
import tempfile
import unittest
import urllib.error
import urllib.request
from github_api_fixture import GitHubApiFixture

ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/quality-gate.yml"
REQUIRED_CHECKS = ROOT / ".github/quality-gate/required-checks.txt"
COMMIT_SCRIPT = ROOT / ".github/scripts/verify-conventional-commits.py"
CONFORMANCE_SCRIPT = ROOT / ".github/scripts/verify-repository-conformance.py"

HEAVY_JOBS = (
    "formatting",
    "static_analysis",
    "detekt",
    "unit_tests",
    "fixture_descriptor",
    "fixture_lifecycle",
    "fixture_contract",
    "android_build",
    "architecture_check",
    "compose_test",
)
DECLARED_CHECKS = tuple(
    line.strip() for line in REQUIRED_CHECKS.read_text().splitlines() if line.strip()
)
HEAVY_JOBS_RUN = (
    "${{ always() && (github.event.action != 'labeled' || "
    "github.event.label.name == 'run-maestro') && "
    "(github.event_name != 'pull_request' || "
    "(!github.event.pull_request.draft && !github.event.pull_request.head.repo.fork && "
    "(contains(github.event.pull_request.labels.*.name, 'run-maestro') || "
    "needs.ui_paths.outputs.docs_only_changed != 'true'))) }}"
)
CONFORMANCE_RUN = (
    "${{ !cancelled() && (github.event.action != 'labeled' || "
    "github.event.label.name == 'run-maestro') && "
    "(github.event_name != 'pull_request' || "
    "(!github.event.pull_request.draft && !github.event.pull_request.head.repo.fork)) }}"
)


JOB_END = re.compile(r"^  [a-z0-9_\\-]+:\s", re.MULTILINE)


def job_block(workflow, job):
    remainder = workflow.split(f"\n  {job}:\n", 1)[-1]
    end = JOB_END.search(remainder)
    return remainder[: end.start()] if end else remainder


def job_key(block, key):
    """Read one job-level key: a `    key:` line that is not a nested key such as `      key:`."""
    prefix = f"    {key}:"
    for line in block.splitlines():
        if not line.startswith(prefix):
            continue
        value = line[len(prefix) :].strip()
        return value or None
    return None


def jobs_of(workflow):
    """Map every job id to its display name and its job-level condition."""
    jobs = {}
    for match in re.finditer(r"^  ([a-z0-9_\\-]+):$", workflow.split("\njobs:\n", 1)[1], re.MULTILINE):
        job = match.group(1)
        block = job_block(workflow, job)
        jobs[job] = (job_key(block, "name"), job_key(block, "if"))
    return jobs


def concurrency_group(workflow, event_name, run_id, ref="refs/heads/main", pull_request=None, action=None, label=None):
    group = workflow.split("\nconcurrency:\n", 1)[1].split("\njobs:\n", 1)[0]
    declaration = re.search(r"^  group: (.+)$", group, re.MULTILINE)
    assert declaration is not None, "The workflow must declare a single-line concurrency group"
    template = declaration.group(1)
    context = {
        "github.workflow": "quality-gate",
        "github.event_name": event_name,
        "github.run_id": run_id,
        "github.ref": ref,
        "github.event.pull_request.number": pull_request,
        "github.event.action": action,
        "github.event.label.name": label,
    }

    def evaluate(match):
        expression = re.sub(r"github\.[a-z_.]+", lambda token: repr(context[token.group()]), match.group(1))
        expression = expression.replace("&&", " and ").replace("||", " or ")
        return str(eval(expression, {"__builtins__": {}, "format": lambda pattern, *args: pattern.format(*args)}))

    return re.sub(r"\$\{\{ (.*?) \}\}", evaluate, template)


class TestProtectedMainWorkflow(unittest.TestCase):
    """The declaration of the protected-main workflow."""

    def setUp(self):
        self.workflow = WORKFLOW.read_text()

    def test_every_declared_check_has_a_job_and_the_aggregate_needs_all_of_them(self):
        for check in DECLARED_CHECKS:
            self.assertIn(f"\n  {check}:\n", self.workflow, f"{check} must have a job")
        aggregate = job_block(self.workflow, "quality-gate")
        needs = aggregate.split("    needs:\n", 1)[1].split("    runs-on:", 1)[0] + "\n"
        declared = re.findall(r"^      - ([a-z0-9_]+)$", needs, re.MULTILINE)
        self.assertEqual(sorted([*DECLARED_CHECKS, "buildsrc_contracts"]), sorted(declared))
        self.assertEqual(len(DECLARED_CHECKS) + 1, len(declared), "the declaration plus the contracts job and the needs list must not differ")

    def test_the_buildsrc_contracts_job_routes_ci_and_build_logic_changes(self):
        jobs = jobs_of(self.workflow)
        self.assertEqual("buildsrc-contracts", jobs["buildsrc_contracts"][0])
        contracts = job_block(self.workflow, "buildsrc_contracts")
        self.assertEqual(
            "${{ always() && (github.event_name == 'schedule' || github.event_name == 'workflow_dispatch' || "
            "(github.event_name == 'pull_request' && (github.event.action != 'labeled' || "
            "github.event.label.name == 'run-maestro') && !github.event.pull_request.draft && "
            "!github.event.pull_request.head.repo.fork && (needs.ui_paths.outputs.ci_files_changed == 'true' || "
            "needs.ui_paths.outputs.build_logic_changed == 'true'))) }}",
            job_key(contracts, "if"),
            "the contracts job must run on the nightly and dispatched validation and on ready in-repository CI or build-logic heads",
        )
        self.assertEqual("[ui_paths]", job_key(contracts, "needs"))
        self.assertIn("./gradlew -p buildSrc test", contracts)
        self.assertIn("--tests org.hermesnative.client.buildlogic.QualityGateConfigurationTest", contracts)
        architecture = job_block(self.workflow, "architecture_check")
        self.assertIn("./gradlew architectureRuleTests verifyNoMocks verifyDeterministicFakes --no-daemon", architecture)
        self.assertNotIn("./gradlew -p buildSrc test", architecture)
        aggregate = job_block(self.workflow, "quality-gate")
        self.assertIn("      - buildsrc_contracts\n", aggregate)
        self.assertIn("BUILDSRC_CONTRACTS_RESULT: ${{ needs.buildsrc_contracts.result }}", aggregate)
        self.assertIn("DEPENDENCY_ONLY_CHANGED: ${{ needs.ui_paths.outputs.dependency_files_changed }}", aggregate)
        self.assertIn("CI_FILES_CHANGED: ${{ needs.ui_paths.outputs.ci_files_changed }}", aggregate)
        self.assertIn("BUILD_LOGIC_CHANGED: ${{ needs.ui_paths.outputs.build_logic_changed }}", aggregate)
        self.assertIn("Dependency-file detection must report true or false for this run", aggregate)
        self.assertIn('if [ "$COVERAGE_MUTATION_RESULT" != "$coverage_expected_state" ]; then', aggregate)
        self.assertIn('[[ "$ci_files_decision" == true || "$build_logic_decision" == true ]]; then', aggregate)

    def test_the_aggregate_gate_is_the_single_required_status(self):
        jobs = jobs_of(self.workflow)
        self.assertEqual("quality-gate", jobs["quality-gate"][0])
        published = [name for name, _ in jobs.values()]
        self.assertEqual(1, published.count("quality-gate"), "only the aggregate gate may publish that status")
        for check in DECLARED_CHECKS:
            self.assertIn(check, jobs, f"{check} must have a job")

    def test_the_workflow_never_runs_pull_request_code_with_secrets_or_write_access(self):
        self.assertNotIn("pull_request_target", self.workflow)
        self.assertNotIn("secrets.", self.workflow)
        self.assertIn("permissions:\n  contents: read\n", self.workflow)

    def test_a_new_commit_cancels_the_obsolete_run_of_the_same_pull_request(self):
        first = concurrency_group(self.workflow, "pull_request", 100, pull_request=25)
        updated = concurrency_group(self.workflow, "pull_request", 101, pull_request=25)
        other = concurrency_group(self.workflow, "pull_request", 102, pull_request=26)
        self.assertEqual("quality-gate-quality-gate-25", first)
        self.assertEqual(first, updated)
        self.assertNotEqual(first, other)
        self.assertIn("  cancel-in-progress: true\n", self.workflow)

    def test_a_push_cannot_cancel_a_dispatched_validation(self):
        dispatched = concurrency_group(self.workflow, "workflow_dispatch", 100)
        pushed = concurrency_group(self.workflow, "push", 101)
        self.assertNotEqual(dispatched, pushed)

    def test_a_non_maestro_label_run_groups_only_with_itself(self):
        first = concurrency_group(self.workflow, "pull_request", 100, pull_request=25, action="labeled", label="bug")
        second = concurrency_group(self.workflow, "pull_request", 101, pull_request=25, action="labeled", label="bug")
        maestro = concurrency_group(self.workflow, "pull_request", 102, pull_request=25, action="labeled", label="run-maestro")
        self.assertEqual("quality-gate-quality-gate-noop-100", first)
        self.assertEqual("quality-gate-quality-gate-noop-101", second)
        self.assertNotEqual(first, second)
        self.assertEqual(maestro, "quality-gate-quality-gate-25")

    def test_dispatched_validations_group_only_with_their_own_run(self):
        dispatched = concurrency_group(self.workflow, "workflow_dispatch", 100)
        self.assertEqual(dispatched, concurrency_group(self.workflow, "workflow_dispatch", 100))
        self.assertNotEqual(dispatched, concurrency_group(self.workflow, "workflow_dispatch", 101))
        self.assertNotEqual(dispatched, concurrency_group(self.workflow, "pull_request", 101, pull_request=100))
        self.assertNotEqual(dispatched, concurrency_group(self.workflow, "schedule", 101))

    def test_successive_pushes_still_supersede_each_other_per_ref(self):
        first = concurrency_group(self.workflow, "push", 100)
        self.assertEqual("quality-gate-quality-gate-refs/heads/main", first)
        self.assertEqual(first, concurrency_group(self.workflow, "push", 101))
        self.assertEqual(first, concurrency_group(self.workflow, "schedule", 102))
        self.assertNotEqual(first, concurrency_group(self.workflow, "push", 103, ref="refs/heads/other"))

    def test_the_fork_guard_reports_the_read_only_policy_for_every_pull_request(self):
        fork_guard = job_block(self.workflow, "fork_guard")
        self.assertEqual("fork-guard", jobs_of(self.workflow)["fork_guard"][0])
        self.assertEqual(
            "${{ always() && github.event_name == 'pull_request' && (github.event.action != 'labeled' "
            "|| github.event.label.name == 'run-maestro') }}",
            job_key(fork_guard, "if"),
        )
        self.assertIn("github.event.pull_request.head.repo.full_name", fork_guard)
        self.assertIn("read-only and secret-free", fork_guard)
        self.assertIn("must carry the change onto an in-repository branch", fork_guard)
        self.assertNotIn("actions/checkout@", fork_guard)

    def test_a_draft_pull_request_runs_the_lightweight_validation_only(self):
        draft = job_block(self.workflow, "draft_validation")
        self.assertEqual("draft-validation", jobs_of(self.workflow)["draft_validation"][0])
        self.assertEqual(
            "${{ always() && github.event_name == 'pull_request' && (github.event.action != 'labeled' "
            "|| github.event.label.name == 'run-maestro') && !github.event.pull_request.head.repo.fork }}",
            job_key(draft, "if"),
            "the lightweight validation belongs to every in-repository pull request",
        )
        self.assertIn("./gradlew formatCheck :app:lintDebug --no-daemon\n", draft)
        self.assertNotIn("coverageVerify", draft)
        self.assertNotIn("verifyRoborazziDebug", draft)

    def test_a_ready_pull_request_runs_the_complete_gate(self):
        jobs = jobs_of(self.workflow)
        for job in HEAVY_JOBS:
            self.assertIn(job, jobs, f"{job} must have a job")
            self.assertEqual(HEAVY_JOBS_RUN, jobs[job][1], f"{job} must run for a ready in-repository pull request")
            self.assertEqual(
                "[ui_paths]",
                job_key(job_block(self.workflow, job), "needs"),
                f"{job} must wait for the pull-request decisions",
            )
        coverage_mutation_gate = (
            "${{ always() && (github.event.action != 'labeled' || "
            "github.event.label.name == 'run-maestro') && "
            "(github.event_name != 'pull_request' || "
            "(!github.event.pull_request.draft && !github.event.pull_request.head.repo.fork && "
            "(contains(github.event.pull_request.labels.*.name, 'run-maestro') || "
            "(needs.ui_paths.outputs.docs_only_changed != 'true' && "
            "needs.ui_paths.outputs.dependency_files_changed != 'true')))) }}"
        )
        self.assertEqual(coverage_mutation_gate, jobs["coverage_mutation"][1])
        self.assertEqual(
            "[ui_paths]",
            job_key(job_block(self.workflow, "coverage_mutation"), "needs"),
            "coverage_mutation must wait for the pull-request decisions",
        )
        aggregate = job_block(self.workflow, "quality-gate")
        self.assertIn('expected_state=success', aggregate)
        label_guard = "(github.event.action != 'labeled' || github.event.label.name == 'run-maestro')"
        ui_path_gate = "needs.ui_paths.outputs.ui_paths_changed == 'true'"
        emulator_gate = (
            "github.event_name == 'schedule' || github.event_name == 'workflow_dispatch' || "
            "(github.event_name == 'pull_request' && !github.event.pull_request.draft && "
            "!github.event.pull_request.head.repo.fork && "
            "(contains(github.event.pull_request.labels.*.name, 'run-maestro') || " + ui_path_gate + "))"
        )
        self.assertEqual(
            "${{ always() && " + label_guard + " && (" + emulator_gate + ") }}",
            job_key(job_block(self.workflow, "api24_instrumentation"), "if"),
        )
        self.assertEqual(
            "${{ always() && needs.api24_instrumentation.result == 'success' && "
            + label_guard
            + " && ("
            + emulator_gate
            + ") }}",
            job_key(job_block(self.workflow, "maestro_journeys"), "if"),
        )
        self.assertEqual(
            "[ui_paths]",
            job_key(job_block(self.workflow, "api24_instrumentation"), "needs"),
        )
        self.assertEqual(
            "[api24_instrumentation, ui_paths]",
            job_key(job_block(self.workflow, "maestro_journeys"), "needs"),
        )
        self.assertIn('expected_state=skipped', aggregate)
        self.assertIn('if [ "$EVENT_BASE_REF" != main ]', aggregate)
        self.assertIn('if [ "$EVENT_FORK" = true ]', aggregate)
        self.assertIn('if [ "$EVENT_DRAFT" = true ]', aggregate)

    def test_the_ui_path_detector_routes_the_emulator_suites_on_the_declared_ui_surface(self):
        ui_paths = job_block(self.workflow, "ui_paths")
        self.assertEqual("ui-paths", jobs_of(self.workflow)["ui_paths"][0])
        self.assertEqual(
            "${{ always() && github.event_name == 'pull_request' && (github.event.action != 'labeled' "
            "|| github.event.label.name == 'run-maestro') }}",
            job_key(ui_paths, "if"),
            "the detector belongs to the same pull-request activities as the fork guard",
        )
        self.assertIn(
            "    permissions:\n      contents: read\n      pull-requests: read\n",
            ui_paths,
            "the detector reads the pull request files with a read-only token",
        )
        self.assertIn(
            "uses: dorny/paths-filter@0e4a8c6effa4802afeda77dc8d303f8176d7dfad",
            ui_paths,
            "the detector action must be pinned to an immutable revision",
        )
        self.assertIn("      ui_paths_changed: ${{ steps.ui_paths.outputs.ui_paths }}\n", ui_paths)
        filters = ui_paths.split("          filters: |\n", 1)[1].split("\n      - name:", 1)[0]
        self.assertEqual(
            [
                "app/src/main/**",
                "feature/entry/presentation/**",
                "feature/entry/wiring/src/main/res/**",
                "fixtures/hermes/journey/**",
            ],
            re.findall(r"^              - '(.+)'$", filters, re.MULTILINE),
        )
        aggregate = job_block(self.workflow, "quality-gate")
        self.assertIn("      - ui_paths\n", aggregate)
        self.assertIn("UI_PATHS_RESULT: ${{ needs.ui_paths.result }}", aggregate)
        self.assertIn("UI_PATHS_CHANGED: ${{ needs.ui_paths.outputs.ui_paths_changed }}", aggregate)
        self.assertIn('|| "$ui_paths_decision" == true ]]; then', aggregate)

    def test_the_docs_only_detector_skips_the_heavy_lanes_for_a_documentation_head(self):
        ui_paths = job_block(self.workflow, "ui_paths")
        self.assertEqual(
            2,
            ui_paths.count("uses: dorny/paths-filter@0e4a8c6effa4802afeda77dc8d303f8176d7dfad"),
            "the changed-file detector must reuse the same pinned paths-filter revision",
        )
        self.assertIn("      docs_only_changed: ${{ steps.docs_only.outputs.docs_only }}\n", ui_paths)
        changed_files = ui_paths.split("      - name: Detect the changed files of the pull request\n", 1)[1].split("      - name: ", 1)[0]
        self.assertIn("          list-files: json\n", changed_files, "the changed-file answer must be a file list, not a filter match")
        self.assertIn(
            "            all:\n              - '**'\n",
            changed_files,
            "the detector must enumerate every changed file so an unknown path cannot pass as documentation",
        )
        classifier = ui_paths.split("      - name: Classify the docs-only decision\n", 1)[1].split("      - name: ", 1)[0]
        self.assertIn(
            "CHANGED_FILES: ${{ steps.changed_files.outputs.all_files }}",
            classifier,
            "the classifier must consume the file-list output, not the boolean filter match",
        )
        self.assertIn('if "/.github/" in "/" + path:', classifier, "a control-plane change must never classify as documentation")
        self.assertIn('path.startswith("docs/")', classifier)
        self.assertIn('path.endswith(".md")', classifier)
        self.assertIn("bool(changed_files) and all(", classifier, "an empty changed-file answer must fail closed to the full gate")
        self.assertIn("failing closed to the full gate", classifier)
        self.assertIn('printf \'%s\\n\' "docs_only=$decision" >> "$GITHUB_OUTPUT"', classifier)
        for job in HEAVY_JOBS:
            block = job_block(self.workflow, job)
            condition = job_key(block, "if")
            assert condition is not None, f"{job} must declare a condition"
            self.assertIn(
                "(contains(github.event.pull_request.labels.*.name, 'run-maestro') || "
                "needs.ui_paths.outputs.docs_only_changed != 'true')",
                condition,
                f"{job} must skip a docs-only head unless the run-maestro label keeps precedence",
            )
        coverage_condition = job_key(job_block(self.workflow, "coverage_mutation"), "if")
        assert coverage_condition is not None, "coverage_mutation must declare a condition"
        self.assertIn(
            "(needs.ui_paths.outputs.docs_only_changed != 'true' && "
            "needs.ui_paths.outputs.dependency_files_changed != 'true')",
            coverage_condition,
            "the coverage lane must also skip a dependency-only head",
        )
        aggregate = job_block(self.workflow, "quality-gate")
        self.assertIn("DOCS_ONLY_CHANGED: ${{ needs.ui_paths.outputs.docs_only_changed }}", aggregate)
        self.assertIn('Docs-only detection must report true or false for this run', aggregate)
        self.assertIn(
            'if [ "$docs_only_decision" = true ] && [[ "${EVENT_LABELS:-}" != *\'"run-maestro"\'* ]]; then',
            aggregate,
            "the aggregate must expect the heavy lanes to skip exactly on an unlabeled docs-only head",
        )
        self.assertIn("expected_state=skipped", aggregate)
        self.assertIn("expected_state=success", aggregate)
        documentation = (ROOT / "docs/quality-gates.md").read_text()
        routing = (ROOT / "docs/protected-main-workflow.md").read_text()
        for declared in ("`docs/**`", "outside `.github/`", "`run-maestro` label keeps precedence"):
            self.assertIn(declared, documentation, f"quality-gates.md must declare the docs-only rule: {declared}")
            self.assertIn(declared, routing, f"protected-main-workflow.md must declare the docs-only rule: {declared}")

    def docs_only_python(self):
        classifier = (
            job_block(self.workflow, "ui_paths")
            .split("      - name: Classify the docs-only decision\n", 1)[1]
            .split("      - name: ", 1)[0]
        )
        start = classifier.index("python3 -c '") + len("python3 -c '")
        raw = classifier[start : classifier.index("\n          ')", start)]
        return "\n".join(line[10:] if line.startswith(" " * 10) else line for line in raw.splitlines())

    def docs_only_decision(self, changed_files):
        environment = dict(os.environ)
        environment["CHANGED_FILES"] = (
            changed_files if isinstance(changed_files, str) else json.dumps(changed_files)
        )
        return subprocess.run(
            ["python3", "-c", self.docs_only_python()],
            capture_output=True,
            text=True,
            env=environment,
            check=False,
        )

    def test_the_docs_only_classifier_accepts_only_the_declared_documentation_surface(self):
        for changed in (
            ["docs/quality-gates.md"],
            ["README.md"],
            ["a.md", "docs/img.png", "deep/nested/guide.md"],
        ):
            completed = self.docs_only_decision(changed)
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertEqual("true", completed.stdout.strip())

    def test_the_docs_only_classifier_fails_closed_on_every_non_documentation_head(self):
        for changed in (
            ["docs/quality-gates.md", "app/src/main/EntryViewModel.kt"],
            [".github/workflows/quality-gate.yml"],
            [".github/PULL_REQUEST_TEMPLATE.md"],
            ["docs/nested/.github/trap.md"],
            ["build.gradle.kts"],
            ["gradle/libs.versions.toml"],
            [],
        ):
            completed = self.docs_only_decision(changed)
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertEqual("false", completed.stdout.strip(), f"{changed} must route the full gate")

    def test_the_docs_only_classifier_fails_closed_on_an_unreadable_changed_file_answer(self):
        for raw in ("not json", "", "[42]", '{"path": "docs/x.md"}'):
            completed = self.docs_only_decision(raw)
            self.assertEqual(1, completed.returncode, f"an unreadable answer {raw!r} must fail the detector")
            self.assertIn("failing closed to the full gate", completed.stderr)

    def test_the_aggregate_gate_runs_on_non_maestro_label_runs_and_mirrors_the_last_completed_gate(self):
        aggregate = job_block(self.workflow, "quality-gate")
        self.assertEqual(
            "${{ always() }}",
            job_key(aggregate, "if"),
            "the aggregate must also run on non-maestro label runs so it can re-report the last completed gate",
        )
        self.assertIn(
            "    permissions:\n      contents: read\n      checks: read\n",
            aggregate,
            "the mirror reads check runs with a read-only token scope",
        )
        mirror = aggregate.split("      - name: Mirror the newest completed quality-gate decision\n", 1)[1].split("      - name: ", 1)[0]
        self.assertEqual(
            "        if: ${{ github.event.action == 'labeled' && github.event.label.name != 'run-maestro' }}\n",
            mirror.split("        env:\n", 1)[0],
            "the mirror belongs to non-maestro label runs only",
        )
        self.assertEqual(1, aggregate.count("curl "), "the mirror makes exactly one API call")
        self.assertIn(
            '"${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/commits/${HEAD_SHA}/check-runs?check_name=quality-gate&per_page=100"',
            mirror,
        )
        self.assertIn("Bearer ${GH_TOKEN}", mirror)
        self.assertIn("details_url", mirror)
        self.assertIn(
            "      - name: Verify declared checks and results\n"
            "        if: ${{ github.event.action != 'labeled' || github.event.label.name == 'run-maestro' }}\n",
            aggregate,
            "the declared-check verification stays a real-run step",
        )

    def mirror_python(self):
        aggregate = job_block(self.workflow, "quality-gate")
        mirror = aggregate.split("      - name: Mirror the newest completed quality-gate decision\n", 1)[1].split("      - name: ", 1)[0]
        start = mirror.index("python3 -c '") + len("python3 -c '")
        raw = mirror[start : mirror.index("\n          '\n", start)]
        return "\n".join(line[10:] if line.startswith(" " * 10) else line for line in raw.splitlines())

    def mirror_decision(self, check_runs, current_run_id="424242"):
        environment = dict(os.environ)
        environment["MIRROR_CURRENT_RUN_ID"] = current_run_id
        return subprocess.run(
            ["python3", "-c", self.mirror_python()],
            input=json.dumps({"total_count": len(check_runs), "check_runs": check_runs}),
            capture_output=True,
            text=True,
            env=environment,
            check=False,
        )

    def check_run(self, identifier, conclusion=None, status="completed", run_id=None, completed_at="2026-10-17T12:00:00Z"):
        check = {
            "id": identifier,
            "status": status,
            "completed_at": completed_at,
            "details_url": (
                f"https://github.com/Gvetri/hermes-native-client/actions/runs/{run_id}"
                if run_id
                else "https://example.com/not-an-actions-run"
            ),
        }
        if conclusion is not None:
            check["conclusion"] = conclusion
        return check

    def test_a_green_head_stays_passing_when_a_label_arrives(self):
        completed = self.mirror_decision([self.check_run(7, conclusion="success", run_id=39027900483)])
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Mirrored the newest completed quality-gate check run", completed.stdout)

    def test_a_failed_head_stays_blocked_when_a_label_arrives(self):
        completed = self.mirror_decision([self.check_run(7, conclusion="failure", run_id=39027891206)])
        self.assertEqual(1, completed.returncode)
        self.assertIn("concluded 'failure'", completed.stderr)

    def test_an_external_fork_head_stays_blocked_when_a_label_arrives(self):
        completed = self.mirror_decision([self.check_run(7, conclusion="failure", run_id=39027891206)])
        self.assertEqual(1, completed.returncode)
        self.assertIn("the head is not validated", completed.stderr)

    def test_a_head_without_a_completed_gate_fails_closed_when_a_label_arrives(self):
        for check_runs in (
            [],
            [self.check_run(7, status="in_progress", run_id=39027900483)],
            [self.check_run(7, conclusion="success", run_id=424242)],
        ):
            completed = self.mirror_decision(check_runs)
            self.assertEqual(1, completed.returncode, completed.stderr)
            self.assertIn("failing closed", completed.stderr)

    def test_the_mirror_takes_the_newest_completed_gate_from_another_run(self):
        older_success = self.check_run(7, conclusion="success", run_id=39027900483, completed_at="2026-10-17T12:01:43Z")
        newer_failure = self.check_run(8, conclusion="failure", run_id=39027891206, completed_at="2026-10-17T13:00:00Z")
        older_failure = self.check_run(7, conclusion="failure", run_id=39027891206, completed_at="2026-10-17T12:01:43Z")
        newer_success = self.check_run(8, conclusion="success", run_id=39027900483, completed_at="2026-10-17T13:00:00Z")
        self.assertEqual(1, self.mirror_decision([older_success, newer_failure]).returncode)
        self.assertEqual(1, self.mirror_decision([newer_failure, older_success]).returncode)
        self.assertEqual(0, self.mirror_decision([older_failure, newer_success]).returncode)
        same_time_success = self.check_run(9, conclusion="success", run_id=39027900483, completed_at="2026-10-17T13:00:00Z")
        same_time_failure = self.check_run(10, conclusion="failure", run_id=39027891206, completed_at="2026-10-17T13:00:00Z")
        self.assertEqual(1, self.mirror_decision([same_time_success, same_time_failure]).returncode)
        self.assertEqual(
            1,
            self.mirror_decision([self.check_run(7, conclusion="success")]).returncode,
            "a check run that cannot be attributed to another run must not satisfy the mirror",
        )

    def test_the_mirror_accepts_the_documented_pass_conclusions(self):
        for conclusion in ("success", "neutral", "skipped"):
            completed = self.mirror_decision([self.check_run(7, conclusion=conclusion, run_id=39027900483)])
            self.assertEqual(0, completed.returncode, completed.stderr)
        for conclusion in ("failure", "cancelled", "timed_out", "action_required"):
            self.assertEqual(1, self.mirror_decision([self.check_run(7, conclusion=conclusion, run_id=39027900483)]).returncode)

    def test_the_commit_message_check_runs_for_a_ready_in_repository_pull_request(self):
        commit_message = job_block(self.workflow, "commit_message")
        self.assertEqual("commit-message", jobs_of(self.workflow)["commit_message"][0])
        self.assertIn("run: python3 .github/scripts/verify-conventional-commits.py\n", commit_message)
        self.assertIn(
            "    permissions:\n      contents: read\n      pull-requests: read\n",
            commit_message,
            "the check reads the pull request and its commits",
        )
        self.assertIn("      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262\n", commit_message)
        self.assertIn("          persist-credentials: false\n", commit_message)
        self.assertEqual(
            "${{ always() && github.event_name == 'pull_request' && (github.event.action != 'labeled' "
            "|| github.event.label.name == 'run-maestro') && !github.event.pull_request.head.repo.fork "
            "&& !github.event.pull_request.draft }}",
            job_key(commit_message, "if"),
        )

    def test_the_conformance_check_reads_the_repository_configuration_back(self):
        conformance = job_block(self.workflow, "conformance")
        self.assertEqual("conformance", jobs_of(self.workflow)["conformance"][0])
        self.assertIn("    permissions:\n      contents: read\n", conformance)
        self.assertNotIn("administration:", self.workflow, "an undocumented scope invalidates the workflow file")
        self.assertIn("run: python3 .github/scripts/verify-repository-conformance.py\n", conformance)
        self.assertEqual(CONFORMANCE_RUN, job_key(conformance, "if"))

    def test_the_pull_request_triggers_re_evaluate_the_head_on_every_declared_activity(self):
        triggers = self.workflow.split("\non:\n", 1)[1].split("\npermissions:\n", 1)[0]
        self.assertIn("  pull_request:\n    types:\n", triggers)
        for activity in ("opened", "synchronize", "reopened", "ready_for_review", "labeled"):
            self.assertIn(f"      - {activity}\n", triggers, f"{activity} must trigger the workflow")
        for inactive in ("converted_to_draft", "edited"):
            self.assertNotIn(
                f"      - {inactive}\n",
                triggers,
                f"{inactive} must not re-run the gate on an unchanged head",
            )

    def test_every_declared_token_scope_is_a_documented_one(self):
        documented = {
            "actions", "artifact-metadata", "attestations", "checks", "code-quality", "contents",
            "deployments", "discussions", "id-token", "issues", "models", "packages", "pages",
            "pull-requests", "security-events", "statuses", "vulnerability-alerts",
        }
        requested = re.findall(r"^ +([a-z-]+): (?:read|write|none)$", self.workflow, re.MULTILINE)
        self.assertTrue(requested, "the workflow must declare its token scopes")
        self.assertEqual(set(), set(requested) - documented)

    def test_checkout_steps_stay_immutable_and_credential_free(self):
        jobs = jobs_of(self.workflow)
        with_checkout = [job for job in jobs if "actions/checkout@" in job_block(self.workflow, job)]
        self.assertEqual(sorted(set(jobs) - {"fork_guard", "ui_paths"}), sorted(with_checkout))
        self.assertEqual(len(DECLARED_CHECKS) + 3, len(jobs))
        self.assertIn("quality-gate", with_checkout)
        for job, block in ((job, job_block(self.workflow, job)) for job in jobs):
            keys = [line.strip().split(":", 1)[0] for line in block.splitlines() if re.match(r"^    [a-z0-9_\\-]+: ", line)]
            self.assertEqual(len(keys), len(set(keys)), f"{job} must not declare a key twice")
        for job in with_checkout:
            block = job_block(self.workflow, job)
            for line in block.splitlines():
                if line.strip().startswith("- uses: actions/checkout@"):
                    reference = line.strip().split("actions/checkout@", 1)[1]
                    self.assertRegex(reference, r"^[0-9a-f]{40}$", f"{job} must pin the checkout action")
            checkout_at = block.find("actions/checkout@")
            step_end = block.find("      - ", block.find("\n", checkout_at))
            step = block[checkout_at : step_end if step_end != -1 else len(block)]
            self.assertIn("persist-credentials: false", step, f"{job} must not persist credentials")


class TestVerifyConventionalCommits(unittest.TestCase):
    """The commit-message check, against a local GitHub API stand-in."""

    def setUp(self):
        self.routes = {}
        self.event = {"pull_request": {"number": 25, "title": "feat: protect main"}}
        self.api = GitHubApiFixture(self.routes)
        self.api.start()
        self.addCleanup(self.api.stop)

    def run_check(self, event_name="pull_request", payload=None):
        with tempfile.TemporaryDirectory() as directory:
            event_path = Path(directory) / "event.json"
            event_path.write_text(json.dumps(payload if payload is not None else self.event))
            environment = dict(os.environ)
            environment.update(
                {
                    "GITHUB_REPOSITORY": "Gvetri/hermes-native-client",
                    "GITHUB_EVENT_NAME": event_name,
                    "GITHUB_EVENT_PATH": str(event_path),
                    "GITHUB_API_URL": self.api.url,
                    "GITHUB_TOKEN": "test-token",
                }
            )
            return subprocess.run(
                ["python3", str(COMMIT_SCRIPT)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                env=environment,
                check=False,
            )

    def commits(self, messages):
        return [{"sha": f"{index:040x}", "commit": {"message": message}} for index, message in enumerate(messages, start=1)]

    def test_a_conventional_title_and_commits_pass(self):
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25/commits"] = self.commits(
            ["feat: protect main", "fix(ci): pin the concurrency group\n\nBody text.", "Merge branch 'main' into feat/25"]
        )
        completed = self.run_check()
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Commit-message verification passed.", completed.stdout)

    def test_an_unconventional_title_fails(self):
        self.event["pull_request"]["title"] = "protect main"
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25/commits"] = self.commits(["feat: protect main"])
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("The squash-merge title", completed.stderr)
        self.assertIn("is not a Conventional Commit", completed.stderr)

    def test_an_undeclared_type_and_an_uppercase_scope_fail(self):
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25/commits"] = self.commits(
            ["feature: protect main", "fix(CI): pin the group", "fix: protect main."]
        )
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("'feature' is not a declared commit type", completed.stderr)
        self.assertIn("the scope 'CI' must be lower case", completed.stderr)
        self.assertIn("must not end with a period", completed.stderr)

    def test_an_empty_commit_list_fails_closed(self):
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25/commits"] = []
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("failed closed", completed.stderr)

    def test_an_unavailable_api_fails_closed(self):
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("GitHub API answered 404", completed.stderr)

    def test_the_checker_validates_the_live_title_from_the_api(self):
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25"] = {
            "number": 25,
            "title": "protect main",
        }
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25/commits"] = self.commits(
            ["feat: protect main"]
        )
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("The squash-merge title", completed.stderr)
        self.assertIn("'protect main' is not a Conventional Commit", completed.stderr)

    def test_an_unavailable_title_api_falls_back_to_the_payload_title(self):
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25/commits"] = self.commits(
            ["feat: protect main"]
        )
        completed = self.run_check()
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("/repos/Gvetri/hermes-native-client/pulls/25", self.api.requests)
        self.assertIn("Commit-message verification passed.", completed.stdout)

    def test_a_payload_title_that_differs_from_the_live_title_passes(self):
        self.event["pull_request"]["title"] = "protect main"
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25"] = {
            "number": 25,
            "title": "chore: commit-message gate validates the live PR title",
        }
        self.routes["/repos/Gvetri/hermes-native-client/pulls/25/commits"] = self.commits(
            ["feat: protect main"]
        )
        completed = self.run_check()
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Commit-message verification passed.", completed.stdout)

    def test_a_push_validates_the_commits_it_introduced(self):
        self.routes["/repos/Gvetri/hermes-native-client/compare/aaa...bbb"] = {
            "total_commits": 2,
            "commits": self.commits(["feat: protect main (#25)", "docs: describe the protected-main workflow"]),
        }
        completed = self.run_check(event_name="push", payload={"before": "aaa", "after": "bbb"})
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Verified 2 pushed commit message(s).", completed.stdout)

    def test_a_truncated_push_comparison_fails_closed(self):
        self.routes["/repos/Gvetri/hermes-native-client/compare/aaa...bbb"] = {
            "total_commits": 251,
            "commits": self.commits(["feat: protect main"]),
        }
        completed = self.run_check(event_name="push", payload={"before": "aaa", "after": "bbb"})
        self.assertEqual(1, completed.returncode)
        self.assertIn("a truncated answer cannot be verified", completed.stderr)

    def test_an_unsupported_event_fails_closed(self):
        completed = self.run_check(event_name="schedule")
        self.assertEqual(1, completed.returncode)
        self.assertIn("supports pull_request and push events", completed.stderr)


class TestVerifyRepositoryConformance(unittest.TestCase):
    """The conformance check, against a local GitHub API stand-in."""

    def setUp(self):
        self.routes = {
            "/repos/Gvetri/hermes-native-client": {"default_branch": "main"},
            "/repos/Gvetri/hermes-native-client/rules/branches/main": [
                {"type": "deletion"},
                {"type": "non_fast_forward"},
                {
                    "type": "pull_request",
                    "parameters": {
                        "allowed_merge_methods": ["squash"],
                        "required_approving_review_count": 0,
                        "dismiss_stale_reviews_on_push": True,
                    },
                },
                {
                    "type": "required_status_checks",
                    "parameters": {
                        "strict_required_status_checks_policy": True,
                        "required_status_checks": [{"context": "quality-gate", "integration_id": 15368}],
                    },
                },
            ],
            "/repos/Gvetri/hermes-native-client/rulesets": [
                {"id": 22461074, "name": "Main", "target": "branch", "enforcement": "active"}
            ],
            "/repos/Gvetri/hermes-native-client/rulesets/22461074": {"id": 22461074, "name": "Main", "bypass_actors": []},
            "/repos/Gvetri/hermes-native-client/actions/permissions/workflow": {
                "default_workflow_permissions": "read",
                "can_approve_pull_request_reviews": False,
            },
            "/repos/Gvetri/hermes-native-client/actions/permissions/fork-pr-contributor-approval": {
                "approval_policy": "all_external_contributors"
            },
        }
        self.owner_routes = {
            "/repos/Gvetri/hermes-native-client": {
                "default_branch": "main",
                "allow_squash_merge": True,
                "allow_merge_commit": False,
                "allow_rebase_merge": False,
                "delete_branch_on_merge": True,
            },
        }
        self.api = GitHubApiFixture(self.routes)
        self.api.start()
        self.addCleanup(self.api.stop)

    def run_check(self, api_url=None, owner=False, workflow=None):
        if owner:
            self.routes.update(self.owner_routes)
        environment = dict(os.environ)
        environment.update(
            {
                "GITHUB_REPOSITORY": "Gvetri/hermes-native-client",
                "GITHUB_API_URL": api_url or self.api.url,
                "GITHUB_TOKEN": "test-token",
            }
        )
        script = CONFORMANCE_SCRIPT
        if workflow is not None:
            directory = tempfile.TemporaryDirectory()
            self.addCleanup(directory.cleanup)
            copied_root = Path(directory.name)
            for source in (CONFORMANCE_SCRIPT, WORKFLOW, REQUIRED_CHECKS):
                target = copied_root / source.relative_to(ROOT)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
            (copied_root / WORKFLOW.relative_to(ROOT)).write_text(workflow)
            script = copied_root / CONFORMANCE_SCRIPT.relative_to(ROOT)
        command = ["python3", str(script)]
        if owner:
            command.append("--owner")
        return subprocess.run(
            command,
            cwd=ROOT,
            capture_output=True,
            text=True,
            env=environment,
            check=False,
        )

    def test_the_declared_configuration_passes(self):
        completed = self.run_check()
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Verified the 4 enforced rule(s) protecting main", completed.stdout)
        self.assertIn("Repository conformance passed.", completed.stdout)

    def test_removing_dispatch_isolation_fails_conformance(self):
        workflow = re.sub(
            r"^  group: .+$",
            "  group: quality-gate-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}",
            WORKFLOW.read_text(),
            flags=re.MULTILINE,
        )
        completed = self.run_check(workflow=workflow)
        self.assertEqual(1, completed.returncode)
        self.assertEqual(
            "The concurrency group must isolate dispatched run ids and supersede runs per pull request or ref\n"
            "Repository conformance failed.\n",
            completed.stderr,
        )

    def test_removing_cancellation_fails_conformance(self):
        workflow = WORKFLOW.read_text().replace("  cancel-in-progress: true\n", "")
        completed = self.run_check(workflow=workflow)
        self.assertEqual(1, completed.returncode)
        self.assertEqual("The workflow must cancel an obsolete run\nRepository conformance failed.\n", completed.stderr)

    def test_a_weakened_rule_fails(self):
        self.routes["/repos/Gvetri/hermes-native-client/rules/branches/main"] = [
            rule
            for rule in self.routes["/repos/Gvetri/hermes-native-client/rules/branches/main"]
            if rule["type"] != "non_fast_forward"
        ]
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("must prohibit force pushes", completed.stderr)

    def test_an_extra_required_status_fails(self):
        self.routes["/repos/Gvetri/hermes-native-client/rules/branches/main"][3]["parameters"]["required_status_checks"] = [
            {"context": "quality-gate"},
            {"context": "optional-report"},
        ]
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("must require exactly the 'quality-gate' status", completed.stderr)

    def test_a_bypass_actor_fails(self):
        self.routes["/repos/Gvetri/hermes-native-client/rulesets/22461074"] = {
            "id": 22461074,
            "name": "Main",
            "bypass_actors": [{"actor_type": "RepositoryRole", "bypass_mode": "always"}],
        }
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("grants a bypass", completed.stderr)

    def test_a_write_capable_workflow_token_fails(self):
        self.routes["/repos/Gvetri/hermes-native-client/actions/permissions/workflow"] = {
            "default_workflow_permissions": "write",
            "can_approve_pull_request_reviews": True,
        }
        completed = self.run_check(owner=True)
        self.assertEqual(1, completed.returncode)
        self.assertIn("default workflow token must be read-only", completed.stderr)
        self.assertIn("must not be able to approve pull requests", completed.stderr)

    def test_an_unapproved_fork_policy_fails(self):
        self.routes["/repos/Gvetri/hermes-native-client/actions/permissions/fork-pr-contributor-approval"] = {
            "approval_policy": "first_time_contributors"
        }
        completed = self.run_check(owner=True)
        self.assertEqual(1, completed.returncode)
        self.assertIn("must wait for maintainer approval", completed.stderr)

    def test_the_pull_request_run_needs_no_administration_access(self):
        for route in (
            "/repos/Gvetri/hermes-native-client/actions/permissions/workflow",
            "/repos/Gvetri/hermes-native-client/actions/permissions/fork-pr-contributor-approval",
        ):
            del self.routes[route]
        completed = self.run_check()
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Repository conformance passed.", completed.stdout)

    def test_the_owner_run_verifies_the_actions_settings(self):
        completed = self.run_check(owner=True)
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Verified the squash-only merge policy and branch deletion.", completed.stdout)
        self.assertIn("Verified the read-only default workflow token.", completed.stdout)
        self.assertIn("Verified that an external-fork run requires maintainer approval.", completed.stdout)

    def test_an_enabled_merge_commit_fails_the_owner_run(self):
        self.owner_routes["/repos/Gvetri/hermes-native-client"]["allow_merge_commit"] = True
        completed = self.run_check(owner=True)
        self.assertEqual(1, completed.returncode)
        self.assertIn("must disable allow_merge_commit", completed.stderr)

    def test_an_unreadable_actions_setting_fails_closed_in_the_owner_run(self):
        del self.routes["/repos/Gvetri/hermes-native-client/actions/permissions/workflow"]
        completed = self.run_check(owner=True)
        self.assertEqual(1, completed.returncode)
        self.assertIn("failed closed", completed.stderr)

    def test_an_unprotected_branch_fails(self):
        self.routes["/repos/Gvetri/hermes-native-client/rules/branches/main"] = []
        completed = self.run_check()
        self.assertEqual(1, completed.returncode)
        self.assertIn("failed closed", completed.stderr)
        self.assertIn("a direct push to it is allowed", completed.stderr)

    def test_an_unavailable_api_fails_closed(self):
        url = self.api.url
        self.api.stop()
        completed = self.run_check(api_url=url)
        self.assertEqual(1, completed.returncode)
        self.assertIn("failed closed", completed.stderr)


class GitHubApiFixtureTest(unittest.TestCase):
    def setUp(self):
        self.routes = {}
        self.api = GitHubApiFixture(self.routes)
        self.api.start()
        self.addCleanup(self.api.stop)

    def request(self, path, method="GET", payload=None):
        data = json.dumps(payload).encode() if payload is not None else None
        request = urllib.request.Request(self.api.url + path, data=data, method=method)
        try:
            return urllib.request.urlopen(request, timeout=5)
        except urllib.error.HTTPError as response:
            return response

    def test_get_routes_keep_exact_query_precedence_and_original_dictionary(self):
        self.routes.update({"/issues": lambda: {"fallback": True},
                            "/issues?page=2": {"exact": True}})
        self.api.routes = {"/issues": {"replacement": True}}
        for path, expected in (("/issues?page=2", {"exact": True}),
                               ("/issues?page=3", {"fallback": True})):
            with self.request(path) as response:
                self.assertEqual(200, response.status)
                self.assertEqual("application/json", response.headers["Content-Type"])
                body = response.read()
                self.assertEqual(str(len(body)), response.headers["Content-Length"])
                self.assertEqual(expected, json.loads(body))
        self.assertEqual(["/issues?page=2", "/issues?page=3"], self.api.requests)

    def test_get_status_tuples_and_missing_routes_preserve_http_errors(self):
        self.routes["/denied"] = lambda: (403, {"message": "Denied"})
        for path, status, expected in (("/denied", 403, {"message": "Denied"}),
                                       ("/missing?raw=true", 404, {"message": "Not Found"})):
            with self.request(path) as response:
                self.assertEqual(status, response.status)
                self.assertEqual("application/json", response.headers["Content-Type"])
                self.assertEqual(expected, json.load(response))
        self.assertEqual(["/denied", "/missing?raw=true"], self.api.requests)

    def test_mutations_record_method_path_and_payload_even_for_missing_routes(self):
        self.routes[("POST", "/issues?raw=true")] = lambda payload: (201, payload)
        issue = {"state": "open"}

        def update(payload):
            issue.update(payload)
            return 200, issue

        self.routes[("PATCH", "/issues/9")] = update
        self.routes[("POST", "/dispatch")] = 204
        self.routes[("DELETE", "/issues/9")] = lambda: 204
        cases = (
            ("POST", "/issues?raw=true", {"title": "Created"}, 201, {"title": "Created"}),
            ("PATCH", "/issues/9", {"state": "closed"}, 200, {"state": "closed"}),
            ("POST", "/dispatch", {"ref": "main"}, 204, None),
            ("PATCH", "/missing?raw=true", {"state": "open"}, 404, None),
            ("DELETE", "/issues/9", None, 204, None),
            ("DELETE", "/missing?raw=true", None, 404, None),
        )
        for method, path, payload, status, expected in cases:
            with self.request(path, method, payload) as response:
                self.assertEqual(status, response.status)
                body = response.read()
                self.assertEqual(json.dumps(expected).encode() if expected is not None else b"", body)
                if method != "DELETE":
                    self.assertEqual("application/json", response.headers["Content-Type"])
                    self.assertEqual(str(len(body)), response.headers["Content-Length"])
        self.assertEqual([("/issues?raw=true", {"title": "Created"}),
                          ("/dispatch", {"ref": "main"})], self.api.posts)
        self.assertEqual({"state": "closed"}, issue)
        self.assertEqual([("/issues/9", {"state": "closed"}),
                          ("/missing?raw=true", {"state": "open"})], self.api.patches)
        self.assertEqual(["/issues/9", "/missing?raw=true"], self.api.deletes)
        self.assertEqual([], self.api.requests)

    def test_stop_is_idempotent_and_joins_the_server_thread(self):
        thread = self.api.thread
        self.assertTrue(thread.is_alive())
        self.api.stop()
        self.assertFalse(thread.is_alive())
        self.assertIsNone(self.api.server)
        self.api.stop()


if __name__ == "__main__":
    unittest.main()
