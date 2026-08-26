"""File hashing and code-version helpers used by the split pipeline."""

from __future__ import annotations

import hashlib
import subprocess
from pathlib import Path
from typing import Any


def sha256_file(path: Path, chunk_size: int = 1024 * 1024) -> str:
    """Return the SHA-256 digest of a file without loading it all into memory."""
    if not path.is_file():
        raise FileNotFoundError(
            f"Cannot hash missing file: {path}. Check that the path exists and is a file."
        )
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(chunk_size):
            digest.update(chunk)
    return digest.hexdigest()


def collect_code_version(source_dir: Path) -> dict[str, Any]:
    """Describe the executing source using per-file hashes and an optional Git commit."""
    files = {
        path.name: sha256_file(path)
        for path in sorted(source_dir.glob("*.py"), key=lambda item: item.name)
    }
    combined = hashlib.sha256()
    for name, file_hash in files.items():
        combined.update(f"{name}:{file_hash}\n".encode("utf-8"))

    git_commit: str | None = None
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=source_dir,
            check=True,
            capture_output=True,
            text=True,
            timeout=5,
        )
        git_commit = result.stdout.strip() or None
    except (FileNotFoundError, subprocess.SubprocessError):
        pass

    return {
        "combined_sha256": combined.hexdigest(),
        "source_files": files,
        "git_commit": git_commit,
    }


def verify_unchanged(path: Path, expected_sha256: str) -> None:
    """Raise when a protected input file no longer matches its initial digest."""
    actual = sha256_file(path)
    if actual != expected_sha256:
        raise RuntimeError(
            f"Protected input changed during execution: {path}. "
            "Restore the original file and rerun; no outputs were committed."
        )

