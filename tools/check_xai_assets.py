"""Git 체크아웃의 XAI 원본 바이트를 검사한다. 모델 코드는 실행하지 않는다."""

import hashlib
import json
from pathlib import Path


def check(root: Path) -> int:
    payload = (root / "model_assets/xai/payload").resolve()
    manifest = json.loads((root / "model_assets/xai/SHA256SUMS.json").read_text(encoding="utf8"))
    for relative, expected in manifest.items():
        path = (payload / relative).resolve()
        if not path.is_relative_to(payload) or not path.is_file():
            raise RuntimeError(f"모델 파일 누락 또는 잘못된 경로: {relative}")
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise RuntimeError(f"모델 원본 해시 불일치: {relative}")
    return len(manifest)


if __name__ == "__main__":
    print(f"XAI 원본 {check(Path(__file__).resolve().parents[1])}개 검증 통과")
