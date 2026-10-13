import json
from pathlib import Path


class IssueStore:
    def __init__(self, path, repository, number):
        self.path = Path(path)
        self.repository = repository
        self.number = number

    def create(self, payload):
        if self.path.exists():
            raise AssertionError("Issue already exists")
        return self._save({"state": "open", "labels": [], **payload})

    def patch(self, payload):
        return self._save({**self.get(), **payload})

    def get(self):
        return json.loads(self.path.read_text())

    def list(self):
        return [self.get()] if self.path.exists() else []

    def _save(self, issue):
        issue["number"] = self.number
        issue["html_url"] = f"https://github.com/{self.repository}/issues/{self.number}"
        issue["labels"] = [
            {"name": label} if isinstance(label, str) else label
            for label in issue["labels"]
        ]
        self.path.write_text(json.dumps(issue))
        return issue
