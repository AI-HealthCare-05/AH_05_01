package com.tmtn.app.ui.profile

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tmtn.app.ui.theme.LocalTmtnColors

/** 시스템 뒤로가기(제스처/버튼) 눌렀을 때 어느 화면으로 돌아갈지 - 각 화면의 onBack 대상과 동일하게. */
private fun previousScreenFor(screen: ProfileScreenKey): ProfileScreenKey? = when (screen) {
    ProfileScreenKey.HOME -> null
    ProfileScreenKey.BASIC, ProfileScreenKey.STORY -> ProfileScreenKey.HOME
    ProfileScreenKey.TERMS -> ProfileScreenKey.HOME
    ProfileScreenKey.NOTIFICATION -> ProfileScreenKey.HOME
    ProfileScreenKey.NOTIFICATION_TIME -> ProfileScreenKey.NOTIFICATION
    ProfileScreenKey.WAKE_SLEEP -> ProfileScreenKey.NOTIFICATION
    ProfileScreenKey.PERMISSIONS -> ProfileScreenKey.HOME
    ProfileScreenKey.ACCOUNT -> ProfileScreenKey.HOME
    ProfileScreenKey.HEALTH -> ProfileScreenKey.HOME
    ProfileScreenKey.EXERCISE -> ProfileScreenKey.HOME
    ProfileScreenKey.CONSENT -> ProfileScreenKey.HOME
    ProfileScreenKey.ACCESSIBILITY -> ProfileScreenKey.HOME
    ProfileScreenKey.SOUND -> ProfileScreenKey.HOME
    ProfileScreenKey.EMAIL_CHANGE -> ProfileScreenKey.ACCOUNT
    ProfileScreenKey.PASSWORD_CHANGE -> ProfileScreenKey.ACCOUNT
    ProfileScreenKey.DELETE_REAUTH -> ProfileScreenKey.ACCOUNT
    ProfileScreenKey.DELETE_DONE -> null // Deleted accounts leave for the login screen.
    ProfileScreenKey.PRIVACY_DATA -> ProfileScreenKey.HOME
    ProfileScreenKey.EXPORT_DATA -> ProfileScreenKey.PRIVACY_DATA
    ProfileScreenKey.APP_INFO -> ProfileScreenKey.HOME
    ProfileScreenKey.HELP_DETAIL -> ProfileScreenKey.HOME
    ProfileScreenKey.INQUIRY -> ProfileScreenKey.HELP_DETAIL
}

