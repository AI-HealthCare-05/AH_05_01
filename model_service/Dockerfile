# EC2 내부 시연 전용. Windows 실행 파일을 사용하지 않는다.
FROM python:3.14.7-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
# 고정 SHAP 0.49.1은 CPython 3.14 Linux에서 C++ 확장을 빌드한다.
RUN apt-get update && apt-get install -y --no-install-recommends build-essential libgomp1 \
    && rm -rf /var/lib/apt/lists/*
COPY model_service/requirements-linux.txt /app/requirements-linux.txt
RUN pip install --no-cache-dir -r /app/requirements-linux.txt
COPY model_service /app/model_service
COPY app/data/reference /app/app/data/reference
COPY model_assets/xai/payload /app/payload
# 실제 모델 로드·릴리스/배경/래퍼 해시 검증을 이미지 빌드에서 수행한다.
RUN python -B model_service/check_runtime.py --payload /app/payload
USER 65534:65534
# 외부 포트를 열지 않고 fastapi의 네트워크 공간 안에서만 동작한다.
CMD ["python", "-B", "model_service/server.py", "--payload", "/app/payload", "--port", "8776"]
