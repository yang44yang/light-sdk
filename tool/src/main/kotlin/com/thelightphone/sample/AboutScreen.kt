package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Public attribution and contact details; this page makes no network requests. */
class AboutScreen(activity: SealedLightActivity) : SimpleLightScreen<Unit>(activity) {
    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val scroll = rememberScrollState()
        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack(null) }),
                    center = LightTopBarCenter.Text(stringResource(R.string.about_title)),
                )
                Box(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)
                        .padding(horizontal = 24.dp, vertical = 16.dp)) {
                        LightText(stringResource(R.string.about_app_version), LightTextVariant.Copy,
                            modifier = Modifier.padding(bottom = 24.dp))
                        AboutDetail(R.string.about_author_label, R.string.about_author)
                        AboutDetail(R.string.about_feedback_label, R.string.about_email)
                        AboutDetail(R.string.about_source_label, R.string.about_source)
                    }
                    RoutineScrollPosition(scroll, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                        .width(3.dp).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun AboutDetail(label: Int, value: Int) {
    LightText(stringResource(label), LightTextVariant.Fine, modifier = Modifier.padding(bottom = 4.dp))
    LightText(stringResource(value), LightTextVariant.Detail, lighten = true,
        modifier = Modifier.padding(bottom = 22.dp))
}
