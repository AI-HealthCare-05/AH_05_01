"""팀 내부 EC2 XAI 설정을 생성한다. 기존 .env와 기존 XAI 토큰은 덮어쓰지 않는다."""

import hashlib
import json
import os
import platform
import secrets
from pathlib import Path


def prepare(root):
    root = root.resolve()
    if platform.system() != "Linux" or platform.machine().lower() not in {"x86_64", "amd64"}:
        raise RuntimeError("이 전달본의 EC2 검증 대상은 Linux x86_64입니다.")
    payload = root / "model_assets/xai/payload"
    manifest = root / "model_assets/xai/SHA256SUMS.json"
    for relative, expected in json.loads(manifest.read_text(encoding="utf8")).items():
        path = (payload / relative).resolve()
        if not path.is_relative_to(payload.resolve()):
            raise RuntimeError("잘못된 모델 경로")
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise RuntimeError(f"모델 자산 해시 불일치: {relative}")
    target = root / ".env.xai-review"
    if target.exists():
        values = dict(line.split("=", 1) for line in target.read_text().splitlines() if "=" in line and not line.startswith("#"))
        if values.get("ENV") != "dev" or values.get("TUNTUN_XAI_URL") != "http://127.0.0.1:8776" or len(values.get("TUNTUN_XAI_TOKEN", "")) < 32:
            raise RuntimeError("기존 .env.xai-review 설정을 확인해 주세요. 파일은 변경하지 않았습니다.")
        print("기존 XAI 설정을 유지합니다. 모델 자산 검사 통과.")
        return
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf8") as stream:
        stream.write("# 팀 내부 검토·시연용. 공개 운영 승인과 별개입니다.\n")
        stream.write("ENV=dev\nTUNTUN_XAI_URL=http://127.0.0.1:8776\n")
        stream.write("TUNTUN_XAI_TOKEN=" + secrets.token_urlsafe(48) + "\n")
    print(".env.xai-review 생성 완료. 기존 .env/DB/계정은 변경하지 않았습니다.")


if __name__ == "__main__":
    prepare(Path(__file__).resolve().parents[1])
