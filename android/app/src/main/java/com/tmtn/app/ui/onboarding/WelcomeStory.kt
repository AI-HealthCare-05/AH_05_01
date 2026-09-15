package com.tmtn.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.DamArtwork
import com.tmtn.app.ui.common.tmtnMaterialDrawable
import com.tmtn.app.ui.theme.*

/** Figma A02–A04 (1314:2425 / 2438 / 2451), reader-controlled and skippable. */
@Composable
fun WelcomeStory(onSignup: () -> Unit, onLogin: () -> Unit, replay: Boolean = false) {
    var page by rememberSaveable(replay) { mutableIntStateOf(if (replay) 0 else -1) }
    val reduced = rememberTmtnReducedMotion()
    val colors = LocalTmtnColors.current
    val headings = listOf("틈튼이와 첫 만남", "작은 하루의 빈틈", "하루 세 장의 초대")
    val titles = listOf("안녕,\n나는 틈튼이야.", "바쁜 하루 사이에\n작은 틈이 생겼더라.", "매일 다른 세 장,\n오늘은 한 장부터.")
    val bodies = listOf("틈을 보면 메우고 싶어지는 비버야.",
        "내 몸을 돌볼 시간을 놓친 날들이\n이야기 속 댐에 빈틈으로 남았어.",
        "작은 행동을 마치면 재료가 모여.\n그 재료로 같은 댐의 틈을 차근차근 메워 가.")
    val speeches = listOf("요즘은 사람들 곁에 있는 댐이\n자꾸 눈에 들어와.",
        "하루를 전부 바꿀 필요는 없어.\n오늘 손볼 수 있는 한 곳부터 같이 보자.",
        "어떤 카드가 기다릴지 골라 봐.\n실천한 흔적은 내가 잘 모아 둘게.")
    val actions = listOf("어떤 댐인데?", "어떻게 메우면 될까?", "내 댐도 만나볼래")
    val back: () -> Unit = { if (replay && page == 0) onSignup() else page-- }
    BackHandler(enabled = page >= 0) { back() }
    if (page < 0) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            TmtnTopBar("틈튼 시작", onBack = null)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("작은 습관이 모여,\n틈이 튼튼해지는 곳.", style = TmtnType.headline, color = colors.onSurface)
                Text("틈튼", style = TmtnType.body, color = colors.onSurface)
                Image(painterResource(R.drawable.beaver_wave), null, Modifier.fillMaxWidth().height(300.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TmtnPrimaryButton("틈튼이 만나기", { page = 0 })
                TmtnTonalButton("이미 계정이 있어요", onLogin)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        TmtnTopBar(headings[page], onBack = back)
        AnimatedContent(page, modifier = Modifier.weight(1f), transitionSpec = {
            fadeIn(tween(if (reduced) 0 else 160, easing = TmtnMotion.EaseOut)) togetherWith
                fadeOut(tween(if (reduced) 0 else 80, easing = TmtnMotion.EaseOut))
        }, label = "welcome story") { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(titles[current], style = TmtnType.headline, color = colors.onSurface,
                    modifier = Modifier.fillMaxWidth().semantics { heading() })
                Text(bodies[current], style = TmtnType.body, color = colors.onSurface)
                when (current) {
                    0 -> Image(painterResource(R.drawable.beaver_wave), null, Modifier.fillMaxWidth().height(250.dp))
                    1 -> DamArtwork(0)
                    else -> {
                        Image(painterResource(R.drawable.beaver_card), null, Modifier.fillMaxWidth().height(195.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("WOOD", "FIRE", "EARTH", "METAL", "WATER").forEach {
                                Image(painterResource(tmtnMaterialDrawable(it)), null, Modifier.weight(1f).height(54.dp))
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("틈튼이", style = TmtnType.label, color = colors.onSurface)
                    Text(speeches[current], style = TmtnType.body, color = colors.onSurface)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TmtnPrimaryButton(if (replay && page == 2) "내 정보로 돌아가기" else actions[page], onClick = { if (page < 2) page++ else onSignup() })
            TmtnTonalButton(if (page == 2) "이야기 다시 보기" else if (replay) "이야기 닫기" else "바로 시작할게", onClick = { if (page == 2) page = 0 else onSignup() })
        }
    }
}
