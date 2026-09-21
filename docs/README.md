# 문서 안내

2026-09-21 `main`에 반영된 앱을 기준으로, 구현·운영에 사용하는 문서와 검증 근거를 모았습니다.

| 필요한 내용 | 문서 |
| --- | --- |
| 앱 기능·홈 상태·사용자 흐름 | [현재 앱 안내](APP_GUIDE.md) |
| Python·Android 버전, 로컬 API·테스트 실행 | [개발 환경](DEVELOPMENT_ENVIRONMENT.md) |
| 서버 업데이트, 모델 연결, 마이그레이션, 배포 후 확인 | [EC2 배포](DEPLOYMENT.md) |
| 개인 SHAP·주간 이력·읽을거리 구분과 표시 조건 | [XAI 안내](XAI.md) |
| PR #21·#24 통합 결과와 검사 한계 | [통합 검증 기록](INTEGRATION_2026-09-21.md) |
| 브랜치·릴리스·병합 방식 | [Git 브랜치 전략](GIT_BRANCH_STRATEGY.md) |
| PR 작성·리뷰 기준 | [PR 리뷰 가이드](PR_AND_CODE_REVIEW_GUIDE.md) |
| 약관·개인정보·건강정보·위치·분석·알림 동의 | [법적 문서 검토본](legal/README.md) |
| 모델 선택·실험 근거·실제 수치와 그림 | [모델 개발 보고서](model-development-20260917/REPORT.md) |

## 구현 가까이에 있는 안내

- [Android 빌드와 코드 위치](../android/README.md), [디자인 기준](../android/DESIGN.md)
- [개인 XAI 런타임](../model_service/README.md)
- [카드 콘텐츠 적재](../app/scripts/data/README.md)
- [일보 읽을거리·출처·승인 데이터](../app/data/journal/README.md)
- [시스템 설계](../SYSTEM_DESIGN.md)

## 정리 기준

지난 UI 시안·스크린샷·이미지 생성 프롬프트, 중복 전달본 안내, 초기 미구현 현황표와 완료된 PR별 임시 메모는 현재 문서에서 제거했습니다. 예전 문서는 삭제 전 Git 이력에서 확인할 수 있으며 앱의 현재 동작 기준으로 사용하지 않습니다.

배포 절차는 `DEPLOYMENT.md`, 화면 흐름은 `APP_GUIDE.md`, 모델 표시 계약은 `XAI.md`로 통합했습니다. 모델 연구 보고서·그림·수치 검증 파일과 약관·동의 검토본은 근거 보존을 위해 남겼습니다. 모델 원본, 실행 코드, 테스트, DB 마이그레이션은 이 문서 정리의 삭제 대상이 아닙니다.

현재 구현과 계획을 구분합니다. 과거 연구 결과는 공개 승인이나 현재 서버 배포 완료의 증거가 아닙니다.
