# ⚠️ 2026-09-09 추가 — 튼튼지수 vNext(또래 백분위) 검토용 모델 브릿지.
#
# 이 서비스는 tuntun_peer_bridge/README(원본 전달 패키지)가 요구하는 대로 정확히
# Python 3.14.7만 쓴다. 기존 fastapi(3.13.15)·ai-worker(3.13.15)와 다른 버전이라
# 같은 이미지에 얹지 않고 완전히 분리된 컨테이너로 뗐다.
#
# ⚠️ 절대 하지 말 것: numpy/pandas/scipy/scikit-learn/joblib 버전을 요구사항에서
# 임의로 올리거나 내리지 말 것 — 모델이 학습된 환경과 정확히 일치해야 재현성이
# 보장된다는 게 원본 README의 핵심 경고. requirements.txt는 원본 패키지 그대로 씀.
FROM python:3.14.7-slim

WORKDIR /app

COPY tuntun_peer_bridge/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY tuntun_peer_bridge/ ./

# ⚠️ 원본 server.py가 127.0.0.1에 하드코딩돼 있어서(README: "공개 운영 서버가
# 아니다"), 이 컨테이너는 docker-compose에서 network_mode: "service:fastapi"로
# fastapi와 네트워크 네임스페이스를 공유한다 - 그래야 fastapi가 자신의
# localhost:8766으로 접근할 수 있다. 소스 코드(따라서 manifest SHA-256)는
# 전혀 건드리지 않는다.
#
# 컨테이너 시작 시: 1) 신뢰 매니페스트 해시로 무결성 검증(실패하면 즉시 종료,
# 조용히 우회하지 않음) 2) 통과하면 서버 기동.
CMD ["sh", "-c", "\
    PIN=$(cat TRUSTED_MANIFEST_SHA256.txt | tr -d '[:space:]') && \
    echo \"[tuntun-peer-bridge] validating release against pinned manifest ${PIN}\" && \
    python -m src.tuntun_app_vnext validate --release . --manifest-sha256 \"$PIN\" && \
    exec python -m src.tuntun_app_vnext serve --release . --manifest-sha256 \"$PIN\" --port 8766 \
    "]
