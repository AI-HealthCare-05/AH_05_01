package com.tmtn.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
    ProfileScreenKey.NOTIFICATION -> ProfileScreenKey.HOME
    ProfileScreenKey.NOTIFICATION_TIME -> ProfileScreenKey.NOTIFICATION
    ProfileScreenKey.PERMISSIONS -> ProfileScreenKey.HOME
    ProfileScreenKey.ACCOUNT -> ProfileScreenKey.HOME
    ProfileScreenKey.HEALTH -> ProfileScreenKey.HOME
    ProfileScreenKey.EXERCISE -> ProfileScreenKey.HOME
    ProfileScreenKey.CONSENT -> ProfileScreenKey.HOME
    ProfileScreenKey.ACCESSIBILITY -> ProfileScreenKey.HOME
    ProfileScreenKey.EMAIL_CHANGE -> ProfileScreenKey.ACCOUNT
    ProfileScreenKey.PASSWORD_CHANGE -> ProfileScreenKey.ACCOUNT
    ProfileScreenKey.DELETE_REAUTH -> ProfileScreenKey.ACCOUNT
    ProfileScreenKey.DELETE_DONE -> null // 삭제 끝났으니 뒤로 갈 데 없음(홈으로가기 버튼만)
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
    onEditWakeSleep: () -> Unit,
    onSaveCsv: (fileName: String, content: String) -> Unit,
) {
    val colors = LocalTmtnColors.current
    val state = remember { ProfileState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        state.loadAll()
    }

    val previousScreen = previousScreenFor(state.screen.value)
    BackHandler(enabled = previousScreen != null) {
        previousScreen?.let { state.screen.value = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.screen.value) {
            ProfileScreenKey.HOME -> ProfileHomeScreen(
                state = state,
                onNavigate = { state.screen.value = it },
                onOpenDam = onOpenDam,
            )
            ProfileScreenKey.NOTIFICATION -> NotificationSettingScreen(
                state, scope,
                onBack = { state.screen.value = ProfileScreenKey.HOME },
                onEditWakeSleep = onEditWakeSleep,
            )
            ProfileScreenKey.NOTIFICATION_TIME -> NotificationTimeEditScreen(
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
            ProfileScreenKey.HEALTH -> HealthEditScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.HOME })
            ProfileScreenKey.EXERCISE -> ExerciseEditScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.HOME })
            ProfileScreenKey.CONSENT -> ConsentScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.HOME })
            ProfileScreenKey.ACCESSIBILITY -> AccessibilityScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.HOME })
            ProfileScreenKey.EMAIL_CHANGE -> EmailChangeScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.ACCOUNT })
            ProfileScreenKey.PASSWORD_CHANGE -> PasswordChangeScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.ACCOUNT })
            ProfileScreenKey.DELETE_REAUTH -> AccountDeleteReauthScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.ACCOUNT })
            ProfileScreenKey.DELETE_DONE -> AccountDeletedScreen(onGoHome = onLoggedOut)
            ProfileScreenKey.PRIVACY_DATA -> PrivacyDataScreen(
                state, scope,
                onBack = { state.screen.value = ProfileScreenKey.HOME },
                onOpenTerms = { }, // TODO: A14(약관 상세)는 온보딩 전용이라 재사용하려면 별도 작업 필요
            )
            ProfileScreenKey.EXPORT_DATA -> ExportDataScreen(
                state, scope,
                onBack = { state.screen.value = ProfileScreenKey.PRIVACY_DATA },
                onSaveCsv = onSaveCsv,
            )
            ProfileScreenKey.APP_INFO -> AppInfoScreen(
                onBack = { state.screen.value = ProfileScreenKey.HOME },
                onOpenTerms = { },
            )
            ProfileScreenKey.HELP_DETAIL -> HelpDetailScreen(
                onBack = { state.screen.value = ProfileScreenKey.HOME },
                onInquiry = { state.screen.value = ProfileScreenKey.INQUIRY },
            )
            ProfileScreenKey.INQUIRY -> InquiryScreen(state, scope, onBack = { state.screen.value = ProfileScreenKey.HELP_DETAIL })
        }

        if (state.isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        }
    }
}
