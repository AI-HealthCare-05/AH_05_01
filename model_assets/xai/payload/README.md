# 지현님 SHAP 추가 전달 — 허리둘레 cm / 2026-09-11

첫 설명 대상은 **허리둘레 예측(cm)** 으로 정리했습니다. 기존 vNext v0.2 후보와 같은 동결 모델을 사용하며, 재학습이나 점수 산식 변경은 없습니다. 이 패키지는 앱 설명 시험용이며 운영 배포·의료 성능 승인과 구분합니다.

## 파일과 읽는 순서

1. `02_background/background_300.csv`: 실제 2019~2021 개발 입력에서 선정한 300행 × 6열. 프롬프트 가상데이터와 다릅니다.
2. `02_background/selection_receipt.json`: 출처·선정 비율·시드·해시·시험 데이터 제외 확인.
3. `04_documentation/입력_전처리_설명범위.md`: 변수·단위·적격성·출력 해석.
4. `waist_cm.py`: 전용 추론 함수와 검증된 SHAP 계산 함수.
5. `03_samples/waist_cm_inputs_5.csv`, `waist_cm.expected.json`, `shap.expected.json`: 합성 대조 입력 5개와 실제 계산 결과.
6. `verification.json`: 실제 재실행·오류 입력·SHAP 합산 대조 결과.
7. `04_documentation/시험범위_연령경계_처리방향.md`: vNext 시험 범위와 운영 전 처리 방향.

`01_inference`는 기존에 받은 vNext v0.2 릴리스를 그대로 보존한 실행 패키지입니다. 새 PC에서도 예전 작업 폴더를 찾지 않도록 포함했습니다. `04_documentation`에는 개발 데이터의 원본 행을 제외한 선정 계약·manifest·전처리 출처 코드도 있습니다.

## 실행

이 README가 있는 폴더를 작업 디렉터리로 사용합니다. Python 3.14.7과 원본 requirements 버전을 유지합니다. 기존 환경이 있으면 해당 Python 실행 경로를 아래 명령의 Python 경로 대신 사용하세요. 다른 모델 프로젝트의 `src`를 이미 import한 프로세스와 섞지 말고 새 프로세스에서 실행합니다.

```powershell
python --version
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-shap.txt
.\.venv\Scripts\python.exe -X utf8 verify_handoff.py --output local_verification.json
```

cm 추론만 실행:

```powershell
.\.venv\Scripts\python.exe -X utf8 waist_cm.py --input 03_samples/waist_cm_inputs_5.csv --pregnancy-status nonpregnant --output my_predictions.json
```

SHAP까지 실행:

```powershell
.\.venv\Scripts\python.exe -X utf8 waist_cm.py --input 03_samples/waist_cm_inputs_5.csv --background 02_background/background_300.csv --pregnancy-status nonpregnant --output my_shap.json
```

Python에서 사용:

```python
from pathlib import Path
import pandas as pd
from waist_cm import WaistCM
model = WaistCM(Path('01_inference'))
X = pd.read_csv('03_samples/waist_cm_inputs_5.csv')
predicted_cm = model.predict_waist_cm(X, pregnancy_status='nonpregnant')
background = pd.read_csv('02_background/background_300.csv')
explanation = model.explain_waist_cm(X, background, pregnancy_status='nonpregnant')
```

`pregnancy_status`는 적격성을 확인하는 문맥이며 SHAP 입력 열이 아닙니다. 남성도 기존 내부 모델 계약에서 적격성을 확인한 뒤 nonpregnant 문맥으로 호출합니다. 임신/미상은 거부하며, 유효하지 않은 입력을 임의 보완하지 않습니다.

## SHAP 계산 방식과 해석

`shap==0.49.1`, `ExactExplainer`, `Independent` masker, identity link를 사용합니다. 입력은 6개이므로 최대 64개 부분집합을 실제 cm 함수로 평가합니다. 배경은 300행 전체이며 `max_samples=300`을 명시해 기본 100행 재추출을 방지합니다. 배경 표나 행 수를 바꾸면 기준값과 기여도도 바뀌므로 버전을 다시 부여하세요.

`baseValueCm + sum(shapValuesCm) = predictionCm`을 절대 오차 1e-9 cm로 검사합니다. 기준값은 동일 300행의 모델 예측 평균과 대조합니다. cm는 허리둘레 예측 단위이며 점수·백분위·질환 확률 단위가 아닙니다. 기여도가 양수이면 해당 기준에서 예측 cm를 높이는 방향이라는 뜻입니다. 입력별 기여도가 운동 효과·질병 원인·개인별 처방을 뜻하지 않습니다. Independent masking은 서로 연관된 키·체중 등의 비현실적 조합을 만들 수 있으므로 인과 해석이나 행동 효과 추정에 사용하지 않습니다.

초기 TreeExplainer 점검에서는 SHAP 합계와 실제 함수 사이 약 0.001176 cm 차이가 관찰됐고, float32 입력 정렬 후에도 약 0.000277 cm가 남았습니다. 원인은 확정하지 않았습니다. 그 출력을 보정하거나 기준값을 임의 변경하지 않고, 실제 함수의 모든 부분집합을 평가하는 ExactExplainer로 전달 경로를 정했습니다. 기존 모델 파일은 변경하지 않았습니다.

공식 API 근거:
- https://shap.readthedocs.io/en/stable/generated/shap.ExactExplainer.html
- https://shap.readthedocs.io/en/stable/generated/shap.maskers.Independent.html

정확한 계산은 이 유한 배경 표와 정해진 masking 정의에 대한 것이며, SHAP 표본 수 300의 안정성·임상적 타당성·조건부 분포의 정확성을 입증한 것은 아닙니다. 서버 SLA나 동시 처리량은 별도 측정이 필요합니다. `verification.json` 시간은 현재 PC의 합성 샘플 5개 실행 시간입니다.

## 배경 데이터 재현

기존 학습 입력 해시와 일치하는 원본 개발 파일을 보유한 담당자만 재현할 수 있습니다. 원본 개발 데이터는 ZIP에 넣지 않았습니다. 출력 경로는 새 폴더를 지정하세요.

```powershell
python select_background.py --source PATH_TO_DEVELOPMENT_2019_2021.csv --output regenerated_background
```

원본 파일 해시를 강제로 검사하고, 연도·성별·연령대 18개 층에서 비례 할당·최대 나머지 방식으로 300행을 배정한 뒤 시드 20260911로 비복원 추출합니다. 출력 순서도 섞습니다. 실제 입력값은 바꾸지 않았습니다. 키·연도·조사가중치·정답은 출력 CSV에 포함하지 않습니다. 식별자 제거는 완전한 익명성 보장을 의미하지 않으므로 이 실제 입력 표는 팀 내부 검토 자료로 취급하고 공개 저장소·LLM 문맥에 올리지 않습니다. LLM에는 설명에 필요한 해당 사용자의 계산 결과만 전달합니다.

## 검증 범위

모델 파일 해시·환경, 배경 열/결측/입력 적격성, 저장 모델과 cm 함수의 일치, 나이 상한, 입력 오류 거부, 원본 앱 샘플 5개 불변, SHAP 기준값·기여도 합산을 확인합니다. OpenAI 호출, 앱 UI 연동, 모델 재학습, 임상 평가, 운영 배포는 수행하지 않았습니다.
