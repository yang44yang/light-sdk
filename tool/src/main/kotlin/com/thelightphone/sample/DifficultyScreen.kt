package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.thelightphone.sdk.ui.lightClickable
import io.github.yang44yang.habits.R

class DifficultyScreen(
    sealedActivity: SealedLightActivity,
    private val current: HabitDifficulty,
) : SimpleLightScreen<HabitDifficulty>(sealedActivity) {
    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack(null) }),
                    center = LightTopBarCenter.Text(stringResource(R.string.difficulty)),
                )
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp)) {
                    HabitDifficulty.values().forEach { level ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .semantics { selected = level == current }
                            .lightClickable(role = Role.RadioButton) { goBack(level) },
                            verticalAlignment = Alignment.CenterVertically) {
                            LightText(if (level == current) "●" else "○", LightTextVariant.Copy,
                                modifier = Modifier.padding(end = 16.dp))
                            LightText(stringResource(level.labelRes), LightTextVariant.Copy, modifier = Modifier.weight(1f))
                            LightText("+${level.gold}", LightTextVariant.Copy)
                        }
                    }
                    LightText(stringResource(R.string.difficulty_help), LightTextVariant.Detail, lighten = true,
                        modifier = Modifier.padding(top = 24.dp))
                }
            }
        }
    }
}