/** F01~F12(중 API 있는 것) "내 정보" 탭 전체를 관리하는 최상위 컴포저블. 다른 탭과 동일한 패턴. */
@Composable
fun ProfileFlow(
    onOpenDam: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit,
    // ⚠️ 2026-09-08 QA 반영: onEditWakeSleep 파라미터를 없앰. MainActivity가 빈 람다({})를
    // 넘기고 있어서 "자고 일어나는 시각"이 눌러도 반응이 없었는데, 이제 내 정보 탭 안의
    // WAKE_SLEEP 화면으로 직접 이동하므로 바깥에서 받을 이유가 없어짐.
    onSaveCsv: (fileName: String, content: String) -> Unit,
    // ⚠️ 참고(틈튼지수) 탭의 "계산에 쓰인 값"에서 몸 정보/운동 정보 행을 눌렀을 때,
    // "내 정보" 홈이 아니라 그 항목 편집 화면으로 바로 들어가게 하기 위한 진입점.
    // 기본값 HOME이라 하단 탭에서 직접 "내 정보"를 눌렀을 때는 원래대로 홈부터 보여줌.
    initialScreen: ProfileScreenKey = ProfileScreenKey.HOME,
    // ⚠️ initialScreen이 HOME이 아닐 때(=다른 탭에서 딥링크로 들어온 경우), 그 화면에서
    // 뒤로가기를 누르면 "내 정보" 홈이 아니라 원래 있던 탭(참고)으로 돌아가야 함.
    // null이면(=하단 탭에서 직접 진입) 평소처럼 previousScreenFor()로 동작.
    onBackToOrigin: (() -> Unit)? = null,
    onImmersiveChange: (Boolean) -> Unit = {},
    state: ProfileState = remember { ProfileState().apply { screen.value = initialScreen } },
    onLoad: suspend () -> Unit = { state.loadAll() },
) {
    val colors = LocalTmtnColors.current
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.SideEffect { onImmersiveChange(state.screen.value == ProfileScreenKey.DELETE_DONE) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onImmersiveChange(false) } }
    BackHandler(enabled = state.screen.value == ProfileScreenKey.DELETE_DONE) { onLoggedOut() }

    LaunchedEffect(Unit) {
        onLoad()
    }

    val previousScreen = if (state.screen.value == ProfileScreenKey.TERMS) state.termsOrigin else previousScreenFor(state.screen.value)
    // 딥링크로 들어온 화면에 아직 그대로 있을 때만 "원래 탭으로 돌아가기"를 씀 - Profile
    // 안에서 다른 화면으로 이미 이동했다면 평소처럼 Profile 안의 이전 화면으로 돌아감.
    val isAtDeepLinkEntry = state.screen.value == initialScreen && initialScreen != ProfileScreenKey.HOME
    // Successful saves already route to HOME in ProfileState. For a score deep link, return to its caller.
    LaunchedEffect(state.screen.value) {
        state.errorMessage.value = null
        if (initialScreen != ProfileScreenKey.HOME && state.screen.value == ProfileScreenKey.HOME) onBackToOrigin?.invoke()
    }
    val backFromEditor: () -> Unit = {
        if (isAtDeepLinkEntry && onBackToOrigin != null) onBackToOrigin()
        else state.screen.value = ProfileScreenKey.HOME
    }
    BackHandler(enabled = previousScreen != null || (isAtDeepLinkEntry && onBackToOrigin != null)) {
        if (isAtDeepLinkEntry && onBackToOrigin != null) {
            onBackToOrigin()
        } else {
            previousScreen?.let { state.screen.value = it }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (state.screen.value) {
            ProfileScreenKey.HOME -> ProfileHomeScreen(
                state = state,
                onNavigate = { state.screen.value = it },
                onOpenDam = onOpenDam,
            )
            ProfileScreenKey.NOTIFICATION -> NotificationSettingScreen(
                state, scope,
                onBack = { state.screen.value = ProfileScreenKey.HOME },
            )
            ProfileScreenKey.NOTIFICATION_TIME -> NotificationTimeEditScreen(
                state, scope,
                onBack = { state.screen.value = ProfileScreenKey.NOTIFICATION },
            )
            ProfileScreenKey.WAKE_SLEEP -> WakeSleepEditScreen(
                state, scope,
                onBack = { state.screen.value = ProfileScreenKey.NOTIFICATION },
            )
            ProfileScreenKey.PERMISSIONS -> PermissionsScreen(
                onBack = { state.screen.value = ProfileScreenKey.HOME },
                onOpenSettings = onOpenSettings,
            )
            ProfileScreenKey.ACCOUNT -> {
                var showLogoutConfirm by remember { mutableStateOf(false) }
                AccountScreen(
                    userInfo = state.userInfo.value,
                    onBack = { state.screen.value = ProfileScreenKey.HOME },
                    onChangeEmail = { state.screen.value = ProfileScreenKey.EMAIL_CHANGE },
                    onChangePassword = { state.screen.value = ProfileScreenKey.PASSWORD_CHANGE },
                    onLogout = { showLogoutConfirm = true },
                    onDeleteAccount = { state.screen.value = ProfileScreenKey.DELETE_REAUTH },
                )
                // F22: 로그아웃 확인 다이얼로그
                if (showLogoutConfirm) {
                    LogoutConfirmDialog(
                        onConfirm = {
                            showLogoutConfirm = false
                            state.logout()
                            onLoggedOut()
                        },
                        onDismiss = { showLogoutConfirm = false },
                    )
                }
            }
            ProfileScreenKey.HEALTH -> HealthEditScreen(state, scope, onBack = backFromEditor)
            ProfileScreenKey.BASIC -> BasicInfoScreen(state, scope, onBack = backFromEditor)
            ProfileScreenKey.STORY -> com.tmtn.app.ui.onboarding.WelcomeStory(backFromEditor, backFromEditor, replay = true)
            ProfileScreenKey.EXERCISE -> ExerciseEditScreen(state, scope, onBack = backFromEditor)
            ProfileScreenKey.CONSENT -> ConsentScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.HOME })
            ProfileScreenKey.ACCESSIBILITY -> AccessibilityScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.HOME })
            ProfileScreenKey.SOUND -> SoundSettingsScreen(onBack = { state.screen.value = ProfileScreenKey.HOME })
            ProfileScreenKey.EMAIL_CHANGE -> EmailChangeScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.ACCOUNT })
            ProfileScreenKey.PASSWORD_CHANGE -> PasswordChangeScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.ACCOUNT })
            ProfileScreenKey.DELETE_REAUTH -> AccountDeleteReauthScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.ACCOUNT })
            ProfileScreenKey.DELETE_DONE -> AccountDeletedScreen(onGoLogin = onLoggedOut)
            ProfileScreenKey.PRIVACY_DATA -> PrivacyDataScreen(
                state, scope,
                onBack = { state.screen.value = ProfileScreenKey.HOME },
                onOpenTerms = { state.termsOrigin = ProfileScreenKey.PRIVACY_DATA; state.screen.value = ProfileScreenKey.TERMS },
            )
            ProfileScreenKey.EXPORT_DATA -> ExportDataScreen(
                state, scope,
                onBack = { state.screen.value = ProfileScreenKey.PRIVACY_DATA },
                onSaveCsv = onSaveCsv,
            )
            ProfileScreenKey.APP_INFO -> AppInfoScreen(
                onBack = { state.screen.value = ProfileScreenKey.HOME },
                onOpenTerms = { state.termsOrigin = ProfileScreenKey.APP_INFO; state.screen.value = ProfileScreenKey.TERMS },
            )
            ProfileScreenKey.HELP_DETAIL -> HelpDetailScreen(
                onBack = { state.screen.value = ProfileScreenKey.HOME },
                onInquiry = { state.inquirySubmitted.value = false; state.screen.value = ProfileScreenKey.INQUIRY },
            )
            ProfileScreenKey.INQUIRY -> InquiryScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.HELP_DETAIL })
            ProfileScreenKey.TERMS -> com.tmtn.app.ui.onboarding.TermsDetailScreen { state.screen.value = state.termsOrigin }
        }
      }
        if (state.screen.value != ProfileScreenKey.BASIC && !state.confirmationOwnsFeedback) {
            com.tmtn.app.ui.onboarding.OnboardingErrorMessage(state.errorMessage.value)
        }
        if (state.loadFailed.value && state.screen.value == ProfileScreenKey.HOME) {
            com.tmtn.app.ui.onboarding.TmtnTonalButton("정보 다시 불러오기", { scope.launch { state.loadAll() } }, enabled = !state.isLoading.value)
        }

        if (state.isLoading.value && !state.confirmationOwnsFeedback) {
            com.tmtn.app.ui.onboarding.OnboardingSavingDialog()
        }
    }
}
