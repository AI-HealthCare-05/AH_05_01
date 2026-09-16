"""Loopback-only, token-authenticated model worker. Personal inputs never logged."""

import argparse
import hmac
import json
import os
import re
import socket
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from personal_shap import PersonalShapEngine


def serve(payload, port, token):  # noqa: C901 - isolated handler closes over one engine, token and lock
    if len(token) < 32:
        raise ValueError("TUNTUN_XAI_TOKEN must contain at least 32 characters")
    engine = PersonalShapEngine(payload)
    lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def reply(self, code, body):
            data = json.dumps(body, ensure_ascii=False, allow_nan=False).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_POST(self):
            if not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + token):
                return self.reply(401, {"error": "unauthorized"})
            if self.path != "/personal-score":
                return self.reply(404, {"error": "not_found"})
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 8192:
                    return self.reply(413, {"error": "invalid_size"})
                self.connection.settimeout(10)
                request = json.loads(self.rfile.read(length))
            except (ValueError, OSError):
                return self.reply(400, {"error": "invalid_request"})
            if not lock.acquire(blocking=False):
                return self.reply(503, {"error": "busy"})
            try:
                result = engine.compute(request)
            except (ValueError, KeyError, TypeError) as exc:
                code = str(exc) if re.fullmatch(r"[A-Z_]{1,100}", str(exc)) else type(exc).__name__
                print("Model input rejected: " + code, file=sys.stderr, flush=True)
                return self.reply(422, {"error": "unsupported_input"})
            except Exception as exc:
                print("Model calculation unavailable: " + type(exc).__name__, file=sys.stderr, flush=True)
                return self.reply(503, {"error": "calculation_unavailable"})
            finally:
                lock.release()
            self.reply(200, result)

    class WorkerServer(ThreadingHTTPServer):
        allow_reuse_address = False

        def server_bind(self):
            if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
                self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            super().server_bind()

    server = WorkerServer(("127.0.0.1", port), Handler)
    server.daemon_threads = True
    print(f"Personal SHAP worker ready on 127.0.0.1:{server.server_port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload", required=True)
    parser.add_argument("--port", type=int, default=8776)
    args = parser.parse_args()
    serve(args.payload, args.port, os.environ.get("TUNTUN_XAI_TOKEN", ""))
