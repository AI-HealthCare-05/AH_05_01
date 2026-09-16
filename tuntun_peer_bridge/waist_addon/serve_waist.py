"""⚠️ 2026-09-10 추가 - 허리둘레(cm) 계산 보조 서버.

이 파일은 core_bundle/ 밖(waist_addon/)에 있어서 manifest 무결성 검증 대상이
아니다 - core_bundle 안의 원본 pinned 파일(waist_model.joblib, d0_inference.py 등)은
단 한 바이트도 수정하지 않는다. 다만 그 파일들을 "읽기 전용으로 import/로드"해서
재사용한다 - tuntun_inference.py의 TuntunPredictor.score()가 원래도 하던 동작(같은
waist_model.joblib을 로드해서 predict() 호출)과 똑같은 일을, 다만 그 결과(cm 실수값)를
중간에 버리지 않고 그대로 반환할 뿐이다.

왜 필요한가: 원본 TuntunPredictor.score()는 waist_model.predict()의 cm 결과를 즉시
"신체 위험 확률"로 변환해서 반환값에서 지워버린다(tuntun_inference.py:171~172,
`estimated = ...predict()`; `probability = physical_probability(estimated, ...)`;
health["physical"]만 반환). "몇 cm"라는 값 자체가 필요한 화면(허리둘레 cm 표시)에는
이 변환된 값이 아니라 원래 cm 값이 필요해서, 별도 경로로 같은 모델을 한 번 더 호출한다.

포트 8768에서 리슨(원본 서버는 8766) - 같은 컨테이너 안에서 fastapi가 두 포트 모두에
접근 가능(network_mode: "service:fastapi" 공유).
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import joblib
import pandas as pd

ROOT = Path(__file__).resolve().parent.parent / "core_bundle" / "legacy"
sys.path.insert(0, str(ROOT))
import d0_inference as d0  # noqa: E402  (core_bundle의 원본 모듈 - 읽기 전용 재사용)

_WAIST_MODEL = None


def _load_model():
    global _WAIST_MODEL
    if _WAIST_MODEL is None:
        # ⚠️ core_bundle 안의 원본 파일을 그대로 로드(복사도 수정도 아님) - joblib.load는
        # 읽기 전용 오픈이라 원본 파일에 어떤 변경도 가하지 않는다.
        _WAIST_MODEL = joblib.load(ROOT / "waist_model.joblib")
    return _WAIST_MODEL


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):  # noqa: A002 - 표준 라이브러리 시그니처 그대로 유지
        print(f"[waist-addon] {self.address_string()} - {format % args}", flush=True)

    def do_GET(self):
        if self.path == "/health":
            self._respond(200, {"status": "ready"})
            return
        self._respond(404, {"detail": "NOT_FOUND"})

    def do_POST(self):
        if self.path != "/waist/cm":
            self._respond(404, {"detail": "NOT_FOUND"})
            return
        try:
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            features = {key: body.get(key) for key in d0.FEATURES}
            if any(v is None for v in features.values()):
                self._respond(422, {"detail": "MISSING_REQUIRED_FEATURE"})
                return
            # ⚠️ 원본(tuntun_peer/inputs.py normalize())이 하던 범위 검증을 재사용 -
            # d0.SCHEMA(min/max/allowed/integer)를 그대로 씀. 이게 없으면 극단값(나이
            # 150세 등)이 그대로 모델에 들어가서 품질을 알 수 없는 결과가 나올 수 있음.
            for key, value in features.items():
                rule = d0.SCHEMA[key]
                low, high = rule.get("min", float("-inf")), rule.get("max", float("inf"))
                if not (isinstance(value, (int, float)) and low <= value <= high):
                    self._respond(422, {"detail": f"INVALID_{key.upper()}"})
                    return
                if "allowed" in rule and value not in rule["allowed"]:
                    self._respond(422, {"detail": f"INVALID_{key.upper()}"})
                    return
            # ⚠️ 원본(tuntun_inference.py TuntunPredictor.score())은 waist 계산 전에 항상
            # "당뇨/고혈압 모델이 이 입력을 지원하는지"부터 확인하고, 지원 안 하면(임신
            # 중이거나 임신 여부 불확실) waist도 같이 건너뛴다 - waist 모델 자체는 6개
            # 입력만 쓰고 임신 여부를 안 쓰지만, "임신 중엔 체형 기반 위험도 추정 자체를
            # 안 보여준다"는 원본의 안전 정책을 그대로 따름. pregnancy_status가
            # "not_applicable"(남성)이면 "nonpregnant"로 매핑하는 것도 원본
            # (tuntun_peer/inputs.py normalize())과 동일하게 재현.
            pregnancy_status = body.get("pregnancyStatus", "unknown")
            mapped = "nonpregnant" if pregnancy_status == "not_applicable" else pregnancy_status
            if mapped != "nonpregnant":
                self._respond(422, {"detail": "PREGNANCY_STATUS_UNSUPPORTED_FOR_WAIST"})
                return
            frame = d0.prepare_features(pd.DataFrame([features], columns=d0.FEATURES))
            model = _load_model()
            estimated_cm = float(model.predict(frame)[0])
            self._respond(200, {"waistCmEstimate": estimated_cm, "modelVersion": "waist_addon_v0_1"})
        except d0.InferenceError as exc:
            self._respond(422, {"detail": str(exc)})
        except Exception as exc:  # noqa: BLE001 - 보조 서버 전체가 죽지 않게 방어
            self._respond(503, {"detail": f"WAIST_MODEL_ERROR: {exc}"})

    def _respond(self, code: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 8768), Handler)
    # ⚠️ 2026-09-11 버그 수정: flush=True가 없어서 백그라운드 프로세스의 출력이 stdout
    # 버퍼에 갇혀 docker logs에 전혀 안 보였음(메인 서버 코드(__main__.py)는 flush=True를
    # 명시적으로 쓰고 있어서 정상 출력됐던 것과 대조됨 - 같은 문제가 없게 통일함).
    print(json.dumps({"status": "ready", "host": "127.0.0.1", "port": 8768}), flush=True)
    server.serve_forever()
