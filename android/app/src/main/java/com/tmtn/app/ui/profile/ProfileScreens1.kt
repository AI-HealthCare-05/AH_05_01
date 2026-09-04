package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.UserInfoResponse
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Figma F01 · 내 정보 · 홈 */
@Composable
fun ProfileHomeScreen(state: ProfileState, onNavigate: (ProfileScreenKey) -> Unit, onOpenDam: () -> Unit) {
    val colors = LocalTmtnColors.current
    val user = state.userInfo.value
    val companionMaterials = state.companionMaterials.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("내 정보", style = TmtnType.title, color = colors.onSurface)

        Column(
            modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${user?.nickname ?: user?.name ?: ""} 님", style = TmtnType.title, color = colors.onSurface)
            Text("${user?.email ?: ""} · 이메일 로그인", style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text(
                "함께한 지 ${daysSince(user?.created_at)}일 · 재료 ${companionMaterials ?: 0}개",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            ProfileListItem("생활시간 · 알림", "아침 준비 · 점심 뒤 · 자기 전") { onNavigate(ProfileScreenKey.NOTIFICATION) }
            ProfileListItem("연동 · 권한", "걸음 수 허용 · 알림 허용") { onNavigate(ProfileScreenKey.PERMISSIONS) }
            ProfileListItem("캐릭터 · 댐", "비버 · 댐") { onOpenDam() }
            ProfileListItem(
                "몸 정보",
                if (user?.birth_year != null) "생년월 ${user.birth_year}년 ${user.birth_month}월" else "입력 안 함",
            ) { onNavigate(ProfileScreenKey.HEALTH) }
            ProfileListItem("운동 정보", "") { onNavigate(ProfileScreenKey.EXERCISE) }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            ProfileListItem("계정", null) { onNavigate(ProfileScreenKey.ACCOUNT) }
            ProfileListItem("개인정보 · 내 데이터", null) { onNavigate(ProfileScreenKey.PRIVACY_DATA) }
            ProfileListItem("동의 관리", null) { onNavigate(ProfileScreenKey.CONSENT) }
            ProfileListItem("접근성", null) { onNavigate(ProfileScreenKey.ACCESSIBILITY) }
            ProfileListItem("도움말 · 문의", null) { onNavigate(ProfileScreenKey.HELP_DETAIL) }
            ProfileListItem("앱 정보", "버전 1.0.0") { onNavigate(ProfileScreenKey.APP_INFO) }
        }
    }
}

@Composable
internal fun ProfileListItem(title: String, sub: String?, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(title, style = TmtnType.label, color = colors.onSurface)
        if (!sub.isNullOrBlank()) {
            Text(sub, style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
    }
}

private fun daysSince(createdAt: String?): Long {
    if (createdAt == null) return 0
    return try {
        val date = LocalDate.parse(createdAt.take(10))
        ChronoUnit.DAYS.between(date, LocalDate.now()) + 1
    } catch (e: Exception) {
        0
    }
}

/** Figma F03 · 연동 · 권한 (기기 권한 상태는 앱 설정에서만 확인 가능해서 안내 위주) */
@Composable
fun PermissionsScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "연동 · 권한", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                ProfileListItem("걸음 수 읽기", "걷기 미션 자동 기록") { }
                ProfileListItem("알림", "") { }
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("무엇을 읽고 무엇을 읽지 않나요", style = TmtnType.label, color = colors.onSurface)
                Text("읽습니다: 걸음 수 · 걸은 시간", style = TmtnType.body, color = colors.onSurfaceVariant)
                Text("읽지 않습니다: 위치 · 심박 · 연락처 · 사진", style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                Text(
                    "권한을 끄면 걷기 미션은 직접 체크로 바뀝니다. 미션 내용과 받는 재료는 그대로입니다.",
                    style = TmtnType.label, color = colors.onSurfaceVariant,
                )
            }
            TmtnOutlinedButton(text = "기기 설정 열기", onClick = onOpenSettings)
        }
    }
}

/** Figma F04 · 계정 */
@Composable
fun AccountScreen(
    userInfo: UserInfoResponse?,
    onBack: () -> Unit,
    onChangeEmail: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "계정", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                ProfileListItem("로그인 방식", "이메일") { }
                ProfileListItem("이메일", userInfo?.email ?: "") { onChangeEmail() }
                ProfileListItem("비밀번호 변경", null) { onChangePassword() }
                ProfileListItem("로그아웃", null) { onLogout() }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("다른 기기에서 로그인", style = TmtnType.label, color = colors.onSurface)
                Text(
                    "같은 계정으로 로그인하면 기록이 이어집니다.\n기록이 겹치면 시간이 더 늦은 쪽을 남깁니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("계정 삭제", style = TmtnType.label, color = colors.onSurface)
                Text(
                    "삭제하면 기록·재료·댐이 모두 사라집니다. 되돌릴 수 없습니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).background(colors.error, RoundedCornerShape(14.dp)).clickable { onDeleteAccount() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("계정 삭제하기", style = TmtnType.label, color = Color.White)
                }
            }
        }
    }
}
