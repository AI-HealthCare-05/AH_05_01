package kr.tmtn.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kr.tmtn.app.designsystem.TmtnColor
import kr.tmtn.app.designsystem.TmtnText
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.auth.LoginScreen
import kr.tmtn.app.ui.card.CardFrontScreen
import kr.tmtn.app.ui.card.CardPickScreen
import kr.tmtn.app.ui.home.HomeScreen
import kr.tmtn.app.ui.mission.MissionIntroScreen
import kr.tmtn.app.ui.mission.MissionCompleteScreen
import kr.tmtn.app.ui.mission.ModelMissionScreen
import kr.tmtn.app.ui.mission.SelfMissionScreen
import kr.tmtn.app.ui.onboarding.OnboardingScreen
import kr.tmtn.app.ui.tabs.DamScreen
import kr.tmtn.app.ui.tabs.MyPageScreen
import kr.tmtn.app.ui.tabs.RecordScreen
import kr.tmtn.app.ui.tabs.ReferenceScreen
import kr.tmtn.app.ui.state.AccessibilityScreen
import kr.tmtn.app.ui.state.ServerMaintenanceScreen
import kr.tmtn.app.ui.state.SessionExpiredScreen
import kr.tmtn.app.ui.state.UpdateRequiredScreen
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

@Composable
fun TmtnApp(today: TodayViewModel) {
    val nav = rememberNavController()
    val ctx = LocalContext.current

    val start = when {
        !today.isLoggedIn -> Route.LOGIN
        today.needsOnboarding -> Route.ONBOARDING
        else -> Route.HOME
    }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showTabs = Tab.entries.any { it.route == current }

    Scaffold(
        containerColor = TmtnColor.Background,
        bottomBar = { if (showTabs) TmtnBottomBar(nav, current) },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = start,
            // 키보드가 올라오면 그만큼 화면을 밀어 올린다.
            // Manifest 의 adjustResize 만으로는 부족하다 — enableEdgeToEdge() 를 쓰는 순간
            // 앱이 창 여백을 직접 맡게 되어, 키보드가 입력 칸을 덮어 버린다.
            // (2026-08-30 키·몸무게 칸이 가려진다는 제보로 확인)
            modifier = Modifier.padding(inner).imePadding(),
        ) {
            composable(Route.LOGIN) {
                LoginScreen(onDone = {
                    today.login()
                    val next = if (today.needsOnboarding) Route.ONBOARDING else Route.HOME
                    nav.navigate(next) { popUpTo(Route.LOGIN) { inclusive = true } }
                })
            }

            composable(Route.ONBOARDING) {
                OnboardingScreen(
                    initial = today.profile,
                    onDone = { p ->
                        today.saveProfile(p)
                        nav.navigate(Route.HOME) { popUpTo(Route.ONBOARDING) { inclusive = true } }
                    },
                )
            }

            composable(Route.HOME) { HomeScreen(today, nav) }
            composable(Route.RECORD) { RecordScreen(today) }
            composable(Route.DAM) { DamScreen(today) }
            composable(Route.REFERENCE) { ReferenceScreen(today) }
            composable(Route.MY) {
                MyPageScreen(
                    today,
                    onLoggedOut = { nav.navigate(Route.LOGIN) { popUpTo(0) } },
                    onOpen = { nav.navigate(it) },
                )
            }

            composable(Route.CARD_PICK) { CardPickScreen(today, nav) }
            composable(Route.CARD_FRONT) { CardFrontScreen(today, nav) }
            composable(Route.MISSION_INTRO) { MissionIntroScreen(today, nav) }
            composable(Route.MISSION_SELF) { SelfMissionScreen(today, nav) }
            composable(Route.MISSION_MODEL) { ModelMissionScreen(today, nav) }
            composable(Route.COMPLETE) { MissionCompleteScreen(today, nav) }

            /* ── H · 상태·복구·접근성 ─────────────────────── */
            composable(Route.ACCESSIBILITY) {
                AccessibilityScreen(
                    onOpenSystemSettings = { openDisplaySettings(ctx) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Route.SERVER_MAINTENANCE) { ServerMaintenanceScreen() }
            composable(Route.UPDATE_REQUIRED) { UpdateRequiredScreen(onOpenStore = { openStore(ctx) }) }
            composable(Route.SESSION_EXPIRED) {
                SessionExpiredScreen(onLogin = { nav.navigate(Route.LOGIN) { popUpTo(0) } })
            }
        }
    }
}

@Composable
private fun TmtnBottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = TmtnColor.Surface, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    if (current != tab.route) {
                        nav.navigate(tab.route) {
                            popUpTo(Route.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = TmtnText.Caption) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TmtnColor.Primary,
                    selectedTextColor = TmtnColor.Primary,
                    indicatorColor = TmtnColor.SecondaryContainer,
                    unselectedIconColor = TmtnColor.OnSurfaceVariant,
                    unselectedTextColor = TmtnColor.OnSurfaceVariant,
                ),
            )
        }
    }
}

/* ---------------------------------------------------------- 바깥으로 */

/**
 * 기기의 글자 크기 설정으로 보낸다. (H05)
 * 앱 안에서 따로 조절하지 않고 기기 설정을 그대로 따르기 때문이다.
 */
private fun openDisplaySettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        // 설정 화면이 없는 기기도 있다. 앱이 죽는 것보다 아무 일도 안 일어나는 편이 낫다.
    }
}

/** 스토어의 이 앱 페이지를 연다. (H04) 스토어가 없으면 웹으로 떨어진다. */
private fun openStore(ctx: Context) {
    val pkg = ctx.packageName.removeSuffix(".debug")
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
