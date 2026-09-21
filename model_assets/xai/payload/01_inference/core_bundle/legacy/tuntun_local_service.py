"""Loopback-only integration test bridge. Not a production web service."""
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
try:
    from .tuntun_inference import TuntunPredictor, VERSION
except ImportError:
    from tuntun_inference import TuntunPredictor, VERSION


def create_server(predictor, port=0):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):
            pass  # No input bodies or query strings in logs.

        def reply(self,status,value):
            body=json.dumps(value,ensure_ascii=True,allow_nan=False).encode()
            self.send_response(status)
            self.send_header("Content-Type","application/json")
            self.send_header("Content-Length",str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path!="/health":
                return self.reply(404,{"error":"not_found"})
            self.reply(200,{"modelVersion":VERSION,"status":"ready","productionReleaseGate":"BLOCKED"})

        def do_POST(self):
            if self.path!="/score":
                return self.reply(404,{"error":"not_found"})
            try:
                length=int(self.headers.get("Content-Length","0"))
                if not 0<length<=16384:
                    return self.reply(413,{"error":"invalid_body_size"})
                self.connection.settimeout(10)
                request=json.loads(self.rfile.read(length),parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
                if not isinstance(request,dict) or set(request)-{"features","pregnancy_status","activity_window_end","recorded_days"}:
                    raise ValueError()
                result=predictor.score(**request)
            except (ValueError,TypeError,KeyError,TimeoutError):
                return self.reply(400,{"error":"invalid_request"})
            except Exception:
                return self.reply(500,{"error":"inference_failed"})
            self.reply(200,result)
    return ThreadingHTTPServer(("127.0.0.1",port),Handler)


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle",type=Path,required=True)
    parser.add_argument("--manifest-sha256",required=True)
    parser.add_argument("--port",type=int,default=8765)
    args=parser.parse_args()
    predictor=TuntunPredictor(args.bundle,args.manifest_sha256)
    server=create_server(predictor,args.port)
    print(json.dumps({"host":"127.0.0.1","port":server.server_port,"production_release_gate":"BLOCKED"}),flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()
