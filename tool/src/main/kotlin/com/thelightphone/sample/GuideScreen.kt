package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import io.github.yang44yang.habits.R

class GuideScreen(activity: SealedLightActivity) : SimpleLightScreen<Unit>(activity) {
    private val scrollState = ScrollState(0)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            RoutineGuide(
                firstUse = false,
                onSkip = { goBack(null) },
                onStart = { goBack(null) },
                onAbout = { navigateTo(screenFactory = { AboutScreen(it) }) },
                scrollState = scrollState,
            )
        }
    }
}

/** One introduction page; its actions stay reachable when text needs to scroll. */
@Composable
fun RoutineGuide(
    firstUse: Boolean,
    busy: Boolean = false,
    error: Int? = null,
    onSkip: () -> Unit,
    onStart: () -> Unit,
    onAbout: (() -> Unit)? = null,
    scrollState: ScrollState? = null,
) {
    val scroll = scrollState ?: rememberScrollState()
    Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
        LightTopBar(
            leftButton = if (firstUse) null else LightBarButton.LightIcon(LightIcons.BACK, onClick = onSkip),
            center = LightTopBarCenter.Text(if (firstUse) "Routine" else stringResource(R.string.help_title)),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                LightText(stringResource(R.string.guide_tagline), LightTextVariant.Copy,
                    modifier = Modifier.padding(bottom = if (firstUse) 12.dp else 16.dp))
                GuideSection(R.string.guide_training_title, R.string.guide_training_body, compact = firstUse)
                GuideSection(R.string.guide_alcohol_title, R.string.guide_alcohol_body, compact = firstUse)
                GuideSection(R.string.guide_food_title, R.string.guide_food_body, compact = firstUse)
                if (firstUse) {
                    LightText(stringResource(R.string.guide_daily_hint), LightTextVariant.Detail, lighten = true,
                        modifier = Modifier.padding(bottom = 8.dp))
                    LightText(stringResource(R.string.guide_reward_intro), LightTextVariant.Detail, lighten = true,
                        modifier = Modifier.padding(bottom = 8.dp))
                    LightText(stringResource(R.string.guide_privacy), LightTextVariant.Detail, lighten = true)
                } else {
                    GuideSection(R.string.guide_record_title, R.string.guide_record_body)
                    GuideSection(R.string.guide_add_title, R.string.guide_add_body)
                    GuideSection(R.string.guide_reward_title, R.string.guide_reward_body)
                    GuideSection(R.string.guide_counts_title, R.string.guide_counts_body)
                    GuideSection(R.string.guide_details_title, R.string.guide_details_body)
                    GuideSection(R.string.guide_data_title, R.string.guide_data_body)
                    onAbout?.let {
                        RoutineAction(stringResource(R.string.about_action), onClick = it)
                    }
                }
            }
            RoutineScrollPosition(scroll, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                .width(3.dp).fillMaxHeight())
        }
        error?.let {
            LightText(stringResource(it), LightTextVariant.Detail, lighten = true,
                modifier = Modifier.padding(horizontal = 24.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
            if (firstUse) {
                RoutineAction(stringResource(R.string.guide_skip_action), Modifier.weight(1f), !busy, onSkip)
                RoutineAction(stringResource(R.string.guide_start_action), Modifier.weight(2f), !busy, onStart)
            } else {
                RoutineAction(stringResource(R.string.receipt_done_action), Modifier.weight(1f), onClick = onStart)
            }
        }
    }
}

@Composable
internal fun RoutineScrollPosition(state: ScrollState, modifier: Modifier) {
    val colors = LightThemeTokens.colors
    Canvas(modifier) {
        if (state.maxValue <= 0 || state.viewportSize <= 0) return@Canvas
        val height = (size.height * state.viewportSize / (state.viewportSize.toFloat() + state.maxValue))
            .coerceIn(24.dp.toPx().coerceAtMost(size.height), size.height)
        val top = (size.height - height) * state.value / state.maxValue
        drawRect(colors.content.copy(alpha = .18f))
        drawRect(colors.content.copy(alpha = .65f), Offset(0f, top), Size(size.width, height))
    }
}

@Composable
private fun GuideSection(title: Int, body: Int, compact: Boolean = false) {
    LightText(stringResource(title), LightTextVariant.Fine, modifier = Modifier.padding(bottom = 4.dp))
    LightText(stringResource(body), LightTextVariant.Detail, lighten = true,
        modifier = Modifier.padding(bottom = if (compact) 10.dp else 14.dp))
}
