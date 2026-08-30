# 틈튼 TMTN · UI 핸드오프 v5

2026-08-29 · Figma `ByGT2uoinUBxAQOBqAK7sM` 기준
받는 사람: 문홍주 (안드로이드)

---

## 이것부터 보세요

**`CHANGELOG_v4_to_v5.md`** — 8/27 핸드오프 대비 무엇이 바뀌었고, 어떤 순서로 고치면 되는지.
이 문서 하나가 이 패키지의 본체입니다.

---

## 들어 있는 것

```
tmtn-handoff/
├── README.md                 ← 지금 보는 문서
├── CHANGELOG_v4_to_v5.md     ← ★ 여기부터
├── index.html                ← 화면 107장 목록. 누르면 Figma 그 화면으로 바로 이동
├── SCREENS.csv               ← 같은 목록의 표 버전 (Figma 링크 포함)
├── tokens.css                ← 웹·문서용 토큰
└── designsystem/
    ├── TmtnColor.kt          ← 앱에 덮어쓰기
    ├── Type.kt               ← 앱에 덮어쓰기
    └── TmtnDimens.kt         ← 새로 추가
```

## v4에 있던 것 중 빠진 것

- `previews/*.png` (화면 이미지 107장)
- `screens/*.html` (화면별 HTML)

이번엔 **Figma 링크로 대신했습니다.** 디자인이 계속 움직이고 있어서,
떠 놓은 이미지는 며칠이면 실제와 어긋납니다. `index.html` 에서 화면을 누르면
언제나 **지금의 Figma**가 열립니다.

`assets/*.svg` 도 4탭 시절 아이콘이라 넣지 않았습니다. 필요한 아이콘은
Figma에서 그때그때 내보내 쓰세요.

## 오늘 할 일 (추천 순서)

1. `TmtnColor.kt` 덮어쓰기 → 빌드 → 색이 순백+먹색으로 바뀌는지 확인 · **30분**
2. `Type.kt` · `TmtnDimens.kt` 넣기 → 빌드 · **20분**
3. 하단 탭 4개 → 5개 (참고 → 틈튼지수, 마이 → 내 정보) · **1시간**
4. 기록 탭 달력 (월요일 시작 · 4상태) · **반나절**
5. 쉼 흐름 (B16 · B17) · **반나절**

1·2번은 오늘 안에 됩니다. 거기까지만 해도 화면이 확 달라집니다.

## 막히면

Figma에서 해당 화면을 열고 오른쪽 Inspect 패널을 보면 정확한 값이 나옵니다.
`SCREENS.csv` 의 `figma_url` 열이 화면별 바로가기입니다.
