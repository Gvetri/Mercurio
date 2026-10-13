from datetime import datetime, timezone
from pathlib import Path
import runpy


class ValidationClock:
    def __init__(self):
        self.elapsed = 0

    def time(self):
        return datetime(2026, 1, 1, tzinfo=timezone.utc).timestamp() + self.elapsed

    def monotonic(self):
        return self.elapsed

    def sleep(self, seconds):
        self.elapsed += seconds


nightly = runpy.run_path(str(Path(__file__).resolve().parents[2] / "nightly-release.py"))
raise SystemExit(nightly["main"](ValidationClock()))
