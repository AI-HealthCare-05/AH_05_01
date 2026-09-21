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
    val displayName = user?.nickname?.takeIf { it.isNotBlank() } ?: user?.name?.takeIf { it.isNotBlank() }
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("내 정보", onBack = null)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(displayName?.let { "${it}의 작업 자리." } ?: "나의 작업 자리.", style = TmtnType.headline, color = colors.onSurface)
            user?.email?.let { Text(it, style = TmtnType.body, color = colors.onSurfaceVariant) }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))
                .tmtnClickable(role = Role.Button, onClick = onOpenDam).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("함께 쌓은 흔적", style = TmtnType.label, color = colors.onSurface)
                val count = state.companionMaterials.value
                val stage = state.companionStage.value
                Text(if (count != null && stage != null) "모은 재료 ${count}개 · 댐 ${stage}단계" else "내 댐과 재료 보러 가기", style = TmtnType.body, color = colors.onSurface)
            }
            ProfileSectionLabel("내 생활과 정보")
            ProfileListItem("기본 정보", "이름 · 별명 · 태어난 연·월") { onNavigate(ProfileScreenKey.BASIC) }
            ProfileListItem("신체 정보", "키 · 몸무게 · 성별") { onNavigate(ProfileScreenKey.HEALTH) }
            val exercise = state.exerciseHabits.value
            ProfileListItem("운동 정보", exercise?.let { "근력 ${it.strength_weekly_count}${if (it.strength_weekly_count == 5) "일 이상" else "일"} · 중강도 ${it.aerobic_moderate_minutes}분" } ?: "평소 운동량과 강도") { onNavigate(ProfileScreenKey.EXERCISE) }
            ProfileListItem("생활시간 · 알림", "하루에 맞춘 실천 시간") { onNavigate(ProfileScreenKey.NOTIFICATION) }
            ProfileListItem("연동 · 권한", "활동 · 알림 설정") { onNavigate(ProfileScreenKey.PERMISSIONS) }
            ProfileSectionLabel("계정과 앱 설정")
            ProfileListItem("계정", "이메일 · 비밀번호") { onNavigate(ProfileScreenKey.ACCOUNT) }
            ProfileListItem("개인정보 · 내 데이터", null) { onNavigate(ProfileScreenKey.PRIVACY_DATA) }
            ProfileListItem("동의 관리", null) { onNavigate(ProfileScreenKey.CONSENT) }
            ProfileListItem("사운드", "효과음 · 홈 배경음") { onNavigate(ProfileScreenKey.SOUND) }
            ProfileSectionLabel("틈튼과 함께")
            ProfileListItem("도움말 · 문의", "이용 중 궁금한 점") { onNavigate(ProfileScreenKey.HELP_DETAIL) }
            ProfileListItem("틈튼이 이야기 다시 보기", "틈을 메우고 싶은 비버") { onNavigate(ProfileScreenKey.STORY) }
            ProfileListItem("앱 정보", null) { onNavigate(ProfileScreenKey.APP_INFO) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProfileSectionLabel(text: String) {
    val colors = LocalTmtnColors.current
    Text(text, style = TmtnType.caption, color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

@Composable
internal fun ProfileListItem(title: String, sub: String?, horizontalInset: androidx.compose.ui.unit.Dp? = null, onClick: (() -> Unit)? = null) {
    val colors = LocalTmtnColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.tmtnClickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 60.dp).padding(vertical = 16.dp, horizontal = horizontalInset ?: if (onClick == null) 20.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = TmtnType.bodyLarge, color = colors.onSurface)
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
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                ProfileListItem("신체 활동", "걸음 · 계단 · 움직이는 시간 측정")
                ProfileListItem("위치", "거리 미션을 할 때만")
                ProfileListItem("알림", "카드 알림과 측정 상태 안내")
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
                    "자동 측정이 어려우면 직접 체크를 선택할 수 있어요. 미션 내용과 받는 재료는 같아요.",
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
