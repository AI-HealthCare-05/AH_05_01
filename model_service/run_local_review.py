"""Start the real API and pinned personal model together for local review.

Requires the configured application's MySQL database and PR21 migration 23 first.
No database migrations, account changes or source approvals are performed here.
"""

import argparse
import os
import secrets
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--runtime", type=Path, required=True)
parser.add_argument("--payload", type=Path, required=True)
parser.add_argument("--api-port", type=int, default=8000)
args = parser.parse_args()
cache = root / ".runtime" / "personal-xai"
cache.mkdir(parents=True, exist_ok=True)
env = {
    **os.environ,
    "ENV": "local",
    "TUNTUN_XAI_TOKEN": secrets.token_urlsafe(48),
    "NUMBA_CACHE_DIR": str(cache / "numba"),
    "MPLCONFIGDIR": str(cache / "matplotlib"),
}
flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
worker = subprocess.Popen(
    [
        str(args.runtime.resolve()),
        "-B",
        str(root / "model_service/server.py"),
        "--payload",
        str(args.payload.resolve()),
        "--port",
        "0",
    ],
    cwd=root,
    env=env,
    stdout=subprocess.PIPE,
    text=True,
    encoding="utf-8",
    creationflags=flags,
)
api = None
try:
    line = worker.stdout.readline()
    if "worker ready" not in line:
        raise RuntimeError("Model worker did not start. Check runtime and release hashes.")
    env["TUNTUN_XAI_URL"] = "http://" + line.strip().split(" on ")[-1]
    api = subprocess.Popen(
        [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", str(args.api_port)],
        cwd=root,
        env=env,
        creationflags=flags,
    )
    print(
        f"Local review API: http://127.0.0.1:{args.api_port}/api/docs\nToken stays in child process environments. Ctrl+C stops both processes.",
        flush=True,
    )
    api.wait()
finally:
    if api is not None and api.poll() is None:
        api.terminate()
        api.wait(timeout=15)
    worker.terminate()
    worker.wait(timeout=15)
if api is not None:
    sys.exit(api.returncode)
