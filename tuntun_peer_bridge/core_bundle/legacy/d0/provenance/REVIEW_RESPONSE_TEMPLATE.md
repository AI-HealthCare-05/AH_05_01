# D0 단일 조합 독립 검토 회신

- 검토자: 김지현
- 검토일: 2026-09-04
- OS / Python / 라이브러리 버전: Windows / Python 3.14.4 / pandas 3.0.3, numpy 2.4.4, scikit-learn 1.9.0
- 수신 ZIP SHA-256: (PACKAGE_MANIFEST.json 기준 확인 완료 — verify_review_bundle.py가 21개 파일 해시 전부 PASS로 확인함)
- 자동검사 명령 및 결과: `python tools/verify_review_bundle.py` → `AGGREGATE_REVIEW_CHECKS: PASS` (당뇨·고혈압 모두 rank/selection PASS)
- pytest 명령 및 passed/failed/skipped 수: `pytest tests/test_select_d0_single_pipeline.py tests/test_disease_model_pipeline.py -q` → **24 passed** (예상값과 일치), FutureWarning(`penalty` deprecated) 46건은 문서 §9에 이미 기재된 기존 기술부채와 동일 항목

| 항목 | PASS / FAIL / UNVERIFIED | 근거 파일·함수 및 의견 |
|---|---|---|
| 계약·후보공간·선택 규칙 일치 | PASS | `select_d0_single_pipeline.py:91` 에서 calibrators==["identity","platt"] 검증, 46 base × 2 calibrator × 3 seed = 276행 정확히 생성. 당뇨는 identity(0.102398879)가 절대 최저이나 동등범위(+0.0005) 내 log loss 우위로 platt 선택 — 계약의 tie-break 순서(`aggregate_and_select`:256-259행: stability→equivalent_to_best→log_loss→slope→intercept→pr_auc→roc_auc→simplicity)와 정확히 일치 |
| holdout 누수 방지 코드 | PASS | `evaluate_base_candidate` 159행 "outer train/holdout leakage", 162행 "outer holdout leaked into inner registry" — 명시적 assert로 위반 시 SelectionError 발생. Platt은 inner cross-fitted raw prediction만 사용(176-178행), outer holdout은 예측 대상으로만 사용 |
| 평균·seed 범위·순위·선택 결과 재계산 | PASS | 자동검사 도구가 552개 지표 완전성 + 184개 순위 독립 재계산 + 선택 JSON 대조까지 수행, 전부 PASS. freeze report의 당뇨/고혈압 수치(§3)와 combination_ranking.csv 재계산 결과 일치 |
| 해시·산출물 완전성 | PASS | manifest_sha256.json 기준 21개 파일 해시 전부 일치, 중복 key 0건, 결측 0건(freeze report §4) |
| 실패 경로 및 회귀 테스트 충분성 | **부분 FAIL / 의견 있음** | 12개 테스트가 정상 경로·불안정 후보 배제·PII 스캔 등은 커버하나(`test_select_d0_single_pipeline.py`), **계약 로더가 손상/변조된 YAML을 받았을 때의 거부 테스트**와 **checkpoint 재사용 로직(`evaluate_base_candidate`의 outer raw fit 재사용)이 잘못된 후보에 적용되지 않는지 검증하는 테스트**가 안 보임. 테스트 통과만으로 완전성 인정하지 말라는 README 지시에 따라 이 부분은 보완 권고 |
| production 경계 유지 | PASS | freeze report에 `PRODUCTION_RELEASE_GATE: BLOCKED` 명시, selection_status 전부 `D0_ONLY_MVP_BASELINE_SELECTED_PRODUCTION_REFIT_REQUIRED`로 OOF≠production 구분 유지 |
| 원자료/fold 및 실제 학습 재현 | UNVERIFIED (원자료 미제공) | README 지시대로 미확인 처리 |
| 실행 당시 종속 코드·환경 provenance | UNVERIFIED | README §"이 ZIP만으로 확인할 수 없는 것" 명시대로, manifest가 `disease_model_pipeline.py`의 당시 hash·전체 환경을 기록하지 않음 — 과거 provenance 소급 증명 불가 |

## 발견 사항

- **[낮음] 실패 경로 테스트 공백**: `select_d0_single_pipeline.py`의 계약 로더(`load_contract`)와 candidate 재사용 경로에 대한 실패 케이스 테스트가 12개 중에 없음. 재현 명령: 위 pytest 명령으로 커버리지 확인 시 해당 함수의 예외 분기 미도달 확인 가능. 기대값: 손상된 계약·잘못된 후보 조합에 대해 명시적 에러 테스트 존재. 실제값: 정상 경로 테스트만 존재. 수정 권고: `test_owner_contract_loads`류에 malformed-contract 케이스, `evaluate_base_candidate`에 checkpoint 오적용 방지 회귀 테스트 추가 권고 (필수 차단 사유는 아님, 보완 권고)

## 종합 의견

**검토 범위 내 PASS.** 계약·리크 방지·재계산·해시 무결성 전부 확인했고, 문서화된 한계(원자료 미제공 항목)는 README 지시대로 UNVERIFIED 처리했습니다. 다만 실패 경로 테스트 공백 1건은 production refit 전에 보완하면 좋겠습니다 — 차단 사유는 아니고 권고 사항입니다. 이 회신은 기술 검토 의견이며 소유자의 최종 승인 또는 production 배포 승인 자체가 아닙니다.
