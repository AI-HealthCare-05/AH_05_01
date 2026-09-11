"""Local HTTP bridge. No prediction caching, no request logging, no mock fallback."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
from . import SCHEMA_VERSION
from .inputs import normalize


def strict_json(data):
    def no_duplicates(pairs):
        output = {}
        for key, value in pairs:
            if key in output:
                raise ValueError("DUPLICATE_JSON_KEY")
            output[key] = value
        return output
    def no_constant(_):
        raise ValueError("NONFINITE_JSON_VALUE")
    return json.loads(data, object_pairs_hook=no_duplicates, parse_constant=no_constant)


def make_server(service, port=0):
    inference_lock = threading.Lock()
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def log_message(self, *args):
            pass

        def send_json(self, status, value):
            body = json.dumps(value, ensure_ascii=False, allow_nan=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == "/health":
                self.send_json(200, {"status": "ready", "schemaVersion": SCHEMA_VERSION,
                                     "modelLoaded": True, "isMock": False, "releaseStatus": "LOCAL_REVIEW_CANDIDATE"})
            else:
                self.send_json(404, {"error": "NOT_FOUND"})

        def do_POST(self):
            if self.path != "/score/peer/v2":
                return self.send_json(404, {"error": "NOT_FOUND"})
            if self.headers.get("X-Tuntun-Schema") != SCHEMA_VERSION:
                return self.send_json(409, {"error": "UNSUPPORTED_CLIENT_SCHEMA", "supportedSchema": SCHEMA_VERSION})
            if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json":
                return self.send_json(415, {"error": "JSON_CONTENT_TYPE_REQUIRED"})
            if self.headers.get("Transfer-Encoding"):
                return self.send_json(400, {"error": "CHUNKED_ENCODING_UNSUPPORTED"})
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                return self.send_json(400, {"error": "INVALID_CONTENT_LENGTH"})
            if not 1 <= length <= 16384:
                return self.send_json(413, {"error": "INVALID_BODY_SIZE"})
            try:
                data = strict_json(self.rfile.read(length).decode("utf-8"))
                normalize(data)
            except (ValueError, TypeError, KeyError, UnicodeError):
                return self.send_json(422, {"error": "INVALID_INPUT_OR_CONTRACT", "scoreAvailable": False})
            try:
                with inference_lock:
                    output = service.score(data)
            except Exception:
                return self.send_json(503, {"error": "MODEL_INFERENCE_FAILED", "scoreAvailable": False})
            self.send_json(200, output)
    return ThreadingHTTPServer(("127.0.0.1", port), Handler)
