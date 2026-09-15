package com.tmtn.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.*

enum class MainTab(val label: String) {
    HOME("홈"),
    RECORD("기록"),
    REFERENCE("일보"),
    DAM("댐"),
    MY("내 정보"),
}

/** Figma navigation: a quiet grey selection surface and the original exported vector paths. */
@Composable
fun BottomNavBar(currentTab: MainTab, onTabSelected: (MainTab) -> Unit) {
    val colors = LocalTmtnColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TmtnType.navigationLabel
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(colors.navigationContainer)) {
        // Keep all five labels at the same size. Only fit the compact navigation row;
        // the rest of the app continues to respect the full chosen text scale.
        val widest = measurer.measure(MainTab.MY.label, labelStyle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1).size.width
        val available = (constraints.maxWidth / MainTab.entries.size - with(density) { 4.dp.toPx() }).coerceAtLeast(1f)
        val fit = (available / widest.coerceAtLeast(1)).coerceAtMost(1f)
        val fittedLabel = labelStyle.copy(fontSize = labelStyle.fontSize * fit, lineHeight = labelStyle.lineHeight * fit)
        Box(Modifier.fillMaxWidth().height(.5.dp).background(colors.outlineVariant))
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            MainTab.entries.forEach { tab ->
                NavItem(tab, tab == currentTab, { onTabSelected(tab) }, fittedLabel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavItem(tab: MainTab, isActive: Boolean, onClick: () -> Unit, labelStyle: TextStyle, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    val reduce = rememberTmtnReducedMotion()
    val iconColor by animateColorAsState(
        if (isActive) colors.navigationIconActive else colors.navigationIconInactive,
        animationSpec = tween(if (reduce) 0 else 120), label = "tab selection")
    val interactions = remember { MutableInteractionSource() }
    Column(modifier.tmtnPressFeedback(interactions).selectable(selected = isActive, role = Role.Tab, interactionSource = interactions, indication = ripple(), onClick = onClick)
        .heightIn(min = 72.dp)
        .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(56.dp).height(32.dp)
            .background(if (isActive) colors.navigationIndicator else Color.Transparent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            TabIcon(tab, iconColor)
        }
        Text(tab.label, style = labelStyle.copy(fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium),
            color = iconColor, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
    }
}

/** Figma SVG paths imported as Android VectorDrawables. */
@Composable
private fun TabIcon(tab: MainTab, color: Color) {
    val resource = when (tab) {
        MainTab.HOME -> com.tmtn.app.R.drawable.figma_nav_home
        MainTab.RECORD -> com.tmtn.app.R.drawable.figma_nav_record
        MainTab.REFERENCE -> com.tmtn.app.R.drawable.figma_nav_journal
        MainTab.DAM -> com.tmtn.app.R.drawable.figma_nav_dam
        MainTab.MY -> com.tmtn.app.R.drawable.figma_nav_profile
    }
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(resource), null,
        Modifier.width(56.dp).height(32.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color))
}
