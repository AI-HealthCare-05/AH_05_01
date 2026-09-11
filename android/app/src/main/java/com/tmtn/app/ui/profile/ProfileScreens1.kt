package com.tmtn.app.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.Role
import com.tmtn.app.ui.common.TmtnMascot
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.tmtn.app.ui.theme.tmtnClickable
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("내 정보", style = TmtnType.title, color = colors.onSurface)
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user?.nickname?.takeIf { it.isNotBlank() }?.let { "$it 님" }
                    ?: user?.name?.takeIf { it.isNotBlank() }?.let { "$it 님" } ?: "반가워요",
                    style = TmtnType.headline, color = colors.onSurface)
                user?.email?.let { Text(it, style = TmtnType.caption, color = colors.onSurfaceVariant) }
                user?.created_at?.let { Text("틈튼과 함께 ${daysSince(it)}일째", style = TmtnType.caption, color = colors.onSurfaceVariant) }
            }
            TmtnMascot(com.tmtn.app.R.drawable.beaver_wave, null, Modifier.size(88.dp))
        }
        ProfileSectionLabel("나의 생활")
        ProfileListItem("신체 정보", null) { onNavigate(ProfileScreenKey.HEALTH) }
        ProfileListItem("운동 정보", null) { onNavigate(ProfileScreenKey.EXERCISE) }
        ProfileListItem("생활시간 · 알림", null) { onNavigate(ProfileScreenKey.NOTIFICATION) }
        ProfileSectionLabel("앱 설정")
        ProfileListItem("접근성", "글자 크기 · 동작 줄이기 · 고대비") { onNavigate(ProfileScreenKey.ACCESSIBILITY) }
        ProfileListItem("연동 · 권한", null) { onNavigate(ProfileScreenKey.PERMISSIONS) }
        ProfileListItem("계정", null) { onNavigate(ProfileScreenKey.ACCOUNT) }
        ProfileListItem("개인정보 · 내 데이터", null) { onNavigate(ProfileScreenKey.PRIVACY_DATA) }
        ProfileListItem("동의 관리", null) { onNavigate(ProfileScreenKey.CONSENT) }
        ProfileSectionLabel("도움이 필요할 때")
        ProfileListItem("도움말 · 문의", null) { onNavigate(ProfileScreenKey.HELP_DETAIL) }
        ProfileListItem("앱 정보", null) { onNavigate(ProfileScreenKey.APP_INFO) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProfileSectionLabel(text: String) {
    val colors = LocalTmtnColors.current
    Text(text, style = TmtnType.caption, color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

@Composable
internal fun ProfileListItem(title: String, sub: String?, onClick: (() -> Unit)? = null) {
    val colors = LocalTmtnColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.tmtnClickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 60.dp).padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = TmtnType.body, color = colors.onSurface)
                if (!sub.isNullOrBlank()) Text(sub, style = TmtnType.caption, color = colors.onSurfaceVariant)
            }
            if (onClick != null) Text("›", style = TmtnType.title, color = colors.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
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
                ProfileListItem("걸음 수 읽기", "걷기 미션 자동 기록")
                ProfileListItem("알림", null)
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("무엇을 읽고 무엇을 읽지 않나요", style = TmtnType.label, color = colors.onSurface)
                Text("읽습니다: 걸음 수 · 걸은 시간", style = TmtnType.body, color = colors.onSurfaceVariant)
                Text("위치는 거리 측정을 허용했을 때만 사용해요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                Text("읽지 않습니다: 심박 · 연락처 · 사진", style = TmtnType.body, color = colors.onSurfaceVariant)
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
                ProfileListItem("로그인 방식", "이메일")
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
