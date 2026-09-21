import argparse
import json
from pathlib import Path
from .release import validate_release, load_release
from .server import make_server


def main():
    parser = argparse.ArgumentParser(description="튼튼지수 앱 입력·출생연월·등수 표시·로컬 HTTP 연결")
    parser.add_argument("command", choices=["validate", "infer", "serve"])
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--manifest-sha256", required=True)
    parser.add_argument("--request", type=Path)
    parser.add_argument("--port", type=int, default=8766)
    args = parser.parse_args()
    if args.command == "validate":
        validate_release(args.release, args.manifest_sha256)
        print(json.dumps({"status": "PASS", "productionApproved": False}))
        return
    service = load_release(args.release, args.manifest_sha256)
    if args.command == "infer":
        if args.request is None:
            parser.error("--request is required for infer")
        request = json.loads(args.request.read_text(encoding="utf-8"))
        print(json.dumps(service.score(request), ensure_ascii=False, allow_nan=False))
        return
    server = make_server(service, args.port)
    print(json.dumps({"status": "ready", "host": "127.0.0.1", "port": server.server_port}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(json.dumps({"status": "FAIL", "error": type(exc).__name__}))
        raise SystemExit(2)
