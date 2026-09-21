# 개인 XAI · EC2 내부 시연

실행 순서는 [EC2 배포 가이드](../docs/DEPLOYMENT.md), 앱의 표시 조건과 주간 이력은 [XAI 안내](../docs/XAI.md)를 사용합니다.

- API Python 3.13.15와 XAI Python 3.14.7을 분리합니다.
- Linux 의존성은 requirements-linux.txt이며 원본 Windows 검증 모델과 동일한 패키지 버전입니다.
- model_assets/xai/payload의 원본 릴리스·배경·래퍼 해시를 검증합니다. 학습이나 재보정을 하지 않습니다.
- compose.xai-review.yml을 기존 Compose 뒤에 추가합니다. API와 XAI가 loopback 8776을 공유합니다.
- prepare-xai-ec2.py가 토큰을 생성하며 ENV=dev인 팀 내부 시연 전용입니다.
- SHAP는 질환별 내부 참고점수의 기여도입니다. 또래 백분위나 인과 효과를 분해한 값이 아닙니다.
- 회원 ID·이메일·미션 기록을 모델에 보내지 않습니다. 인증·동의·입력 버전·no-store 검사를 유지합니다.
- 실제 ready 결과만 월요일 기준 주간 이력에 저장합니다. 없는 과거 점수를 만들지 않습니다.
- 공개 운영 승인 상태는 바꾸지 않습니다. ENV=prod에서는 release_review_required를 유지합니다.

verify_personal.py: 합성 입력 5건의 실제 모델·예측/순위 일치·기여도 합 확인.
verify_end_to_end.py: 합성 저장소를 사용한 로그인 API → 실제 모델 HTTP → 주간 이력 확인. 실제 DB에는 접속하지 않습니다.
