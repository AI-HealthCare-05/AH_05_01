package com.tmtn.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** 시스템 뒤로가기(제스처·버튼) 눌렀을 때 어느 단계로 돌아갈지.
 * null이면 "더 되돌아갈 데 없음" -> 시스템 기본 동작(앱 종료)을 그대로 허용. */
private fun previousStepFor(step: OnboardingStep): OnboardingStep? = when (step) {
    OnboardingStep.A01_SPLASH -> null
    OnboardingStep.A02_START -> null
    OnboardingStep.A03_SIGNUP -> OnboardingStep.A02_START
    OnboardingStep.A04_VERIFY -> OnboardingStep.A03_SIGNUP
    OnboardingStep.A05_LOGIN -> OnboardingStep.A02_START
    OnboardingStep.A06_CONSENT -> OnboardingStep.A04_VERIFY
    OnboardingStep.A07_PROFILE -> OnboardingStep.SIGNUP_COMPLETE
    OnboardingStep.A08_EXERCISE -> OnboardingStep.A07_PROFILE
    OnboardingStep.A09_SCHEDULE_INTRO -> OnboardingStep.A08_EXERCISE
    OnboardingStep.A10_SCHEDULE -> OnboardingStep.A09_SCHEDULE_INTRO
    OnboardingStep.A11_VERIFY_RETRY -> OnboardingStep.A04_VERIFY
    OnboardingStep.A12_PASSWORD_RESET_REQUEST -> OnboardingStep.A05_LOGIN
    OnboardingStep.A13_NEW_PASSWORD -> OnboardingStep.A05_LOGIN
    OnboardingStep.A14_TERMS_DETAIL -> OnboardingStep.A06_CONSENT
    OnboardingStep.A15_COMPLETE -> null // 온보딩 마지막 요약 - 더 되돌아갈 곳 없음
    OnboardingStep.A16_PERMISSIONS -> OnboardingStep.A08_EXERCISE
    OnboardingStep.SIGNUP_COMPLETE -> null
    OnboardingStep.DONE -> null
}

/**
 * A01~A16 온보딩 흐름 전체를 관리하는 최상위 컴포저블.
 * Navigation Compose 없이 OnboardingState.step 값에 따라 화면만 바꿔치기하는 단순한 방식
 * (새 Gradle 의존성 추가 안 함).
 * 완료되면 onOnboardingComplete()를 호출해서 상위(MainActivity)에 알림.
 *
 * hasSensorPermissions/onRequestPermissions: A08 완료 시 센서 권한이 이미 있으면 A16(권한요청)을
 * 건너뛰고, A16에서 "권한 허용하기" 눌렀을 때 실제 시스템 권한 요청을 트리거하기 위해
 * MainActivity에서 내려받는 콜백.
 */
@Composable
fun OnboardingFlow(
    onOnboardingComplete: () -> Unit,
    hasSensorPermissions: () -> Boolean = { true },
    onRequestPermissions: () -> Unit = {},
    startAtLogin: Boolean = false,
    systemSplashShown: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTmtnColors.current
    val state = remember {
        OnboardingState().apply {
            // H07(세션 만료)에서 넘어온 경우 - 온보딩 처음이 아니라 바로 로그인 화면부터
            if (startAtLogin) step.value = OnboardingStep.A05_LOGIN
            else if (systemSplashShown && step.value == OnboardingStep.A01_SPLASH) step.value = OnboardingStep.A02_START
        }
    }
    val scope = rememberCoroutineScope()

    // ⚠️ 2026-09-06 QA(P1-9) 반영: 스낵바 타이머가 화면 전환과 분리돼 있어서, 로그인
    // 화면에서 뜬 에러가 "이메일로 가입하기"로 넘어간 뒤에도 6초 동안 그대로 남아있었음
    // (가입 화면은 아무 요청도 안 했는데 실패 메시지가 보임). 화면(step)이 바뀔 때마다
    // 지금 이전 화면에서 뜬 에러는 비워서, 새 화면은 항상 깨끗하게 시작하게 함.
    LaunchedEffect(state.step.value) {
        state.errorMessage.value = null
    }

    // ⚠️ 예전엔 화면 안의 "←" 버튼만 단계를 되돌렸고, 폰의 시스템 뒤로가기(제스처/버튼)는
    // 아예 안 걸려있어서 그냥 앱이 종료(바탕화면으로 이동)돼버렸음. 여기서 같이 처리.
    LaunchedEffect(state.accountCreated) {
        state.restoreProfileForResume()
    }
    val previousStep = if (state.accountCreated && state.step.value == OnboardingStep.A06_CONSENT) null else previousStepFor(state.step.value)
    BackHandler(enabled = previousStep != null) {
        previousStep?.let { state.step.value = it }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (state.step.value) {
            OnboardingStep.A01_SPLASH -> A01SplashScreen(state)
            OnboardingStep.A02_START -> A02StartScreen(state)
            OnboardingStep.A03_SIGNUP -> A03SignupScreen(state, scope)
            OnboardingStep.A04_VERIFY -> A04VerifyScreen(state, scope)
            OnboardingStep.A05_LOGIN -> A05LoginScreen(state, scope, onLoginSuccess = onOnboardingComplete)
            OnboardingStep.A06_CONSENT -> A06ConsentScreen(state, scope)
            OnboardingStep.SIGNUP_COMPLETE -> SignupCompleteScreen {
                OnboardingCheckpoint.save(OnboardingStep.A07_PROFILE)
                state.step.value = OnboardingStep.A07_PROFILE
            }
            OnboardingStep.A07_PROFILE -> A07ProfileScreen(state, scope)
            OnboardingStep.A08_EXERCISE -> A08ExerciseScreen(state, scope, hasSensorPermissions)
            OnboardingStep.A09_SCHEDULE_INTRO -> A09ScheduleIntroScreen(state)
            OnboardingStep.A10_SCHEDULE -> A10ScheduleScreen(state, scope)
            OnboardingStep.A11_VERIFY_RETRY -> A11VerifyRetryScreen(state, scope)
            OnboardingStep.A12_PASSWORD_RESET_REQUEST -> A12PasswordResetRequestScreen(state)
            OnboardingStep.A13_NEW_PASSWORD -> A13NewPasswordScreen(state)
            OnboardingStep.A14_TERMS_DETAIL -> A14TermsDetailScreen(state)
            OnboardingStep.A15_COMPLETE -> A15CompleteScreen(state) {
                OnboardingCheckpoint.clear()
                onOnboardingComplete()
            }
            OnboardingStep.A16_PERMISSIONS -> A16PermissionsScreen(state, onRequestPermissions)
            OnboardingStep.DONE -> {
                LaunchedEffect(Unit) { OnboardingCheckpoint.clear(); onOnboardingComplete() }
            }
        }

        // 에러 메시지 - 화면 하단에 떠 있는 배너
        state.errorMessage.value?.let { message ->
            Surface(
                color = colors.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
            ) {
                Text(
                    "⚠️ $message", style = TmtnType.caption, color = colors.error,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // 로딩 인디케이터 - 화면 전체 덮는 오버레이
        if (state.isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        }
    }
}
