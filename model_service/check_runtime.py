"""고정 런타임과 원본 자산을 검증한다. 개인 입력이나 비밀값을 출력하지 않는다."""

import argparse
import importlib.metadata
import platform
from pathlib import Path

from personal_shap import PersonalShapEngine


def check(payload):
    if platform.python_version() != "3.14.7":
        raise RuntimeError("모델은 Python 3.14.7 전용입니다.")
    requirement_file = Path(__file__).with_name("requirements-linux.txt")
    for line in requirement_file.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        name, version = line.split("==")
        if importlib.metadata.version(name) != version:
            raise RuntimeError(f"모델 패키지 버전 불일치: {name}")
    PersonalShapEngine(payload)
    print("모델 런타임·릴리스·래퍼·배경 해시 검증 통과", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--payload", type=Path, required=True)
    args = parser.parse_args()
    check(args.payload)
